package com.thesettler_x_create.blockentity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.thesettler_x_create.minecolonies.building.BuildingCreateShop.GaugePackagingTask;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Which gauge order the shop packages next. The three behaviours the release depends on: an order
 * whose goods are there is served even when an older order is still waiting, the preview and the
 * real pull agree, and one package never holds more than it can carry.
 *
 * <p>Needs real ItemStacks for the stack sizes, so it runs with a loaded FML.
 */
@Tag("fml")
class GaugePackageSelectionFmlTest {
  private static final ItemStack TORCH = new ItemStack(Items.TORCH);
  private static final ItemStack PLATE = new ItemStack(Items.IRON_INGOT);

  /** What the racks hold, per item, and what was asked of them. */
  private final Map<String, Integer> racks = new HashMap<>();

  private final List<String> pulls = new ArrayList<>();

  private final GaugePackageSelection.RackSource source =
      (key, amount, simulate) -> {
        pulls.add(key.getItem() + ":" + amount + (simulate ? ":sim" : ":real"));
        int held = racks.getOrDefault(key.getItem().toString(), 0);
        int pulled = Math.min(held, amount);
        if (pulled <= 0) {
          return ItemStack.EMPTY;
        }
        if (!simulate) {
          racks.put(key.getItem().toString(), held - pulled);
        }
        return key.copyWithCount(pulled);
      };

  @Test
  void anOrderWhoseGoodsAreThereIsServedWhileAnOlderOneStillWaits() {
    // The plates were ordered first and need a crafter; the torches came in by courier minutes ago.
    List<GaugePackagingTask> tasks = List.of(task(PLATE, 64, "gauge-a"), task(TORCH, 8, "gauge-b"));
    racks.put(TORCH.getItem().toString(), 8);

    GaugePackageSelection.Choice choice = GaugePackageSelection.select(tasks, source, false);

    assertNotNull(choice, "the torches are in the racks and nothing should hold them back");
    assertEquals("gauge-b", choice.task().gaugeAddress());
    assertEquals(8, choice.extracted().getCount());
  }

  @Test
  void racksThatHoldNothingOfAnyOrderPackageNothing() {
    List<GaugePackagingTask> tasks = List.of(task(PLATE, 64, "gauge-a"), task(TORCH, 8, "gauge-b"));

    assertNull(GaugePackageSelection.select(tasks, source, true));
  }

  @Test
  void thePreviewAndTheRealPullPickTheSameTaskAndTheSameAmount() {
    List<GaugePackagingTask> tasks =
        List.of(task(PLATE, 64, "gauge-a"), task(TORCH, 20, "gauge-b"));
    racks.put(TORCH.getItem().toString(), 12);

    GaugePackageSelection.Choice preview = GaugePackageSelection.select(tasks, source, true);
    GaugePackageSelection.Choice real = GaugePackageSelection.select(tasks, source, false);

    // Create ships what the preview shows and then asks again. A real pull that hands out a
    // different amount is how the same goods left the shop over and over.
    assertEquals(preview.task().gaugeAddress(), real.task().gaugeAddress());
    assertEquals(preview.extracted().getCount(), real.extracted().getCount());
    assertEquals(12, real.extracted().getCount());
  }

  @Test
  void anOrderLargerThanOnePackageIsAskedForInPackageSizedBites() {
    // Nine slots of a stackable item, and the racks hold more than that.
    List<GaugePackagingTask> tasks = List.of(task(TORCH, 4000, "gauge-a"));
    racks.put(TORCH.getItem().toString(), 4000);

    GaugePackageSelection.Choice choice = GaugePackageSelection.select(tasks, source, false);

    int capacity = 9 * TORCH.getMaxStackSize();
    assertEquals(capacity, choice.extracted().getCount());
    // A slot holding more than a stack cannot be written to disk, so the package is never asked to
    // carry the whole order.
    assertTrue(pulls.get(0).endsWith(":" + capacity + ":real"), pulls.toString());
  }

  private static GaugePackagingTask task(ItemStack item, int amount, String address) {
    return new GaugePackagingTask(item.copy(), amount, address, UUID.randomUUID());
  }
}
