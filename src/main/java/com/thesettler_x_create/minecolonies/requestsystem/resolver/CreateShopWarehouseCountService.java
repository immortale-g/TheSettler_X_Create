package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import com.minecolonies.api.colony.requestsystem.location.ILocation;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.requestable.IDeliverable;
import com.thesettler_x_create.blockentity.CreateShopBlockEntity;
import com.thesettler_x_create.minecolonies.building.BuildingCreateShop;
import com.thesettler_x_create.minecolonies.tileentity.TileEntityCreateShop;

/**
 * Resolves effective Create network stock count exposed to MineColonies warehouse resolver hooks.
 */
final class CreateShopWarehouseCountService {
  int getWarehouseInternalCount(
      ILocation resolverLocation,
      IRequest<? extends IDeliverable> request,
      CreateShopStockResolver stockResolver) {
    if (request == null || resolverLocation == null) {
      return 0;
    }
    IDeliverable deliverable = request.getRequest();
    if (deliverable == null) {
      return 0;
    }
    var colonyManager = com.minecolonies.api.colony.IColonyManager.getInstance();
    if (colonyManager == null) {
      return 0;
    }
    var colony =
        colonyManager.getColonyByPosFromDim(
            resolverLocation.getDimension(), resolverLocation.getInDimensionLocation());
    if (colony == null || colony.getServerBuildingManager() == null) {
      return 0;
    }
    var building =
        colony.getServerBuildingManager().getBuilding(resolverLocation.getInDimensionLocation());
    BuildingCreateShop shop = building instanceof BuildingCreateShop createShop ? createShop : null;
    if (shop == null) {
      return 0;
    }
    TileEntityCreateShop tile = shop.getCreateShopTileEntity();
    if (tile == null || tile.getStockNetworkId() == null) {
      return 0;
    }
    CreateShopBlockEntity pickup = shop.getPickupBlockEntity();
    if (pickup == null) {
      return 0;
    }
    int available = stockResolver.getNetworkAvailable(tile, deliverable);
    int reserved = pickup.getReservedForDeliverable(deliverable);
    return Math.max(0, available - reserved);
  }
}
