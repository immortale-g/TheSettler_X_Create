package com.thesettler_x_create.network;

import com.thesettler_x_create.TheSettlerXCreate;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Sets how many of one item kind the shop keeps in its Create network before the colony may draw.
 * An amount of 0 removes the entry, so adding and clearing are the same message.
 *
 * <p>The stack travels through the optional codec. The plain one refuses an empty stack, and a
 * payload that cannot be encoded takes the whole connection down with it: picking an item and
 * backing out of the amount dialog threw the player off the server. An empty stack is dropped by
 * the handler instead, the way it was always meant to be.
 */
public record SetCreateShopNetworkMinimumPayload(BlockPos hutPos, ItemStack stack, int amount)
    implements CustomPacketPayload {
  public static final Type<SetCreateShopNetworkMinimumPayload> TYPE =
      new Type<>(
          ResourceLocation.fromNamespaceAndPath(
              TheSettlerXCreate.MODID, "set_createshop_network_minimum"));

  public static final StreamCodec<RegistryFriendlyByteBuf, SetCreateShopNetworkMinimumPayload>
      STREAM_CODEC =
          StreamCodec.of(
              (buf, payload) -> {
                buf.writeBlockPos(payload.hutPos);
                ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, payload.stack);
                buf.writeVarInt(Math.max(0, payload.amount));
              },
              buf ->
                  new SetCreateShopNetworkMinimumPayload(
                      buf.readBlockPos(),
                      ItemStack.OPTIONAL_STREAM_CODEC.decode(buf),
                      Math.max(0, buf.readVarInt())));

  @Override
  public Type<? extends CustomPacketPayload> type() {
    return TYPE;
  }
}
