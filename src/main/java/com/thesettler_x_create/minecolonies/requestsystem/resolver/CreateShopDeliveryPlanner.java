package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import com.minecolonies.api.util.Tuple;
import com.thesettler_x_create.stock.ShopStockAccounting;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;

/**
 * Turns planned rack stock into the stacks the shop hands to couriers.
 *
 * <p>Every delivery starts at the shop hut, so where a planned stack lies no longer matters: same
 * item kinds are merged and split into deliveries of at most one stack each. The courier takes them
 * out of the hut's combined rack inventory.
 */
final class CreateShopDeliveryPlanner {
  private CreateShopDeliveryPlanner() {}

  static List<ItemStack> toDeliveryStacks(List<Tuple<ItemStack, BlockPos>> planned) {
    List<ItemStack> merged = new ArrayList<>();
    if (planned == null) {
      return merged;
    }
    for (Tuple<ItemStack, BlockPos> entry : planned) {
      ItemStack stack = entry == null ? null : entry.getA();
      if (stack == null || stack.isEmpty()) {
        continue;
      }
      ItemStack existing = findSameItem(merged, stack);
      if (existing == null) {
        merged.add(stack.copy());
      } else {
        existing.grow(stack.getCount());
      }
    }
    List<ItemStack> deliveries = new ArrayList<>();
    for (ItemStack itemKind : merged) {
      for (int chunk :
          ShopStockAccounting.deliveryChunks(itemKind.getCount(), itemKind.getMaxStackSize())) {
        deliveries.add(itemKind.copyWithCount(chunk));
      }
    }
    return deliveries;
  }

  private static ItemStack findSameItem(List<ItemStack> stacks, ItemStack stack) {
    for (ItemStack candidate : stacks) {
      if (ItemStack.isSameItemSameComponents(candidate, stack)) {
        return candidate;
      }
    }
    return null;
  }
}
