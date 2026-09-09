package com.thesettler_x_create.block;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

/**
 * The colony/shop a Colony Gauge item is linked to, stored on the gauge's custom-data NBT. Shared
 * by the producer ({@code BlockHutCreateShop}, which writes the link when right-clicked with a
 * gauge) and the consumers ({@code ColonyGaugeBlock}, {@code ColonyGaugeBlockItem}) so the two NBT
 * key names only need to be spelled correctly in one place.
 */
public record GaugeLinkData(int colonyId, BlockPos shopPos) {
  private static final String TAG_COLONY_ID = "GaugeColonyId";
  private static final String TAG_SHOP_POS = "GaugeShopPos";

  public static boolean isLinked(CompoundTag data) {
    return data != null && data.contains(TAG_COLONY_ID);
  }

  public static GaugeLinkData readFrom(CompoundTag data) {
    return new GaugeLinkData(data.getInt(TAG_COLONY_ID), BlockPos.of(data.getLong(TAG_SHOP_POS)));
  }

  public static void writeTo(CompoundTag tag, int colonyId, BlockPos shopPos) {
    tag.putInt(TAG_COLONY_ID, colonyId);
    tag.putLong(TAG_SHOP_POS, shopPos.asLong());
  }
}
