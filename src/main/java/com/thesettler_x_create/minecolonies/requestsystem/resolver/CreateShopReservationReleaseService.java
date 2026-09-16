package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.requestsystem.location.ILocation;
import com.minecolonies.api.colony.requestsystem.manager.IRequestManager;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.thesettler_x_create.DebugLog;
import com.thesettler_x_create.TheSettlerXCreate;
import com.thesettler_x_create.blockentity.CreateShopBlockEntity;
import com.thesettler_x_create.minecolonies.building.BuildingCreateShop;
import java.util.UUID;

/**
 * Lets go of what an ended request held in the shop: its reservations are released, and its orders
 * still on their way lose their owner instead of being forgotten, so the next request for the same
 * item claims them instead of ordering again.
 */
final class CreateShopReservationReleaseService {

  void releaseReservation(
      IRequestManager manager, IRequest<?> request, ILocation resolverLocation) {
    BuildingCreateShop shop = resolveShop(manager, resolverLocation);
    if (shop == null || request == null) {
      return;
    }
    CreateShopBlockEntity pickup = shop.getPickupBlockEntity();
    if (pickup == null) {
      return;
    }
    UUID requestId = CreateShopRequestResolver.toRequestId(request.getId());
    pickup.release(requestId);
    int detached = pickup.detachInflight(requestId);
    if (DebugLog.enabled() && detached > 0) {
      TheSettlerXCreate.LOGGER.info(
          "[CreateShop] releaseReservation request={} state={} detachedInflight={}",
          request.getId(),
          request.getState(),
          detached);
    }
  }

  private static BuildingCreateShop resolveShop(
      IRequestManager manager, ILocation resolverLocation) {
    if (manager == null || resolverLocation == null) {
      return null;
    }
    IColony colony = manager.getColony();
    if (colony == null || colony.getServerBuildingManager() == null) {
      return null;
    }
    var building =
        colony.getServerBuildingManager().getBuilding(resolverLocation.getInDimensionLocation());
    return building instanceof BuildingCreateShop shop ? shop : null;
  }
}
