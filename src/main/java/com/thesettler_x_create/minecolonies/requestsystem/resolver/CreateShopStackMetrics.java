package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import java.util.List;
import net.minecraft.world.item.ItemStack;

/** Shared stack formatting/count helpers for Create Shop resolver services. */
final class CreateShopStackMetrics {
  private CreateShopStackMetrics() {}

  static int countStackList(List<ItemStack> stacks) {
    if (stacks == null || stacks.isEmpty()) {
      return 0;
    }
    int total = 0;
    for (ItemStack stack : stacks) {
      if (stack == null || stack.isEmpty()) {
        continue;
      }
      total += stack.getCount();
    }
    return total;
  }

  static String describeStack(ItemStack stack) {
    if (stack == null || stack.isEmpty()) {
      return "";
    }
    return stack.getHoverName().getString();
  }
}
