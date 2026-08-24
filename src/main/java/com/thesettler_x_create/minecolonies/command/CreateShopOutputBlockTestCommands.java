package com.thesettler_x_create.minecolonies.command;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.thesettler_x_create.blockentity.CreateShopOutputBlockEntity;
import com.thesettler_x_create.minecolonies.building.BuildingCreateShop;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * In-game diagnostic and smoke-test commands for the OutputBlock packaging mode.
 *
 * <p>Commands (registered under /thesettlerxcreate):
 *
 * <ul>
 *   <li>diag_output_block — dumps packageAddress, slot count, and slot-0 content for every shop
 *   <li>test_output_packaging — asserts slot_guard, empty_addr_guard, and packaging_mode for every
 *       output block found
 * </ul>
 */
final class CreateShopOutputBlockTestCommands {
  private CreateShopOutputBlockTestCommands() {}

  // -------------------------------------------------------------------------
  // diag_output_block
  // -------------------------------------------------------------------------

  static int runOutputBlockDiag(CommandSourceStack source) {
    int shopsTotal = 0;
    int shopsWithBlock = 0;
    int errors = 0;

    for (IColony colony : IColonyManager.getInstance().getAllColonies()) {
      var bm = colony.getServerBuildingManager();
      if (bm == null || bm.getBuildings() == null) {
        continue;
      }
      for (var entry : bm.getBuildings().entrySet()) {
        if (!(entry.getValue() instanceof BuildingCreateShop shop)) {
          continue;
        }
        shopsTotal++;
        String loc = shop.getLocation().getInDimensionLocation().toString();

        if (!shop.hasOutputBlock()) {
          source.sendSuccess(
              () ->
                  Component.literal(
                      "[CreateShop/OutputBlock] shop=" + loc + " hasOutputBlock=false"),
              false);
          continue;
        }
        shopsWithBlock++;

        CreateShopOutputBlockEntity obe = shop.getOutputBlockEntity();
        if (obe == null) {
          errors++;
          source.sendFailure(
              Component.literal(
                  "[CreateShop/OutputBlock] shop="
                      + loc
                      + " hasOutputBlock=true but entity=null ERROR"));
          continue;
        }

        String pkgAddr = obe.getPackageAddress();
        IItemHandler handler = obe.getItemHandler(null);
        int slots = handler.getSlots();
        ItemStack slot0 = handler.getStackInSlot(0);
        int permaOres = countIterable(shop.getPermaOres());
        String slot0Desc = describeSlot0(slot0);

        String line =
            "[CreateShop/OutputBlock] shop="
                + loc
                + " packageAddress=\""
                + pkgAddr
                + "\""
                + " slots="
                + slots
                + " slot0="
                + slot0Desc
                + " permaOres="
                + permaOres
                + (slots == 1 ? "" : " WARN:slots!=1")
                + (pkgAddr.isEmpty() ? " WARN:no_address" : "");
        source.sendSuccess(() -> Component.literal(line), false);
      }
    }

    final int totalF = shopsTotal;
    final int blockF = shopsWithBlock;
    final int errF = errors;
    source.sendSuccess(
        () ->
            Component.literal(
                "[CreateShop/OutputBlock] done: shopsTotal="
                    + totalF
                    + " shopsWithBlock="
                    + blockF
                    + " errors="
                    + errF),
        true);
    return errors == 0 ? 1 : 0;
  }

  // -------------------------------------------------------------------------
  // test_output_packaging
  // -------------------------------------------------------------------------

