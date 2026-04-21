package com.thesettler_x_create.minecolonies.building;

import com.minecolonies.api.colony.IColony;

/** Ensures Create Shop is not treated as a courier warehouse by MineColonies. */
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
    warehouses.remove(shop);
    shop.warehouseRegistered = false;
  }
}
