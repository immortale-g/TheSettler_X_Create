package com.thesettler_x_create.minecolonies.tileentity;

import com.minecolonies.api.inventory.api.CombinedItemHandler;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The shop hut's combined rack inventory, passed through unchanged, that reports every item really
 * taken out of it.
 *
 * <p>Couriers gather deliveries that start at the hut through this inventory, so this is where the
 * shop sees a pickup happen. It extends {@link CombinedItemHandler} instead of only implementing
 * the item handler interface because MineColonies' warehouse sort only works on that type. Every
 * method forwards to the inventory MineColonies built; none of the parent's own state is used.
 */
final class ObservedHutItemHandler extends CombinedItemHandler {
  /** Receives every real extraction with the combined slot it came from. */
  @FunctionalInterface
  interface TakenListener {
    void taken(int slot, ItemStack taken);
  }

  private final CombinedItemHandler delegate;
  private final TakenListener onTaken;

  ObservedHutItemHandler(CombinedItemHandler delegate, TakenListener onTaken) {
    super("");
    this.delegate = delegate;
    this.onTaken = onTaken;
  }

  /** The inventory this one forwards to; replaced when MineColonies rebuilds it. */
  CombinedItemHandler delegate() {
    return delegate;
  }

  @NotNull
  @Override
  public ItemStack extractItem(int slot, int amount, boolean simulate) {
    ItemStack extracted = delegate.extractItem(slot, amount, simulate);
    if (!simulate && !extracted.isEmpty()) {
      onTaken.taken(slot, extracted.copy());
    }
    return extracted;
  }

  @NotNull
  @Override
  public ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
    return delegate.insertItem(slot, stack, simulate);
  }

  @Override
  public void setStackInSlot(int slot, ItemStack stack) {
    delegate.setStackInSlot(slot, stack);
  }

  @NotNull
  @Override
  public ItemStack getStackInSlot(int slot) {
    return delegate.getStackInSlot(slot);
  }

  @Override
  public int getSlots() {
    return delegate.getSlots();
  }

  @Override
  public int getLastIndex(int slot) {
    return delegate.getLastIndex(slot);
  }

  @Override
  public int getSlotLimit(int slot) {
    return delegate.getSlotLimit(slot);
  }

  @Override
  public boolean isItemValid(int slot, @NotNull ItemStack stack) {
    return delegate.isItemValid(slot, stack);
  }

  @Override
  public CompoundTag serializeNBT(@NotNull HolderLookup.Provider provider) {
    return delegate.serializeNBT(provider);
  }

  @Override
  public void deserializeNBT(@NotNull HolderLookup.Provider provider, CompoundTag nbt) {
    delegate.deserializeNBT(provider, nbt);
  }

  @Override
  public void setName(@Nullable String name) {
    delegate.setName(name);
  }

  @NotNull
  @Override
  public Component getName() {
    return delegate.getName();
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    return other instanceof ObservedHutItemHandler observed && delegate.equals(observed.delegate);
  }

  @Override
  public int hashCode() {
    return delegate.hashCode();
  }
}
