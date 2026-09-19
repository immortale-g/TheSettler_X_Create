package com.thesettler_x_create.create;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.serialization.DataResult;
import java.util.List;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

/**
 * What the shop puts into a package and what comes back out of it. The counts matter twice over: a
 * slot holding more than a saved stack takes the package with it when the chunk is written, and a
 * package read back one slot at a time under-reports what arrived.
 */
@org.junit.jupiter.api.Tag("fml")
class CreatePackageBridgeFmlTest {

  @Test
  void aLargeAmountIsSpreadOverTheSlotsInsteadOfPiledIntoOne() {
    ItemStack pkg = CreatePackageBridge.buildPackage(new ItemStack(Items.COBBLESTONE, 128), "shop");

    List<ItemStack> contents = CreatePackageBridge.readContents(pkg);
    assertEquals(2, contents.size(), "128 cobblestone is two slots, not one");
    for (ItemStack slot : contents) {
      assertTrue(
          slot.getCount() <= slot.getMaxStackSize(),
          "a slot over its stack size cannot be written to disk");
    }
    assertEquals(128, CreatePackageBridge.sumOfFirstKind(contents).getCount());
  }

  @Test
  void aPackageBuiltAtCapacitySurvivesBeingSaved() {
    ItemStack item = new ItemStack(Items.COBBLESTONE);
    ItemStack pkg =
        CreatePackageBridge.buildPackage(
            item.copyWithCount(CreatePackageBridge.capacityFor(item)), "shop");

    // The real failure was invisible until a chunk was saved: the contents component encodes every
    // slot through ItemStack.CODEC, which refuses a count above 99.
    DataResult<Tag> encoded = ItemStack.CODEC.encodeStart(NbtOps.INSTANCE, pkg);
    assertTrue(encoded.isSuccess(), String.valueOf(encoded.error().orElse(null)));
  }

  @Test
  void aGaugePackageSaysWhatIsStillOwedAfterIt() {
    ItemStack pkg =
        CreatePackageBridge.buildGaugePackage(new ItemStack(Items.TORCH, 6), "gauge-one", 4);

    assertEquals(4, CreatePackageBridge.readGaugeOrderOpen(pkg));
  }

  @Test
  void aPackageFromAnywhereElseSaysNothing() {
    ItemStack pkg = CreatePackageBridge.buildPackage(new ItemStack(Items.TORCH, 6), "gauge-one");

    assertNull(CreatePackageBridge.readGaugeOrderOpen(pkg));
  }

  @Test
  void slotsHoldingSomethingElseAreNotCountedTowardsTheFirstItem() {
    List<ItemStack> mixed =
        List.of(
            new ItemStack(Items.TORCH, 8),
            new ItemStack(Items.STONE, 5),
            new ItemStack(Items.TORCH, 3));

    assertEquals(11, CreatePackageBridge.sumOfFirstKind(mixed).getCount());
    assertEquals(Items.TORCH, CreatePackageBridge.sumOfFirstKind(mixed).getItem());
  }

  @Test
  void nothingInMeansNothingOut() {
    assertTrue(CreatePackageBridge.sumOfFirstKind(List.of()).isEmpty());
    assertEquals(0, CreatePackageBridge.capacityFor(ItemStack.EMPTY));
  }
}
