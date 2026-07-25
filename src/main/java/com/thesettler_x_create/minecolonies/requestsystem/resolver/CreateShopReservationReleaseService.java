package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.requestsystem.location.ILocation;
import com.minecolonies.api.colony.requestsystem.manager.IRequestManager;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.request.RequestState;
import com.minecolonies.api.colony.requestsystem.requestable.IDeliverable;
import com.minecolonies.api.colony.requestsystem.requestable.deliveryman.Delivery;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.thesettler_x_create.Config;
import com.thesettler_x_create.TheSettlerXCreate;
import com.thesettler_x_create.blockentity.CreateShopBlockEntity;
import com.thesettler_x_create.minecolonies.building.BuildingCreateShop;
import com.thesettler_x_create.minecolonies.tileentity.TileEntityCreateShop;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;

/** Releases reserved pickup stock and clears matching inflight data for cancelled deliveries. */
final class CreateShopReservationReleaseService {
  private final CreateShopResolverMessaging messaging;

  CreateShopReservationReleaseService(CreateShopResolverMessaging messaging) {
    this.messaging = messaging;
  }

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
    pickup.release(CreateShopRequestResolver.toRequestId(request.getId()));
    if (request.getState() != RequestState.CANCELLED
        || !(request.getRequest() instanceof Delivery delivery)) {
      return;
    }
    ItemStack key = delivery.getStack();
    if (key == null || key.isEmpty()) {
      return;
    }
    // UUID-first cancel: precise and drift-free for entries recorded since Phase 3.1.
    java.util.UUID requestUuid = CreateShopRequestResolver.toRequestId(request.getId());
    int cleared = pickup.cancelInflightByUuid(requestUuid);
    // String-matching fallback for legacy entries (recorded before Phase 3.1, requestUuid == null).
    if (cleared <= 0) {
      String requesterName = messaging.resolveRequesterName(manager, request);
      TileEntityCreateShop tile = shop.getCreateShopTileEntity();
      String address = sanitizeAddress(tile == null ? "" : tile.getShopAddress());
      cleared = shop.cancelLostPackage(key, requesterName, address, -1L);
    }
    if (Config.DEBUG_LOGGING.getAsBoolean()) {
      TheSettlerXCreate.LOGGER.info(
          "[CreateShop] releaseReservation cancelled request={} clearedInflight={}",
          request.getId(),
          cleared);
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

  private static String sanitizeAddress(String value) {
    if (value == null) {
      return "";
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? "" : trimmed;
  }
}

/** Syncs missing request reservations from currently available rack stock. */
final class CreateShopReservationSyncService {
  private final CreateShopRequestStateMutatorService requestStateMutatorService;
  private final CreateShopResolverDiagnostics diagnostics;

  CreateShopReservationSyncService(
      CreateShopRequestStateMutatorService requestStateMutatorService,
      CreateShopResolverDiagnostics diagnostics) {
    this.requestStateMutatorService = requestStateMutatorService;
    this.diagnostics = diagnostics;
  }

  int syncReservationsFromRack(
      CreateShopRequestResolver resolver,
      TileEntityCreateShop tile,
      CreateShopBlockEntity pickup,
      UUID requestId,
      IToken<?> requestToken,
      IDeliverable deliverable,
      int pendingCount,
      int reservedForRequest,
      int rackAvailable,
      long now) {
    if (resolver == null
        || tile == null
        || pickup == null
        || requestId == null
        || requestToken == null
        || deliverable == null
        || pendingCount <= 0
        || rackAvailable <= 0) {
      return 0;
    }
    int reservedForDeliverable = pickup.getReservedForDeliverable(deliverable);
    int rackUnreserved = Math.max(0, rackAvailable - Math.max(0, reservedForDeliverable));
    int missingReservation = Math.max(0, pendingCount - Math.max(0, reservedForRequest));
    int reserveTarget = Math.min(rackUnreserved, missingReservation);
    if (reserveTarget <= 0) {
      return 0;
    }
    List<com.minecolonies.api.util.Tuple<ItemStack, BlockPos>> reservePlan =
        resolver
            .getPlanning()
            .planFromRacksWithPositions(tile, deliverable, Math.max(1, reserveTarget));
    if (reservePlan.isEmpty()) {
      return 0;
    }
    int reservedNow = 0;
    for (var entry : reservePlan) {
      if (entry == null) {
        continue;
      }
      ItemStack stack = entry.getA();
      if (stack == null || stack.isEmpty()) {
        continue;
      }
      pickup.reserve(requestId, stack.copy(), stack.getCount());
      reservedNow += stack.getCount();
    }
    if (reservedNow > 0) {
      requestStateMutatorService.markOrderedWithPendingAtLeastOne(
          resolver, null, requestToken, pendingCount);
      diagnostics.recordPendingSource(requestToken, "tickPending:reservation-refresh");
      resolver.touchFlow(requestToken, now, "tickPending:reservation-refresh");
    }
    return reservedNow;
  }
}