  static int runOutputBlockTest(CommandSourceStack source) {
    int passed = 0;
    int failed = 0;
    int info = 0;
    boolean foundBlock = false;

    for (IColony colony : IColonyManager.getInstance().getAllColonies()) {
      var bm = colony.getServerBuildingManager();
      if (bm == null || bm.getBuildings() == null) {
        continue;
      }
      for (var entry : bm.getBuildings().entrySet()) {
        if (!(entry.getValue() instanceof BuildingCreateShop shop)) {
          continue;
        }
        if (!shop.hasOutputBlock()) {
          continue;
        }
        CreateShopOutputBlockEntity obe = shop.getOutputBlockEntity();
        if (obe == null) {
          continue;
        }
        foundBlock = true;
        String loc = shop.getLocation().getInDimensionLocation().toString();
        source.sendSuccess(
            () -> Component.literal("[CreateShop/OutputBlock/TEST] testing shop=" + loc), false);

        IItemHandler handler = obe.getItemHandler(null);
        int slots = handler.getSlots();
        String pkgAddr = obe.getPackageAddress();

        // --- Check 1: slot count must always be exactly 1 ---
        if (slots == 1) {
          source.sendSuccess(
              () -> Component.literal("[CreateShop/OutputBlock/TEST] slot_guard: slots=1 PASS"),
              false);
          passed++;
        } else {
          source.sendFailure(
              Component.literal(
                  "[CreateShop/OutputBlock/TEST] slot_guard: slots="
                      + slots
                      + " FAIL (expected 1, got N-slot raw-items mode)"));
          failed++;
        }

        // --- Check 2a: empty address → EMPTY ---
        if (pkgAddr.isEmpty()) {
          ItemStack slot0 = handler.getStackInSlot(0);
          if (slot0.isEmpty()) {
            source.sendSuccess(
                () ->
                    Component.literal(
                        "[CreateShop/OutputBlock/TEST] empty_addr_guard: addr=\"\" slot0=EMPTY PASS"),
                false);
            passed++;
          } else {
            String desc = describeSlot0(slot0);
            source.sendFailure(
                Component.literal(
                    "[CreateShop/OutputBlock/TEST] empty_addr_guard: addr=\"\" slot0="
                        + desc
                        + " FAIL (expected EMPTY when no address configured)"));
            failed++;
          }
          source.sendSuccess(
              () ->
                  Component.literal(
                      "[CreateShop/OutputBlock/TEST] packaging_mode: no address set"
                          + " — configure one in the Address tab, add items to racks, then re-run INFO"),
              false);
          info++;
          continue;
        }

        // --- Check 2b: address set → PackageItem or EMPTY (if racks empty) ---
        ItemStack slot0 = handler.getStackInSlot(0);
        if (slot0.isEmpty()) {
          source.sendSuccess(
              () ->
                  Component.literal(
                      "[CreateShop/OutputBlock/TEST] packaging_mode: addr=\""
                          + pkgAddr
                          + "\" slot0=EMPTY (racks empty) INFO — fill racks with perma-items to test"),
              false);
          info++;
        } else if (slot0.getItem() instanceof PackageItem) {
          String slotAddr = PackageItem.getAddress(slot0);
          int itemSlots = countPackageItems(slot0);
          if (pkgAddr.equals(slotAddr)) {
            String pass =
                "[CreateShop/OutputBlock/TEST] packaging_mode: addr=\""
                    + pkgAddr
                    + "\" slot0=PackageItem(addr=\""
                    + slotAddr
                    + "\" itemSlots="
                    + itemSlots
                    + ") PASS";
            source.sendSuccess(() -> Component.literal(pass), false);
            passed++;
          } else {
            source.sendFailure(
                Component.literal(
                    "[CreateShop/OutputBlock/TEST] packaging_mode: address_mismatch"
                        + " configured=\""
                        + pkgAddr
                        + "\" inPackage=\""
                        + slotAddr
                        + "\" FAIL"));
            failed++;
          }
        } else {
          String desc = describeSlot0(slot0);
          source.sendFailure(
              Component.literal(
                  "[CreateShop/OutputBlock/TEST] packaging_mode: addr=\""
                      + pkgAddr
                      + "\" slot0="
                      + desc
                      + " FAIL (expected PackageItem, got something else)"));
          failed++;
        }
      }
    }

    if (!foundBlock) {
      source.sendFailure(
          Component.literal(
              "[CreateShop/OutputBlock/TEST] no output block found"
                  + " — place and link a create_shop_output block first"));
      return 0;
    }

    final int pF = passed;
    final int fF = failed;
    final int iF = info;
    source.sendSuccess(
        () ->
            Component.literal(
                "[CreateShop/OutputBlock/TEST] OVERALL: "
                    + (fF == 0 ? "PASS" : "FAIL")
                    + " passed="
                    + pF
                    + " failed="
                    + fF
                    + " info="
                    + iF),
        true);
    return failed == 0 ? 1 : 0;
  }

  // -------------------------------------------------------------------------
  // Helpers
  // -------------------------------------------------------------------------

