package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import com.minecolonies.api.colony.requestsystem.requestable.IDeliverable;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.thesettler_x_create.blockentity.CreateShopBlockEntity;
import com.thesettler_x_create.minecolonies.tileentity.TileEntityCreateShop;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;

/** Syncs missing request reservations from currently available rack stock. */
final class CreateShopReservationSyncService {
  private final CreateShopRequestStateMutatorService requestStateMutatorService;

  CreateShopReservationSyncService(CreateShopRequestStateMutatorService requestStateMutatorService) {
    this.requestStateMutatorService = requestStateMutatorService;
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
      resolver.getDiagnosticsForOps().recordPendingSource(requestToken, "tickPending:reservation-refresh");
      resolver.touchFlow(requestToken, now, "tickPending:reservation-refresh");
    }
    return reservedNow;
  }
}
