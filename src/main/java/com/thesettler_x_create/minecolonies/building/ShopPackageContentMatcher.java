package com.thesettler_x_create.minecolonies.building;

import com.thesettler_x_create.create.CreatePackageBridge;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Reads and matches a Create package's contents against a lost-package stack key. Extracted out of
 * {@link ShopLostPackageInteraction} - which is a chat-interaction class, not a package-content
 * concern - since {@link ShopLostPackageHandoverProcessor} needed this same logic and was reaching
 * into the interaction class's statics to get it (Clean Code Audit finding a2-6).
 */
final class ShopPackageContentMatcher {
  private ShopPackageContentMatcher() {}

  static int countMatchingInPackage(@Nullable ItemStack packageStack, @Nullable ItemStack key) {
    if (packageStack == null || packageStack.isEmpty() || key == null || key.isEmpty()) {
      return 0;
    }
    int found = 0;
    for (ItemStack content : CreatePackageBridge.readContents(packageStack)) {
      if (matchesForRecovery(content, key)) {
        found += content.getCount();
      }
    }
    return found;
  }

  static List<ItemStack> unpackPackage(ItemStack packageStack) {
    return new ArrayList<>(CreatePackageBridge.readContents(packageStack));
  }

  private static boolean matchesForRecovery(ItemStack candidate, ItemStack key) {
    if (candidate == null || candidate.isEmpty() || key == null || key.isEmpty()) {
      return false;
    }
    if (ItemStack.isSameItemSameComponents(candidate, key)) {
      return true;
    }
    return ItemStack.isSameItem(candidate, key);
  }
}
