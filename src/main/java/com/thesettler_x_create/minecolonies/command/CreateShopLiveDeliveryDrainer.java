package com.thesettler_x_create.minecolonies.command;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.requestsystem.request.RequestState;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.core.colony.requestsystem.management.IStandardRequestManager;
import com.thesettler_x_create.TheSettlerXCreate;
import com.thesettler_x_create.minecolonies.building.BuildingCreateShop;

/**
 * Cancels active local-delivery Delivery requests as part of {@code reset_live_state}. Extracted
 * from {@link CreateShopResetCommands}, which still owns the drain-round orchestration and the
 * shared {@link CreateShopResetCommands#handleGraphException} classifier.
 */
final class CreateShopLiveDeliveryDrainer {
  private CreateShopLiveDeliveryDrainer() {}

  static void cancelActiveLocalDeliveries(
      IColony colony, IStandardRequestManager standard, ResetLiveStateResult result) {
    if (colony == null || standard == null || result == null) {
      return;
    }
    var store = standard.getRequestResolverRequestAssignmentDataStore();
    if (store == null || store.getAssignments() == null || store.getAssignments().isEmpty()) {
      return;
    }
    var requestHandler = standard.getRequestHandler();
    if (requestHandler == null) {
      return;
    }
    java.util.Set<BuildingCreateShop> shops = CreateShopCommandSupport.collectCreateShops(colony);
    if (shops.isEmpty()) {
      return;
    }

    for (var assignmentEntry : store.getAssignments().entrySet()) {
      var assigned = assignmentEntry.getValue();
      if (assigned == null || assigned.isEmpty()) {
        continue;
      }
      for (IToken<?> token : java.util.List.copyOf(assigned)) {
        if (token == null) {
          continue;
        }
        try {
          var request = requestHandler.getRequestOrNull(token);
          if (request == null
              || !(request.getRequest()
                  instanceof
                  com.minecolonies.api.colony.requestsystem.requestable.deliveryman.Delivery)) {
            continue;
          }
          if (CreateShopCommandSupport.isTerminalState(request.getState())
              || !CreateShopCommandSupport.isCreateShopOwnedRequest(standard, request)) {
            continue;
          }
          boolean localDeliveryForAnyShop = false;
          for (BuildingCreateShop shop : shops) {
            if (shop != null && shop.hasActiveLocalDeliveryChildrenForInflight(colony)) {
              localDeliveryForAnyShop = true;
              break;
            }
          }
          if (!localDeliveryForAnyShop) {
            continue;
          }
          standard.updateRequestState(token, RequestState.CANCELLED);
          result.deliveryRequestsCancelled++;
        } catch (Exception ex) {
          CreateShopResetCommands.handleGraphException(
              ex, token, "reset_live_state cancel active delivery", result);
        }
      }
    }
  }

  static int countShopsWithActiveLocalDeliveries(
      IColony colony, java.util.Set<BuildingCreateShop> shops) {
    if (colony == null || shops == null || shops.isEmpty()) {
      return 0;
    }
    int active = 0;
    for (BuildingCreateShop shop : shops) {
      if (shop == null) {
        continue;
      }
      try {
        if (shop.hasActiveLocalDeliveryChildrenForInflight(colony)) {
          active++;
        }
      } catch (Exception ex) {
        // Fail closed: if we cannot verify cleanly, do not perform a destructive cleanup pass.
        active++;
        TheSettlerXCreate.LOGGER.warn(
            "[CreateShop] reset_live_state preflight failed shop={} error={}",
            shop.getLocation() == null ? "<unknown>" : shop.getLocation().getInDimensionLocation(),
            ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
      }
    }
    return active;
  }
}
