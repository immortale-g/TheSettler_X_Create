package com.thesettler_x_create.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * A payload the client cannot encode does not fail quietly: Minecraft drops the connection, and the
 * player is thrown back to the server list with "Failed to encode packet". This one carries an item
 * stack, and the plain stack codec refuses an empty one, so picking an item and then leaving the
 * amount dialog logged the player out (seen in game on 2026-09-18).
 *
 * <p>Needs real ItemStacks, so it runs with a loaded FML.
 */
@Tag("fml")
class SetCreateShopNetworkMinimumPayloadFmlTest {

  @Test
  void anEmptyStackTravelsInsteadOfBreakingTheConnection() {
    SetCreateShopNetworkMinimumPayload original =
        new SetCreateShopNetworkMinimumPayload(new BlockPos(1, 2, 3), ItemStack.EMPTY, 0);

    SetCreateShopNetworkMinimumPayload decoded = roundTrip(original);

    assertTrue(decoded.stack().isEmpty(), "an empty stack must survive as an empty stack");
    assertEquals(original.hutPos(), decoded.hutPos());
  }

  @Test
  void anOrdinaryMinimumRoundTrips() {
    SetCreateShopNetworkMinimumPayload original =
        new SetCreateShopNetworkMinimumPayload(
            new BlockPos(10, -60, 30), new ItemStack(Items.TORCH), 64);

    SetCreateShopNetworkMinimumPayload decoded = roundTrip(original);

    assertEquals(original.hutPos(), decoded.hutPos());
    assertEquals(64, decoded.amount());
    assertTrue(ItemStack.isSameItem(original.stack(), decoded.stack()));
  }

  private static SetCreateShopNetworkMinimumPayload roundTrip(
      SetCreateShopNetworkMinimumPayload payload) {
    // EMPTY has no item registry, and encoding a real stack needs one.
    RegistryFriendlyByteBuf buf =
        new RegistryFriendlyByteBuf(
            Unpooled.buffer(), RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
    SetCreateShopNetworkMinimumPayload.STREAM_CODEC.encode(buf, payload);
    return SetCreateShopNetworkMinimumPayload.STREAM_CODEC.decode(buf);
  }
}
