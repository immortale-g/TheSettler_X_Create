package com.thesettler_x_create.network;

import com.thesettler_x_create.TheSettlerXCreate;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SetCreateShopPermaWaitPayload(BlockPos pos, boolean enabled)
    implements CustomPacketPayload {
  public static final Type<SetCreateShopPermaWaitPayload> TYPE =
      new Type<>(
          ResourceLocation.fromNamespaceAndPath(
              TheSettlerXCreate.MODID, "set_create_shop_perma_wait"));

  public static final StreamCodec<RegistryFriendlyByteBuf, SetCreateShopPermaWaitPayload>
      STREAM_CODEC =
          StreamCodec.of(
              (buf, payload) -> {
                buf.writeBlockPos(payload.pos);
                buf.writeBoolean(payload.enabled);
              },
              buf -> new SetCreateShopPermaWaitPayload(buf.readBlockPos(), buf.readBoolean()));

  @Override
  public Type<? extends CustomPacketPayload> type() {
    return TYPE;
  }
}
