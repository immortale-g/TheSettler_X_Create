package com.thesettler_x_create.minecolonies.module;

import com.minecolonies.api.colony.buildings.modules.AbstractBuildingModule;
import com.minecolonies.api.colony.buildings.modules.IPersistentModule;
import com.minecolonies.api.crafting.ItemStorage;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * How much of an item kind this shop keeps in its Create stock network for the production it feeds.
 * A colony request may only draw what is there beyond that; the shop's own flows are not limited by
 * it.
 */
public class CreateShopNetworkMinimumModule extends AbstractBuildingModule
    implements IPersistentModule {
  /** Enough to guard what a base actually produces, few enough to stay a readable list. */
  public static final int MAX_ENTRIES = 30;

  private static final String TAG_MINIMUMS = "NetworkMinimums";
  private static final String TAG_STACK = "Stack";
  private static final String TAG_AMOUNT = "Amount";

  private final Map<ItemStorage, Integer> minimums = new LinkedHashMap<>();

  /** The minimum kept for this item kind, 0 when the player set none. */
  public int getMinimum(ItemStack stack) {
    if (stack == null || stack.isEmpty()) {
      return 0;
    }
    return minimums.getOrDefault(new ItemStorage(stack), 0);
  }

  public Map<ItemStorage, Integer> getMinimums() {
    return Collections.unmodifiableMap(minimums);
  }

  public boolean hasReachedLimit() {
    return minimums.size() >= MAX_ENTRIES;
  }

  /**
   * Sets what stays in the network for this item kind. An amount of 0 or less removes the entry.
   *
   * @return whether anything changed
   */
  public boolean setMinimum(ItemStack stack, int amount) {
    if (stack == null || stack.isEmpty()) {
      return false;
    }
    ItemStorage key = new ItemStorage(stack);
    if (amount <= 0) {
      if (minimums.remove(key) == null) {
        return false;
      }
      markDirty();
      return true;
    }
    if (!minimums.containsKey(key) && hasReachedLimit()) {
      return false;
    }
    Integer previous = minimums.put(key, amount);
    if (previous != null && previous == amount) {
      return false;
    }
    markDirty();
    return true;
  }

  @Override
  public void deserializeNBT(@NotNull HolderLookup.Provider provider, CompoundTag compound) {
    minimums.clear();
    ListTag list = compound.getList(TAG_MINIMUMS, Tag.TAG_COMPOUND);
    for (int i = 0; i < list.size(); i++) {
      CompoundTag entry = list.getCompound(i);
      ItemStack stack = ItemStack.parseOptional(provider, entry.getCompound(TAG_STACK));
      int amount = entry.getInt(TAG_AMOUNT);
      if (stack.isEmpty() || amount <= 0) {
        continue;
      }
      minimums.put(new ItemStorage(stack), amount);
    }
  }

  @Override
  public void serializeNBT(@NotNull HolderLookup.Provider provider, CompoundTag compound) {
    ListTag list = new ListTag();
    for (Map.Entry<ItemStorage, Integer> entry : minimums.entrySet()) {
      CompoundTag tag = new CompoundTag();
      tag.put(TAG_STACK, entry.getKey().getItemStack().saveOptional(provider));
      tag.putInt(TAG_AMOUNT, entry.getValue());
      list.add(tag);
    }
    compound.put(TAG_MINIMUMS, list);
  }

  @Override
  public void serializeToView(RegistryFriendlyByteBuf buf) {
    buf.writeVarInt(minimums.size());
    for (Map.Entry<ItemStorage, Integer> entry : minimums.entrySet()) {
      ItemStack.STREAM_CODEC.encode(buf, entry.getKey().getItemStack());
      buf.writeVarInt(entry.getValue());
    }
    buf.writeBoolean(hasReachedLimit());
  }
}
