package com.thesettler_x_create.minecolonies.tileentity;

import com.thesettler_x_create.stock.StockAging;
import com.thesettler_x_create.stock.StockAmount;
import com.thesettler_x_create.stock.nbt.StockAgingNbt;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

/**
 * Minecraft side of the unreserved stock ages of a {@link TileEntityCreateShop}: item stacks as
 * keys and saving through {@link StockAgingNbt}. The bookkeeping itself is a {@link StockAging}.
 */
final class ShopStockAgingLedger {
  private static final String TAG_STOCK_AGES = "UnreservedStockAges";

  private final StockAging<ItemStack> aging =
      new StockAging<>(ItemStack::isSameItemSameComponents, ShopStockAgingLedger::makeKey);

  /**
   * Updates the ages with the unreserved amounts counted right now.
   *
   * @return true when the saved state changed
   */
  boolean update(List<StockAmount<ItemStack>> unreservedNow, long now) {
    return aging.update(unreservedNow, now);
  }

  /** How much of an item kind has been unreserved for at least {@code minAge} ticks. */
  int agedAmount(ItemStack key, long now, long minAge) {
    return aging.agedAmount(key, now, minAge);
  }

  /** Forgets every age. @return number of item kinds that were tracked */
  int clear() {
    return aging.clear();
  }

  void load(CompoundTag tag, HolderLookup.Provider registries) {
    aging.restore(
        StockAgingNbt.read(
            tag.getList(TAG_STOCK_AGES, Tag.TAG_COMPOUND),
            stackTag -> readStack(stackTag, registries)));
  }

  void save(CompoundTag tag, HolderLookup.Provider registries) {
    tag.put(TAG_STOCK_AGES, StockAgingNbt.write(aging.stored(), stack -> stack.save(registries)));
  }

  private static Optional<ItemStack> readStack(Tag stackTag, HolderLookup.Provider registries) {
    if (!(stackTag instanceof CompoundTag compound)) {
      return Optional.empty();
    }
    return ItemStack.parse(registries, compound).filter(stack -> !stack.isEmpty());
  }

  private static ItemStack makeKey(ItemStack stack) {
    ItemStack copy = stack.copy();
    copy.setCount(1);
    return copy;
  }
}
