package com.thesettler_x_create.minecolonies.building;

import com.minecolonies.api.colony.buildings.workerbuildings.IWareHouse;
import com.minecolonies.api.tileentities.AbstractTileEntityWareHouse;
import java.util.List;
import net.minecraft.world.item.ItemStack;

/**
 * Small warehouse-stock query shared by any Create Shop flow that needs to know how much of an item
 * the colony's warehouses hold before placing a request against them - both the (disabled)
 * perma-request auto-restocker and the live Colony Factory Gauge pipeline need exactly this same
 * check so a request only gets created when the warehouse actually has the stock to back it.
 */
final class ShopWarehouseStockUtil {
  private ShopWarehouseStockUtil() {}

  /**
   * Sums matching item stacks across all of the colony's MineColonies warehouses (excluding {@code
   * shop} itself, in case it is ever registered as one).
   */
  static int countInWarehouses(BuildingCreateShop shop, ItemStack stack) {
    if (stack == null || stack.isEmpty() || shop.getColony() == null) {
      return 0;
    }
    var manager = shop.getColony().getServerBuildingManager();
    if (manager == null) {
      return 0;
    }
    List<IWareHouse> warehouses = manager.getWareHouses();
    if (warehouses == null || warehouses.isEmpty()) {
      return 0;
    }
    int total = 0;
    for (IWareHouse warehouse : warehouses) {
      if (warehouse == null || warehouse == shop) {
        continue;
      }
      if (!(warehouse.getTileEntity() instanceof AbstractTileEntityWareHouse wareHouse)) {
        continue;
      }
      for (var entry :
          wareHouse.getMatchingItemStacksInWarehouse(match -> matchesStack(match, stack))) {
        ItemStack found = entry.getA();
        if (found == null || found.isEmpty()) {
          continue;
        }
        total += found.getCount();
      }
    }
    return Math.max(0, total);
  }

  private static boolean matchesStack(ItemStack candidate, ItemStack target) {
    if (candidate == null || target == null) {
      return false;
    }
    return ItemStack.isSameItemSameComponents(candidate, target);
  }
}
