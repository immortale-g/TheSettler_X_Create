package com.thesettler_x_create.network;

import com.simibubi.create.content.logistics.BigItemStack;
import com.thesettler_x_create.TheSettlerXCreate;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record CreateShopBatchRequestPayload(BlockPos pos, List<BigItemStack> stacks)
    implements CustomPacketPayload {

  public static final Type<CreateShopBatchRequestPayload> TYPE =
      new Type<>(
          ResourceLocation.fromNamespaceAndPath(
              TheSettlerXCreate.MODID, "create_shop_batch_request"));

  public static final StreamCodec<RegistryFriendlyByteBuf, CreateShopBatchRequestPayload>
      STREAM_CODEC =
          StreamCodec.of(
              (buf, payload) -> {
                buf.writeBlockPos(payload.pos);
                buf.writeVarInt(payload.stacks.size());
                for (BigItemStack stack : payload.stacks) {
                  BigItemStack.STREAM_CODEC.encode(buf, stack);
                }
              },
              buf -> {
                BlockPos pos = buf.readBlockPos();
                int count = buf.readVarInt();
                List<BigItemStack> stacks = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                  stacks.add(BigItemStack.STREAM_CODEC.decode(buf));
                }
                return new CreateShopBatchRequestPayload(pos, stacks);
              });

  @Override
  public Type<? extends CustomPacketPayload> type() {
    return TYPE;
  }
}
