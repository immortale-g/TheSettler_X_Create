package com.thesettler_x_create.minecolonies.building;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.core.colony.buildings.modules.BuildingModules;

/** Maintains Create Shop registration in the MineColonies warehouse list. */
final class ShopWarehouseRegistrar {
  private final BuildingCreateShop shop;

  ShopWarehouseRegistrar(BuildingCreateShop shop) {
    this.shop = shop;
  }

  void ensureWarehouseRegistration() {
    IColony colony = shop.getColony();
    if (colony == null) {
      return;
    }
    var manager = colony.getServerBuildingManager();
    if (manager == null) {
      return;
    }
    var warehouses = manager.getWareHouses();
    if (warehouses == null) {
      return;
    }
    if (!hasWarehouseModules()) {
      warehouses.remove(shop);
      shop.warehouseRegistered = false;
      return;
    }
    if (!warehouses.contains(shop)) {
      warehouses.add(shop);
    }
    shop.warehouseRegistered = true;
  }

  boolean hasWarehouseModules() {
    return shop.getModule(BuildingModules.WAREHOUSE_REQUEST_QUEUE) != null;
  }
}
