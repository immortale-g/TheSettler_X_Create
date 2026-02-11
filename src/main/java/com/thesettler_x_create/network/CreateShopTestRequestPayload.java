package com.thesettler_x_create.network;

import com.thesettler_x_create.TheSettlerXCreate;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public record CreateShopTestRequestPayload(BlockPos pos, ItemStack stack, int amount)
        implements CustomPacketPayload {

    public static final Type<CreateShopTestRequestPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(TheSettlerXCreate.MODID, "create_shop_test_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CreateShopTestRequestPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        buf.writeBlockPos(payload.pos);
                        ItemStack.STREAM_CODEC.encode(buf, payload.stack);
                        buf.writeVarInt(payload.amount);
                    },
                    buf -> new CreateShopTestRequestPayload(
                            buf.readBlockPos(),
                            ItemStack.STREAM_CODEC.decode(buf),
                            buf.readVarInt()
                    )
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
