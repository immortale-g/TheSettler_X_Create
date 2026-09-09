package com.thesettler_x_create.minecolonies.command;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.requestsystem.request.RequestState;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.core.colony.requestsystem.management.IStandardRequestManager;
import com.thesettler_x_create.TheSettlerXCreate;

/**
 * Prunes stale/terminal entries out of every building's warehouse request queue, and cancels the
 * still-active Create Shop-owned ones among them, as part of {@code reset_live_state}. Extracted
 * from {@link CreateShopResetCommands}, which still owns the drain-round orchestration and the
 * shared {@link CreateShopResetCommands#handleGraphException} classifier.
 */
final class CreateShopWarehouseQueuePruner {
  private CreateShopWarehouseQueuePruner() {}

  static void clearWarehouseQueues(
      IColony colony, IStandardRequestManager standard, ResetLiveStateResult result) {
    if (colony == null || standard == null || result == null) {
      return;
    }
    var buildingManager = colony.getServerBuildingManager();
    if (buildingManager == null || buildingManager.getBuildings() == null) {
      return;
    }
    for (var entry : buildingManager.getBuildings().entrySet()) {
      var building = entry.getValue();
      if (building == null) {
        continue;
      }
      var queue =
          building.getModule(
              com.minecolonies.core.colony.buildings.modules.BuildingModules
                  .WAREHOUSE_REQUEST_QUEUE);
      if (queue == null
          || queue.getMutableRequestList() == null
          || queue.getMutableRequestList().isEmpty()) {
        continue;
      }
      // Defensive snapshot, not the live queue list: standard.updateRequestState below can route
      // into DeliverymenRequestResolver.onAssignedRequestBeingCancelled, which itself calls
      // module.getMutableRequestList().remove(...) on this exact list - mutating it out from under
      // a raw Iterator we're still using would be unsafe.
      java.util.List<IToken<?>> liveQueue = queue.getMutableRequestList();
      for (IToken<?> queuedToken : java.util.List.copyOf(liveQueue)) {
        if (queuedToken == null) {
          liveQueue.remove(queuedToken);
          result.queueEntriesCleared++;
          continue;
        }
        try {
          var queuedRequest = standard.getRequestHandler().getRequestOrNull(queuedToken);
          if (queuedRequest == null) {
            liveQueue.remove(queuedToken);
            result.queueEntriesCleared++;
            result.staleCleaned++;
            continue;
          }
          if (!CreateShopCommandSupport.isTerminalState(queuedRequest.getState())) {
            if (!CreateShopCommandSupport.isCreateShopOwnedRequest(standard, queuedRequest)) {
              continue;
            }
            try {
              standard.updateRequestState(queuedToken, RequestState.CANCELLED);
              result.queueRequestsCancelled++;
            } catch (Exception cancelEx) {
              CreateShopResetCommands.handleGraphException(
                  cancelEx, queuedToken, "reset_live_state queue active cancel", result);
            }
            liveQueue.remove(queuedToken);
            result.queueEntriesCleared++;
            try {
              standard.getRequestHandler().cleanRequestData(queuedToken);
              result.staleCleaned++;
            } catch (Exception cleanEx) {
              CreateShopResetCommands.handleGraphException(
                  cleanEx, queuedToken, "reset_live_state queue active cleanup", result);
            }
            continue;
          }
          liveQueue.remove(queuedToken);
          result.queueEntriesCleared++;
          if (queuedRequest.getState() == RequestState.CANCELLED) {
            try {
              standard.getRequestHandler().cleanRequestData(queuedToken);
              result.staleCleaned++;
            } catch (Exception cleanEx) {
              result.errors++;
              TheSettlerXCreate.LOGGER.warn(
                  "[CreateShop] reset_live_state queue stale cleanup failed token={} error={}",
                  queuedToken,
                  cleanEx.getMessage() == null
                      ? cleanEx.getClass().getSimpleName()
                      : cleanEx.getMessage());
            }
          }
        } catch (Exception ex) {
          if (CreateShopResetCommands.handleGraphException(
              ex, queuedToken, "reset_live_state queue cancel", result)) {
            liveQueue.remove(queuedToken);
            result.queueEntriesCleared++;
            try {
              standard.getRequestHandler().cleanRequestData(queuedToken);
              result.staleCleaned++;
            } catch (Exception cleanEx) {
              result.errors++;
              TheSettlerXCreate.LOGGER.warn(
                  "[CreateShop] reset_live_state queue stale cleanup failed token={} error={}",
                  queuedToken,
                  cleanEx.getMessage() == null
                      ? cleanEx.getClass().getSimpleName()
                      : cleanEx.getMessage());
            }
            continue;
          }
        }
      }
    }
  }
}
