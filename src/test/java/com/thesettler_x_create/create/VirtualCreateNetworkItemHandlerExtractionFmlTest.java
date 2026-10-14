package com.thesettler_x_create.create;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The virtual pickup slot merges every rack slot of an item, so one call must never hand out more
 * than one real stack. Needs real ItemStacks for the stack sizes, so it runs with a loaded FML.
 */
@Tag("fml")
class VirtualCreateNetworkItemHandlerExtractionFmlTest {
  @Test
  void aNonStackableItemIsCappedAtOne() {
    ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);

    assertEquals(1, VirtualCreateNetworkItemHandler.cappedExtractable(sword, 64, 0, 5));
  }

  @Test
  void aSixteenStackItemIsCappedAtSixteen() {
    ItemStack pearl = new ItemStack(Items.ENDER_PEARL);

    assertEquals(16, VirtualCreateNetworkItemHandler.cappedExtractable(pearl, 64, 0, 64));
  }

  @Test
  void reservedStockStillComesOffBeforeTheCap() {
    ItemStack cobble = new ItemStack(Items.COBBLESTONE);

    assertEquals(3, VirtualCreateNetworkItemHandler.cappedExtractable(cobble, 64, 7, 10));
  }

  @Test
  void extractionSpansSlotsAndSimulateRemovesNothing() {
    ItemStackHandler rack = new ItemStackHandler(3);
    rack.setStackInSlot(0, new ItemStack(Items.COBBLESTONE, 10));
    rack.setStackInSlot(2, new ItemStack(Items.COBBLESTONE, 20));
    List<VirtualCreateNetworkItemHandler.RackSlot> slots =
        List.of(
            new VirtualCreateNetworkItemHandler.RackSlot(rack, 0, 10),
            new VirtualCreateNetworkItemHandler.RackSlot(rack, 2, 20));
    ItemStack key = new ItemStack(Items.COBBLESTONE);

    ItemStack simulated = VirtualCreateNetworkItemHandler.tryExtractFromRacks(key, slots, 15, true);
    assertEquals(15, simulated.getCount());
    assertEquals(10, rack.getStackInSlot(0).getCount());

    ItemStack real = VirtualCreateNetworkItemHandler.tryExtractFromRacks(key, slots, 15, false);
    assertEquals(15, real.getCount());
    assertTrue(rack.getStackInSlot(0).isEmpty());
    assertEquals(15, rack.getStackInSlot(2).getCount());
  }
}
