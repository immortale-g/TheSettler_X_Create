package com.thesettler_x_create.blockentity;

import com.thesettler_x_create.create.CreatePackageBridge;
import com.thesettler_x_create.minecolonies.building.BuildingCreateShop.GaugePackagingTask;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Which gauge task a shop packages next, and how much of it travels in this package.
 *
 * <p>Kept apart from {@link CreateShopOutputBlockEntity} because this is the decision the shop can
 * get wrong in two expensive ways, and neither shows up in a world test until goods are already
 * lost: serving only the head of the queue stalls every order behind a slow one, and a preview that
 * disagrees with the real pull ships the same goods again and again. As a plain function over a
 * task list and a rack source, both are cheap to pin down.
 */
public final class GaugePackageSelection {
  private GaugePackageSelection() {}

  /**
   * Pulling from the shop's racks: up to {@code amount} of {@code key}, or as much of it as is
   * there. The seam a test stands in for.
   */
  @FunctionalInterface
  public interface RackSource {
    ItemStack pull(ItemStack key, int amount, boolean simulate);
  }

  /** The task that gets served and what came out of the racks for it. */
  public record Choice(GaugePackagingTask task, ItemStack extracted) {}

  /**
   * The first task the racks can cover anything of, or {@code null} when they can cover none.
   *
   * <p>Tasks are queued in the order the gauges asked, which says nothing about when their goods
   * arrive: an order waiting on a crafter would otherwise stand at the head of the queue and hold
   * back every order behind it. A task is asked for no more than one package holds, so a large
   * order travels in several rather than in one that cannot be saved.
   *
   * <p>Called once for the preview and once for the real pull, with the same tasks and the same
   * racks, it picks the same task and the same amount. That is what keeps Create from shipping a
   * preview the shop never hands out.
   */
  @Nullable
  public static Choice select(List<GaugePackagingTask> tasks, RackSource racks, boolean simulate) {
    for (GaugePackagingTask task : tasks) {
      int wanted = Math.min(task.amount(), CreatePackageBridge.capacityFor(task.item()));
      if (wanted <= 0) {
        continue;
      }
      ItemStack extracted = racks.pull(task.item(), wanted, simulate);
      if (extracted.isEmpty()) {
        continue;
      }
      return new Choice(task, extracted);
    }
    return null;
  }
}
