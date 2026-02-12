package com.thesettler_x_create.network;

import com.thesettler_x_create.TheSettlerXCreate;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record CreateShopStockRefreshPayload(BlockPos pos) implements CustomPacketPayload {
  public static final Type<CreateShopStockRefreshPayload> TYPE =
      new Type<>(
          ResourceLocation.fromNamespaceAndPath(
              TheSettlerXCreate.MODID, "create_shop_stock_refresh"));

  public static final StreamCodec<RegistryFriendlyByteBuf, CreateShopStockRefreshPayload>
      STREAM_CODEC =
          StreamCodec.of(
              (buf, payload) -> buf.writeBlockPos(payload.pos),
              buf -> new CreateShopStockRefreshPayload(buf.readBlockPos()));

  @Override
  public Type<? extends CustomPacketPayload> type() {
    return TYPE;
  }
}
