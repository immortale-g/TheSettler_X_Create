package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import com.minecolonies.api.colony.requestsystem.location.ILocation;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.requestable.IDeliverable;
import com.thesettler_x_create.blockentity.CreateShopBlockEntity;
import com.thesettler_x_create.minecolonies.building.BuildingCreateShop;
import com.thesettler_x_create.minecolonies.tileentity.TileEntityCreateShop;
import com.thesettler_x_create.stock.ShopStockAccounting;

/**
 * Count behind {@code AbstractWarehouseRequestResolver#getWarehouseInternalCount}: what the shop
 * can hand out for a request, network stock plus rack stock not reserved for other requests.
 *
 * <p>MineColonies only calls this hook from the base class {@code canResolveRequest}, which {@link
 * CreateShopRequestResolver} replaces with its own validator, and other warehouses only count
 * buildings of the warehouse type. It is kept consistent with the validator so it cannot mislead if
 * that ever changes.
 */
final class CreateShopWarehouseCountService {
  int getWarehouseInternalCount(
      ILocation resolverLocation,
      IRequest<? extends IDeliverable> request,
      CreateShopStockResolver stockResolver,
      CreateShopResolverPlanning planning) {
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
    // Network stock and rack reservations are two different stores; reservations only ever lower
    // what the racks can give.
    int reservedForRequest =
        pickup.getReservedForRequest(CreateShopRequestResolver.toRequestId(request.getId()));
    int reservedForOthers =
        ShopStockAccounting.reservedForOthers(
            pickup.getReservedForDeliverable(deliverable), reservedForRequest);
    CreateShopStockSnapshot snapshot =
        stockResolver.getAvailability(tile, pickup, deliverable, reservedForOthers, planning);
    return ShopStockAccounting.totalAvailable(
        snapshot.networkAvailable(), snapshot.rackUsable(), 0);
  }
}
