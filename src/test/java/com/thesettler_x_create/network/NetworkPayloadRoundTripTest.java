package com.thesettler_x_create.network;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlock.PanelSlot;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelPosition;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/**
 * Real encode/decode round-trip tests for the custom network payloads that don't touch ItemStack.
 * Unlike this project's usual source-text GuardTests, these actually run the StreamCodec against a
 * live buffer - possible here because BlockPos/String/boolean/int/ ResourceLocation all resolve
 * through {@link RegistryAccess#EMPTY} with no live-game bootstrap. {@code
 * CreateShopTestRequestPayload} and {@code CreateShopBatchRequestPayload} carry an ItemStack, whose
 * STREAM_CODEC throws outside a bootstrapped game instance (verified: both fail with
 * ExceptionInInitializerError under this test runner) - those two are covered by
 * ItemStackNetworkPayloadGuardTest instead, following the project's established GuardTest
 * convention for exactly this reason.
 */
class NetworkPayloadRoundTripTest {

  private static RegistryFriendlyByteBuf newBuf() {
    return new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
  }

  @Test
  void colonyGaugeConfigPacketRoundTrips() {
    ColonyGaugeConfigPacket original =
        new ColonyGaugeConfigPacket(
            new FactoryPanelPosition(new BlockPos(1, 2, 3), PanelSlot.TOP_LEFT),
            "some.address",
            5,
            true,
            false);
    RegistryFriendlyByteBuf buf = newBuf();
    ColonyGaugeConfigPacket.STREAM_CODEC.encode(buf, original);
    ColonyGaugeConfigPacket decoded = ColonyGaugeConfigPacket.STREAM_CODEC.decode(buf);
    assertEquals(original, decoded);
  }

  @Test
  void createShopStockRefreshPayloadRoundTrips() {
    CreateShopStockRefreshPayload original =
        new CreateShopStockRefreshPayload(new BlockPos(4, 5, 6));
    RegistryFriendlyByteBuf buf = newBuf();
    CreateShopStockRefreshPayload.STREAM_CODEC.encode(buf, original);
    CreateShopStockRefreshPayload decoded = CreateShopStockRefreshPayload.STREAM_CODEC.decode(buf);
    assertEquals(original, decoded);
  }

  @Test
  void setCreateShopAddressPayloadRoundTrips() {
    SetCreateShopAddressPayload original =
        new SetCreateShopAddressPayload(new BlockPos(7, 8, 9), "warehouse-1");
    RegistryFriendlyByteBuf buf = newBuf();
    SetCreateShopAddressPayload.STREAM_CODEC.encode(buf, original);
    SetCreateShopAddressPayload decoded = SetCreateShopAddressPayload.STREAM_CODEC.decode(buf);
    assertEquals(original, decoded);
  }

  @Test
  void setPackagerAddressPayloadRoundTrips() {
    SetPackagerAddressPayload original =
        new SetPackagerAddressPayload(new BlockPos(-1, 64, 12), "packager-address");
    RegistryFriendlyByteBuf buf = newBuf();
    SetPackagerAddressPayload.STREAM_CODEC.encode(buf, original);
    SetPackagerAddressPayload decoded = SetPackagerAddressPayload.STREAM_CODEC.decode(buf);
    assertEquals(original, decoded);
  }

  @Test
  void setCreateShopPermaWaitPayloadRoundTrips() {
    SetCreateShopPermaWaitPayload original =
        new SetCreateShopPermaWaitPayload(new BlockPos(0, 0, 0), true);
    RegistryFriendlyByteBuf buf = newBuf();
    SetCreateShopPermaWaitPayload.STREAM_CODEC.encode(buf, original);
    SetCreateShopPermaWaitPayload decoded = SetCreateShopPermaWaitPayload.STREAM_CODEC.decode(buf);
    assertEquals(original, decoded);
  }

  @Test
  void setCreateShopPermaOrePayloadRoundTrips() {
    SetCreateShopPermaOrePayload original =
        new SetCreateShopPermaOrePayload(
            new BlockPos(10, 20, 30), ResourceLocation.withDefaultNamespace("iron_ore"), true);
    RegistryFriendlyByteBuf buf = newBuf();
    SetCreateShopPermaOrePayload.STREAM_CODEC.encode(buf, original);
    SetCreateShopPermaOrePayload decoded = SetCreateShopPermaOrePayload.STREAM_CODEC.decode(buf);
    assertEquals(original, decoded);
  }
}
