package com.thesettler_x_create.create;

import com.simibubi.create.content.logistics.box.PackageItem;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Single chokepoint for reading and building Create's {@link PackageItem} package stacks. The same
 * "check {@code isPackage}, then read its {@code ItemStackHandler} contents" sequence used to be
 * duplicated independently across {@code ShopLostPackageInteraction} (three times), {@code
 * ColonyPackagerBlockEntity}, and {@code CreateShopOutputBlockTestCommands}, and building a new
 * package (the reverse: wrap an item in an {@code ItemStackHandler}, call {@code
 * PackageItem.containing}, then {@code addAddress}) was duplicated once more in {@code
 * CreateShopOutputBlockEntity} - the same anti-pattern as the request-broadcast duplication
 * consolidated in {@link CreateLogisticsBridge}, just for the package-item data shape instead of
 * the request shape. Funneling both directions through here means a future compatibility shim for a
 * Create-addon that changes {@code PackageItem}'s internal layout only has to patch one place.
 */
public final class CreatePackageBridge {
  private CreatePackageBridge() {}

  public static boolean isPackage(@Nullable ItemStack stack) {
    return stack != null && !stack.isEmpty() && PackageItem.isPackage(stack);
  }

  /**
   * Copies of every non-empty stack inside {@code packageStack}, in slot order. Empty when {@code
   * packageStack} isn't a package or carries no contents - never {@code null}.
   */
  public static List<ItemStack> readContents(@Nullable ItemStack packageStack) {
    if (!isPackage(packageStack)) {
      return Collections.emptyList();
    }
    ItemStackHandler contents = PackageItem.getContents(packageStack);
    if (contents == null) {
      return Collections.emptyList();
    }
    List<ItemStack> result = new ArrayList<>();
    for (int i = 0; i < contents.getSlots(); i++) {
      ItemStack content = contents.getStackInSlot(i);
      if (!content.isEmpty()) {
        result.add(content.copy());
      }
    }
    return result;
  }

  /** Builds a new single-item package stack addressed to {@code address}. */
  public static ItemStack buildPackage(ItemStack content, @Nullable String address) {
    ItemStackHandler handler = new ItemStackHandler(PackageItem.SLOTS);
    handler.setStackInSlot(0, content);
    ItemStack pkg = PackageItem.containing(handler);
    PackageItem.addAddress(pkg, address);
    return pkg;
  }
}