  private static String describeSlot0(ItemStack stack) {
    if (stack.isEmpty()) {
      return "EMPTY";
    }
    if (stack.getItem() instanceof PackageItem) {
      String addr = PackageItem.getAddress(stack);
      int items = countPackageItems(stack);
      return "PackageItem(addr=\"" + addr + "\" itemSlots=" + items + ")";
    }
    return "OTHER:" + stack.getHoverName().getString();
  }

  private static int countPackageItems(ItemStack pkg) {
    var contents = PackageItem.getContents(pkg);
    if (contents == null) {
      return 0;
    }
    int count = 0;
    for (int i = 0; i < contents.getSlots(); i++) {
      if (!contents.getStackInSlot(i).isEmpty()) {
        count++;
      }
    }
    return count;
  }

  // -------------------------------------------------------------------------
  // diag_perma_requests
  // -------------------------------------------------------------------------

  static int runPermaRequestDiag(CommandSourceStack source) {
    int shops = 0;
    for (IColony colony : IColonyManager.getInstance().getAllColonies()) {
      var bm = colony.getServerBuildingManager();
      if (bm == null || bm.getBuildings() == null) {
        continue;
      }
      for (var entry : bm.getBuildings().entrySet()) {
        if (!(entry.getValue()
            instanceof com.thesettler_x_create.minecolonies.building.BuildingCreateShop shop)) {
          continue;
        }
        shops++;
        String loc = shop.getLocation().getInDimensionLocation().toString();
        var permaOres = shop.getPermaOres();
        boolean canUse = shop.canUsePermaRequests();
        boolean workerWorking = shop.isWorkerWorking();

        source.sendSuccess(
            () ->
                Component.literal(
                    "[CreateShop/PERMA] shop="
                        + loc
                        + " canUsePerma="
                        + canUse
                        + " workerWorking="
                        + workerWorking
                        + " ores="
                        + permaOres.size()),
            false);

        // Count items in racks vs colony warehouse
        var shopTE = shop.getCreateShopTileEntity();
        for (var oreId : permaOres) {
          var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(oreId);
          if (item == null || item == net.minecraft.world.item.Items.AIR) {
            source.sendSuccess(
                () -> Component.literal("[CreateShop/PERMA]   " + oreId + " -> item not found"),
                false);
            continue;
          }
          var stack = new ItemStack(item, 1);

          // Count in racks
          int inRacks = 0;
          if (shopTE != null) {
            for (var entry2 :
                shopTE.getMatchingItemStacksInWarehouse(
                    s -> ItemStack.isSameItemSameComponents(s, stack))) {
              if (entry2.getA() != null && !entry2.getA().isEmpty())
                inRacks += entry2.getA().getCount();
            }
          }

          // Count in colony warehouses
          int inWarehouse = 0;
          var warehouses = colony.getServerBuildingManager().getWareHouses();
          if (warehouses != null) {
            for (var wh : warehouses) {
              if (wh == null || wh == shop) continue;
              if (!(wh.getTileEntity()
                  instanceof com.minecolonies.api.tileentities.AbstractTileEntityWareHouse whTE))
                continue;
              for (var e :
                  whTE.getMatchingItemStacksInWarehouse(
                      s -> ItemStack.isSameItemSameComponents(s, stack))) {
                if (e.getA() != null && !e.getA().isEmpty()) inWarehouse += e.getA().getCount();
              }
            }
          }

          final int rF = inRacks;
          final int wF = inWarehouse;
          source.sendSuccess(
              () ->
                  Component.literal(
                      "[CreateShop/PERMA]   ore="
                          + oreId
                          + " inRacks="
                          + rF
                          + " inColonyWarehouse="
                          + wF),
              false);
        }

        // Show pending request states
        source.sendSuccess(() -> Component.literal("[CreateShop/PERMA] pending requests:"), false);
        for (String line : shop.getPermaPendingDebugLines()) {
          source.sendSuccess(() -> Component.literal("[CreateShop/PERMA]" + line), false);
        }
      }
    }
    if (shops == 0) {
      source.sendFailure(Component.literal("[CreateShop/PERMA] no create shops found"));
    }
    return shops > 0 ? 1 : 0;
  }

  private static int countIterable(Iterable<?> iterable) {
    if (iterable == null) {
      return 0;
    }
    if (iterable instanceof java.util.Collection<?> col) {
      return col.size();
    }
    int n = 0;
    var it = iterable.iterator();
    while (it.hasNext()) {
      it.next();
      n++;
    }
    return n;
  }
}
