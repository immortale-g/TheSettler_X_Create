package com.thesettler_x_create.minecolonies.command;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.requestsystem.request.RequestState;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.core.colony.requestsystem.management.IStandardRequestManager;
import com.thesettler_x_create.TheSettlerXCreate;
import com.thesettler_x_create.minecolonies.building.BuildingCreateShop;
import net.minecraft.commands.CommandSourceStack;

/**
 * Handles the {@code reset_live_state} command for the Create Shop building.
 *
 * <p>Extracted from {@link CreateShopMaintenanceCommands} to separate destructive maintenance
 * operations from diagnostic and test-harness commands. {@code prepare_uninstall} lives in {@link
 * CreateShopUninstallCommands}; helpers shared between the two live in {@link
 * CreateShopCommandSupport}.
 */
final class CreateShopResetCommands {
  private CreateShopResetCommands() {}

  // -------------------------------------------------------------------------
  // Package-visible entry point (called from CreateShopMaintenanceCommands)
  // -------------------------------------------------------------------------

  static ResetLiveStateResult resetLiveState(
      CommandSourceStack source, boolean forceWarehouseQueueClear) {
    ResetLiveStateResult result = new ResetLiveStateResult();
    for (IColony colony : CreateShopCommandSupport.resolveTargetColonies(source)) {
      result.colonies++;
      if (!(colony.getRequestManager() instanceof IStandardRequestManager standard)) {
        continue;
      }

      java.util.Set<BuildingCreateShop> shops = CreateShopCommandSupport.collectCreateShops(colony);
      result.shops += shops.size();
      int initialActiveLocalDeliveries = countShopsWithActiveLocalDeliveries(colony, shops);
      int drainRounds = Math.max(1, 3 + (initialActiveLocalDeliveries > 0 ? 1 : 0));
      result.drainRounds += drainRounds;
      for (int round = 0; round < drainRounds; round++) {
        cancelCreateShopOwnedRequestsGraphAware(standard, result);
        cancelAllAssignedRequestsGraphAware(standard, result);
        cancelActiveLocalDeliveries(colony, standard, result);
        reconcileAssignmentsAndKickCouriers(standard, result);
      }

      // Always prune stale/terminal queue entries; this is conservative and prevents stale
      // warehouse queue tokens from keeping courier jobs in a stuck loop after request cleanup.
      clearWarehouseQueues(colony, standard, result);
      if (forceWarehouseQueueClear) {
        // Force mode gets one extra prune pass after reconciliation below.
      }

      int remainingActiveLocalDeliveries = countShopsWithActiveLocalDeliveries(colony, shops);
      if (forceWarehouseQueueClear) {
        clearWarehouseQueues(colony, standard, result);
      }
      if (remainingActiveLocalDeliveries > 0 || hasActiveCreateShopRootRequests(standard)) {
        result.blockedActiveDeliveries += remainingActiveLocalDeliveries;
        result.runtimeTrackingSkipped += shops.size();
        result.drainResiduals += 1;
        continue;
      }

      for (BuildingCreateShop shop : shops) {
        try {
          result.runtimeTrackingCleared += Math.max(0, shop.clearRuntimeTrackingForDebug());
        } catch (Exception ex) {
          result.errors++;
          TheSettlerXCreate.LOGGER.warn(
              "[CreateShop] reset_live_state tracking clear failed shop={} error={}",
              shop.getLocation() == null
                  ? "<unknown>"
                  : shop.getLocation().getInDimensionLocation(),
              ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
        }
      }
    }
    return result;
  }

  // -------------------------------------------------------------------------
  // Private helpers
  // -------------------------------------------------------------------------

  private static void cancelActiveLocalDeliveries(
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
          if (isStaleRequestGraphException(ex)) {
            continue;
          }
          result.errors++;
          TheSettlerXCreate.LOGGER.warn(
              "[CreateShop] reset_live_state cancel active delivery failed token={} error={}",
              token,
              ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
        }
      }
    }
  }

  private static int countShopsWithActiveLocalDeliveries(
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

  private static void reconcileAssignmentsAndKickCouriers(
      IStandardRequestManager standard, ResetLiveStateResult result) {
    if (standard == null || result == null) {
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

    for (var assignmentEntry : java.util.List.copyOf(store.getAssignments().entrySet())) {
      java.util.Collection<IToken<?>> assigned = assignmentEntry.getValue();
      if (assigned == null || assigned.isEmpty()) {
        continue;
      }
      // Iterate a defensive snapshot, not the live per-resolver collection: standard.assignRequest
      // below can mutate that very collection (MineColonies' addRequestToResolver adds to it). A
      // raw Iterator would be unsafe here, since a mutation from inside assignRequest would
      // invalidate it out from under us.
      for (IToken<?> token : java.util.List.copyOf(assigned)) {
        if (token == null) {
          assigned.remove(token);
          result.assignmentPruned++;
          continue;
        }
        try {
          var request = requestHandler.getRequestOrNull(token);
          if (request == null) {
            assigned.remove(token);
            result.assignmentPruned++;
            continue;
          }
          if (CreateShopCommandSupport.isTerminalState(request.getState())) {
            assigned.remove(token);
            result.assignmentPruned++;
            continue;
          }
          if (!CreateShopCommandSupport.isCreateShopOwnedRequest(standard, request)) {
            continue;
          }
          if (request.getRequest()
                  instanceof
                  com.minecolonies.api.colony.requestsystem.requestable.deliveryman.Delivery
              && request.getState() == RequestState.CREATED) {
            try {
              // Seam-audit finding s3-2: a raw assignRequest() here re-ran resolver search
              // without first clearing the stale forward-map entry that got us into this loop
              // (this token is only visible here because it's already present in
              // store.getAssignments()) - risking a second, orphaned assignment-store entry for
              // the same token. reassignRequest() explicitly unassigns via the same
              // resolver-assignment data store before reassigning, closing that gap; it's the
              // same repair idiom BuildingCreateShop.repairOpenPickupRequest already uses.
              standard.reassignRequest(token, java.util.Collections.emptyList());
              result.deliveryAssignKicks++;
            } catch (Exception kickEx) {
              result.errors++;
              TheSettlerXCreate.LOGGER.warn(
                  "[CreateShop] reset_live_state assign kick failed token={} error={}",
                  token,
                  kickEx.getMessage() == null
                      ? kickEx.getClass().getSimpleName()
                      : kickEx.getMessage());
            }
          }
        } catch (Exception ex) {
          if (isStaleRequestGraphException(ex)) {
            assigned.remove(token);
            result.assignmentPruned++;
            continue;
          }
          result.errors++;
          TheSettlerXCreate.LOGGER.warn(
              "[CreateShop] reset_live_state reconcile assignment failed token={} error={}",
              token,
              ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
        }
      }
    }
  }

  private static void cancelCreateShopOwnedRequestsGraphAware(
      IStandardRequestManager standard, ResetLiveStateResult result) {
    if (standard == null || result == null) {
      return;
    }
    java.util.Set<IToken<?>> assignedTokens =
        CreateShopCommandSupport.collectAssignedRequestTokens(standard);
    if (assignedTokens.isEmpty()) {
      return;
    }

    java.util.Set<IToken<?>> visited = new java.util.LinkedHashSet<>();
    java.util.List<IToken<?>> roots = new java.util.ArrayList<>();
    for (IToken<?> token : assignedTokens) {
      if (token == null) {
        continue;
      }
      try {
        var request = standard.getRequestHandler().getRequestOrNull(token);
        if (request == null || !isCreateShopOwnedRootRequest(standard, request)) {
          continue;
        }
        roots.add(token);
      } catch (Exception ex) {
        if (isStaleRequestGraphException(ex)) {
          cleanupStaleToken(standard, token, result, "reset_live_state root scan");
        } else {
          result.errors++;
          TheSettlerXCreate.LOGGER.warn(
              "[CreateShop] reset_live_state root scan failed token={} error={}",
              token,
              ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
        }
      }
    }

    for (IToken<?> root : roots) {
      cancelRequestGraphPostOrder(standard, root, visited, result);
    }

    // Cancel orphaned assigned Create Shop requests not reachable from a root graph.
    for (IToken<?> token : assignedTokens) {
      if (token == null || visited.contains(token)) {
        continue;
      }
      try {
        var request = standard.getRequestHandler().getRequestOrNull(token);
        if (request == null
            || !CreateShopCommandSupport.isCreateShopOwnedRequest(standard, request)) {
          continue;
        }
        cancelSingleRequest(standard, request, result);
      } catch (Exception ex) {
        if (isStaleRequestGraphException(ex)) {
          cleanupStaleToken(standard, token, result, "reset_live_state orphan scan");
        } else {
          result.errors++;
          TheSettlerXCreate.LOGGER.warn(
              "[CreateShop] reset_live_state orphan scan failed token={} error={}",
              token,
              ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
        }
      }
    }
  }

  /**
   * Hard-reset pass: cancel every non-terminal assigned request graph, regardless of owner
   * resolver. Prevents stuck retrying roots from surviving world reloads.
   */
  private static void cancelAllAssignedRequestsGraphAware(
      IStandardRequestManager standard, ResetLiveStateResult result) {
    if (standard == null || result == null) {
      return;
    }
    java.util.Set<IToken<?>> assignedTokens =
        CreateShopCommandSupport.collectAssignedRequestTokens(standard);
    if (assignedTokens.isEmpty()) {
      return;
    }

    java.util.Set<IToken<?>> visited = new java.util.LinkedHashSet<>();
    java.util.List<IToken<?>> roots = new java.util.ArrayList<>();
    for (IToken<?> token : assignedTokens) {
      if (token == null) {
        continue;
      }
      try {
        var request = standard.getRequestHandler().getRequestOrNull(token);
        if (request == null || request.hasParent()) {
          continue;
        }
        roots.add(token);
      } catch (Exception ex) {
        if (isStaleRequestGraphException(ex)) {
          cleanupStaleToken(standard, token, result, "reset_live_state all root scan");
        } else {
          result.errors++;
          TheSettlerXCreate.LOGGER.warn(
              "[CreateShop] reset_live_state all root scan failed token={} error={}",
              token,
              ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
        }
      }
    }

    for (IToken<?> root : roots) {
      cancelRequestGraphPostOrder(standard, root, visited, result);
    }

    for (IToken<?> token : assignedTokens) {
      if (token == null || visited.contains(token)) {
        continue;
      }
      try {
        var request = standard.getRequestHandler().getRequestOrNull(token);
        if (request == null) {
          cleanupStaleToken(standard, token, result, "reset_live_state all orphan missing");
          continue;
        }
        cancelSingleRequest(standard, request, result);
      } catch (Exception ex) {
        if (isStaleRequestGraphException(ex)) {
          cleanupStaleToken(standard, token, result, "reset_live_state all orphan scan");
        } else {
          result.errors++;
          TheSettlerXCreate.LOGGER.warn(
              "[CreateShop] reset_live_state all orphan scan failed token={} error={}",
              token,
              ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
        }
      }
    }
  }

  private static void cancelRequestGraphPostOrder(
      IStandardRequestManager standard,
      IToken<?> token,
      java.util.Set<IToken<?>> visited,
      ResetLiveStateResult result) {
    if (standard == null || token == null || visited == null || result == null) {
      return;
    }
    if (!visited.add(token)) {
      return;
    }

    com.minecolonies.api.colony.requestsystem.request.IRequest<?> request;
    try {
      request = standard.getRequestHandler().getRequestOrNull(token);
    } catch (Exception ex) {
      if (isStaleRequestGraphException(ex)) {
        cleanupStaleToken(standard, token, result, "reset_live_state graph fetch");
      } else {
        result.errors++;
        TheSettlerXCreate.LOGGER.warn(
            "[CreateShop] reset_live_state graph fetch failed token={} error={}",
            token,
            ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
      }
      return;
    }

    if (request == null) {
      cleanupStaleToken(standard, token, result, "reset_live_state graph missing");
      return;
    }

    if (request.hasChildren()
        && request.getChildren() != null
        && !request.getChildren().isEmpty()) {
      for (IToken<?> child : java.util.List.copyOf(request.getChildren())) {
        cancelRequestGraphPostOrder(standard, child, visited, result);
      }
    }

    cancelSingleRequest(standard, request, result);
  }

  private static void cancelSingleRequest(
      IStandardRequestManager standard,
      com.minecolonies.api.colony.requestsystem.request.IRequest<?> request,
      ResetLiveStateResult result) {
    if (standard == null || request == null || result == null) {
      return;
    }
    try {
      if (CreateShopCommandSupport.isTerminalState(request.getState())) {
        if (request.getState() == RequestState.CANCELLED) {
          standard.getRequestHandler().cleanRequestData(request.getId());
          result.staleCleaned++;
        }
        return;
      }
      standard.updateRequestState(request.getId(), RequestState.CANCELLED);
      result.requestsCancelled++;
    } catch (Exception ex) {
      if (isStaleRequestGraphException(ex)) {
        cleanupStaleToken(standard, request.getId(), result, "reset_live_state graph cancel");
        return;
      }
      result.errors++;
      TheSettlerXCreate.LOGGER.warn(
          "[CreateShop] reset_live_state cancel failed token={} error={}",
          request.getId(),
          ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
    }
  }

  private static void cleanupStaleToken(
      IStandardRequestManager standard,
      IToken<?> token,
      ResetLiveStateResult result,
      String reason) {
    if (standard == null || token == null || result == null) {
      return;
    }
    try {
      standard.getRequestHandler().cleanRequestData(token);
      result.staleCleaned++;
      TheSettlerXCreate.LOGGER.info("[CreateShop] {} stale cleanup token={}", reason, token);
    } catch (Exception cleanEx) {
      result.errors++;
      TheSettlerXCreate.LOGGER.warn(
          "[CreateShop] {} stale cleanup failed token={} error={}",
          reason,
          token,
          cleanEx.getMessage() == null ? cleanEx.getClass().getSimpleName() : cleanEx.getMessage());
    }
  }

  private static void clearWarehouseQueues(
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
              if (!isStaleRequestGraphException(cancelEx)) {
                result.errors++;
                TheSettlerXCreate.LOGGER.warn(
                    "[CreateShop] reset_live_state queue active cancel failed token={} error={}",
                    queuedToken,
                    cancelEx.getMessage() == null
                        ? cancelEx.getClass().getSimpleName()
                        : cancelEx.getMessage());
              }
            }
            liveQueue.remove(queuedToken);
            result.queueEntriesCleared++;
            try {
              standard.getRequestHandler().cleanRequestData(queuedToken);
              result.staleCleaned++;
            } catch (Exception cleanEx) {
              if (!isStaleRequestGraphException(cleanEx)) {
                result.errors++;
                TheSettlerXCreate.LOGGER.warn(
                    "[CreateShop] reset_live_state queue active cleanup failed token={} error={}",
                    queuedToken,
                    cleanEx.getMessage() == null
                        ? cleanEx.getClass().getSimpleName()
                        : cleanEx.getMessage());
              }
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
          if (isStaleRequestGraphException(ex)) {
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
          result.errors++;
          TheSettlerXCreate.LOGGER.warn(
              "[CreateShop] reset_live_state queue cancel failed token={} error={}",
              queuedToken,
              ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
        }
      }
    }
  }

  private static boolean isCreateShopOwnedRootRequest(
      IStandardRequestManager standard,
      com.minecolonies.api.colony.requestsystem.request.IRequest<?> request) {
    if (!CreateShopCommandSupport.isCreateShopOwnedRequest(standard, request)) {
      return false;
    }
    return request != null && !request.hasParent();
  }

  private static boolean hasActiveCreateShopRootRequests(IStandardRequestManager standard) {
    if (standard == null) {
      return false;
    }
    java.util.Set<IToken<?>> tokens =
        CreateShopCommandSupport.collectAssignedRequestTokens(standard);
    for (IToken<?> token : tokens) {
      if (token == null) {
        continue;
      }
      try {
        var request = standard.getRequestHandler().getRequestOrNull(token);
        if (request == null || !isCreateShopOwnedRootRequest(standard, request)) {
          continue;
        }
        if (!CreateShopCommandSupport.isTerminalState(request.getState())) {
          return true;
        }
      } catch (Exception ignored) {
        return true;
      }
    }
    return false;
  }

  /**
   * Detects the "request graph went stale underneath us" failure mode (a request/resolver was
   * concurrently removed by MineColonies while we were mid-traversal) so callers can clean up and
   * move on instead of logging it as a real error.
   *
   * <p>This used to match on exact substrings of the exception message (e.g. {@code
   * "hasChildren()"}), but that text is JVM-generated helpful-NPE detail, not a MineColonies
   * contract - it can change with the JDK or with unrelated MineColonies refactors and silently
   * stop matching. Instead, match on the exception's type (the two known failure shapes are both
   * NPE/ISE from dereferencing a request that vanished mid-traversal) and on the exception having
   * actually originated inside MineColonies' own request-system code, which is what makes it "a
   * stale request graph" rather than an unrelated failure in our own code.
   */
  private static boolean isStaleRequestGraphException(Exception ex) {
    if (!(ex instanceof NullPointerException) && !(ex instanceof IllegalStateException)) {
      return false;
    }
    StackTraceElement[] trace = ex.getStackTrace();
    if (trace == null || trace.length == 0) {
      return false;
    }
    String originClass = trace[0].getClassName();
    return originClass != null && originClass.startsWith("com.minecolonies.");
  }

  // -------------------------------------------------------------------------
  // Result types
  // -------------------------------------------------------------------------

  static final class Result {
    int colonies;
    int shops;
    int providerUnregister;
    int requestsCancelled;
    int errors;
  }

  static final class ResetLiveStateResult {
    int colonies;
    int shops;
    int requestsCancelled;
    int staleCleaned;
    int runtimeTrackingCleared;
    int runtimeTrackingSkipped;
    int queueEntriesCleared;
    int queueRequestsCancelled;
    int blockedActiveDeliveries;
    int assignmentPruned;
    int deliveryAssignKicks;
    int deliveryRequestsCancelled;
    int drainRounds;
    int drainResiduals;
    int errors;
  }
}
