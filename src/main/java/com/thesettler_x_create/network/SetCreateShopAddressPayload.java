package com.thesettler_x_create.network;

import com.thesettler_x_create.TheSettlerXCreate;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SetCreateShopAddressPayload(BlockPos pos, String address) implements CustomPacketPayload {
    private static final int ADDRESS_MAX_LENGTH = 64;
    public static final Type<SetCreateShopAddressPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(TheSettlerXCreate.MODID, "set_create_shop_address"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetCreateShopAddressPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        buf.writeBlockPos(payload.pos);
                        buf.writeUtf(payload.address, ADDRESS_MAX_LENGTH);
                    },
                    buf -> new SetCreateShopAddressPayload(buf.readBlockPos(), buf.readUtf(ADDRESS_MAX_LENGTH))
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
