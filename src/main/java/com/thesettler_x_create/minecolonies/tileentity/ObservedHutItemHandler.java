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
 * taken out of it and every other real change of a slot.
 *
 * <p>Couriers gather deliveries that start at the hut through this inventory, so this is where the
 * shop sees a pickup happen. MineColonies also delivers into the building and sorts it through
 * here. It extends {@link CombinedItemHandler} instead of only implementing the item handler
 * interface because MineColonies' warehouse sort only works on that type. Every method forwards to
 * the inventory MineColonies built; none of the parent's own state is used.
 */
final class ObservedHutItemHandler extends CombinedItemHandler {
  /** Receives the real changes, each with the combined slot it happened in. */
  interface ChangeListener {
    /** Items were extracted. */
    void taken(int slot, ItemStack taken);

    /** Items were inserted ({@code delta > 0}) or a slot was overwritten. */
    void changed(int slot, ItemStack key, int delta);
  }

  private final CombinedItemHandler delegate;
  private final ChangeListener listener;

  ObservedHutItemHandler(CombinedItemHandler delegate, ChangeListener listener) {
    super("");
    this.delegate = delegate;
    this.listener = listener;
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
      listener.taken(slot, extracted.copy());
    }
    return extracted;
  }

  @NotNull
  @Override
  public ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
    ItemStack leftover = delegate.insertItem(slot, stack, simulate);
    int inserted = stack.getCount() - leftover.getCount();
    if (!simulate && !stack.isEmpty() && inserted > 0) {
      listener.changed(slot, stack.copy(), inserted);
    }
    return leftover;
  }

  @Override
  public void setStackInSlot(int slot, ItemStack stack) {
    ItemStack before = delegate.getStackInSlot(slot).copy();
    delegate.setStackInSlot(slot, stack);
    if (!before.isEmpty()) {
      listener.changed(slot, before, -before.getCount());
    }
    if (stack != null && !stack.isEmpty()) {
      listener.changed(slot, stack.copy(), stack.getCount());
    }
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
