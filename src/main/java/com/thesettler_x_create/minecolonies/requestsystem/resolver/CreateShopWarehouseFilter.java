package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import com.minecolonies.api.colony.buildings.workerbuildings.IWareHouse;
import com.thesettler_x_create.minecolonies.building.BuildingCreateShop;

/**
 * Shared classification of which colony buildings count as a "real" warehouse for delivery/courier
 * bookkeeping. {@code BuildingCreateShop} is never actually an {@link IWareHouse} (verified: it
 * extends {@code AbstractBuilding} directly, only {@code BuildingWareHouse} implements {@link
 * IWareHouse}), so the exclusion below is defensive rather than load-bearing today - but every call
 * site should still express the same two-part check explicitly, so a future change to either mod's
 * class hierarchy can't silently start treating the Create Shop as a courier warehouse.
 */
final class CreateShopWarehouseFilter {
  private CreateShopWarehouseFilter() {}

  static boolean isRelevantWarehouse(Object building) {
    return building instanceof IWareHouse && !(building instanceof BuildingCreateShop);
  }
}
