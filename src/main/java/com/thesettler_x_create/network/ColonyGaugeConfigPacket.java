package com.thesettler_x_create.network;

import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelPosition;
import com.thesettler_x_create.TheSettlerXCreate;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ColonyGaugeConfigPacket(
    FactoryPanelPosition position,
    String address,
    int promiseClearingInterval,
    boolean clearPromises,
    boolean reset)
    implements CustomPacketPayload {
  private static final int ADDRESS_MAX_LENGTH = 25;

  public static final Type<ColonyGaugeConfigPacket> TYPE =
      new Type<>(
          ResourceLocation.fromNamespaceAndPath(TheSettlerXCreate.MODID, "colony_gauge_config"));

  public static final StreamCodec<RegistryFriendlyByteBuf, ColonyGaugeConfigPacket> STREAM_CODEC =
      StreamCodec.of(
          (buf, payload) -> {
            FactoryPanelPosition.STREAM_CODEC.encode(buf, payload.position);
            buf.writeUtf(payload.address, ADDRESS_MAX_LENGTH);
            buf.writeVarInt(payload.promiseClearingInterval);
            buf.writeBoolean(payload.clearPromises);
            buf.writeBoolean(payload.reset);
          },
          buf ->
              new ColonyGaugeConfigPacket(
                  FactoryPanelPosition.STREAM_CODEC.decode(buf),
                  buf.readUtf(ADDRESS_MAX_LENGTH),
                  buf.readVarInt(),
                  buf.readBoolean(),
                  buf.readBoolean()));

  @Override
  public Type<? extends CustomPacketPayload> type() {
    return TYPE;
  }
}
