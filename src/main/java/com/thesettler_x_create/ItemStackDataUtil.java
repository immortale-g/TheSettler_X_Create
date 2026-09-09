package com.thesettler_x_create;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/** Small ItemStack/CUSTOM_DATA helpers shared across the mod's custom item components. */
public final class ItemStackDataUtil {
  private ItemStackDataUtil() {}

  /** A mutable copy of {@code stack}'s {@code CUSTOM_DATA} tag, or an empty tag if it has none. */
  public static CompoundTag copyCustomData(ItemStack stack) {
    return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
  }
}
