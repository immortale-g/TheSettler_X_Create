package com.thesettler_x_create.create;

import com.simibubi.create.content.logistics.box.PackageItem;
import com.thesettler_x_create.init.ModDataComponents;
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
  /**
   * The largest count a single slot of a package survives being saved with.
   *
   * <p>A package keeps its contents in a vanilla {@code ItemContainerContents} component, whose
   * codec writes every slot through {@code ItemStack.CODEC}, and that one caps {@code count} at 99.
   * A slot holding more encodes to an error, so the package would not come back from disk. The
   * network codec has no such limit, which is why an oversized package looks fine until the chunk
   * it sits in is saved.
   */
  private static final int MAX_COUNT_PER_SLOT = 99;

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

  /**
   * The contents of a package as one stack: the first item it holds, counting every slot that holds
   * that same item.
   *
   * <p>A package spreads a large amount over its nine slots, so reading only the first slot would
   * report a fraction of what arrived and leave a gauge waiting for goods that are already in the
   * chest. Slots holding something else are left out rather than added up, since the count is what
   * a gauge books against its promise for one item.
   */
  public static ItemStack sumOfFirstKind(List<ItemStack> stacks) {
    if (stacks.isEmpty()) {
      return ItemStack.EMPTY;
    }
    ItemStack first = stacks.get(0).copy();
    for (int i = 1; i < stacks.size(); i++) {
      ItemStack further = stacks.get(i);
      if (ItemStack.isSameItemSameComponents(first, further)) {
        first.setCount(first.getCount() + further.getCount());
      }
    }
    return first;
  }

  /**
   * How much of {@code item} one package can carry: its nine slots, each filled no higher than the
   * item stacks and no higher than a saved slot survives.
   *
   * <p>A caller that wants to ship more asks for several packages. Overfilling one instead used to
   * be invisible, because a single {@code setStackInSlot} accepts any count and nothing complains
   * until the package is written to disk.
   */
  public static int capacityFor(ItemStack item) {
    if (item == null || item.isEmpty()) {
      return 0;
    }
    return PackageItem.SLOTS * Math.min(item.getMaxStackSize(), MAX_COUNT_PER_SLOT);
  }

  /**
   * Builds a package holding {@code content}, addressed to {@code address}.
   *
   * <p>The contents are spread over the package's slots the way Create fills its own packages, so
   * no slot holds more than a stack. {@code content} must fit {@link #capacityFor}; the caller
   * decides what to do with a remainder, since only it knows what the rest is owed to.
   */
  public static ItemStack buildPackage(ItemStack content, @Nullable String address) {
    ItemStack pkg = PackageItem.containing(List.of(content));
    PackageItem.addAddress(pkg, address);
    return pkg;
  }

  /**
   * A package for a gauge order, which carries how much of that order is still owed after it.
   *
   * <p>See {@link ModDataComponents#GAUGE_ORDER_OPEN} for why the amount travels with the goods
   * instead of the gauge asking for it.
   */
  public static ItemStack buildGaugePackage(ItemStack content, @Nullable String address, int open) {
    ItemStack pkg = buildPackage(content, address);
    pkg.set(ModDataComponents.GAUGE_ORDER_OPEN.get(), Math.max(0, open));
    return pkg;
  }

  /**
   * How much of the gauge order this package belongs to is still owed after it, or {@code null}
   * when the package does not say - anything not shipped by a shop for a gauge.
   */
  @Nullable
  public static Integer readGaugeOrderOpen(@Nullable ItemStack packageStack) {
    if (packageStack == null || packageStack.isEmpty()) {
      return null;
    }
    return packageStack.get(ModDataComponents.GAUGE_ORDER_OPEN.get());
  }
}
