package com.thesettler_x_create.minecolonies.command;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.requestsystem.request.RequestState;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.core.colony.requestsystem.management.IStandardRequestManager;
import com.thesettler_x_create.TheSettlerXCreate;
import com.thesettler_x_create.minecolonies.building.BuildingCreateShop;
import com.thesettler_x_create.minecolonies.requestsystem.resolver.CreateShopRequestResolver;

/**
 * Handles reset and uninstall commands for the Create Shop building.
 *
 * <p>Extracted from {@link CreateShopMaintenanceCommands} to separate destructive maintenance
 * operations from diagnostic and test-harness commands.
 */
final class CreateShopResetCommands {
  private CreateShopResetCommands() {}

  // -------------------------------------------------------------------------
  // Package-visible entry points (called from CreateShopMaintenanceCommands)
  // -------------------------------------------------------------------------

  static Result prepareUninstall() {
    Result result = new Result();
    for (var colony : IColonyManager.getInstance().getAllColonies()) {
      result.colonies++;
      if (!(colony.getRequestManager() instanceof IStandardRequestManager standard)) {
        continue;
      }
      var buildingManager = colony.getServerBuildingManager();
      if (buildingManager != null && buildingManager.getBuildings() != null) {
        for (var entry : buildingManager.getBuildings().entrySet()) {
          var building = entry.getValue();
          if (!(building instanceof BuildingCreateShop shop)) {
            continue;
          }
          result.shops++;
          try {
            colony.getRequestManager().onProviderRemovedFromColony(shop);
            result.providerUnregister++;
          } catch (Exception ex) {
            result.errors++;
            TheSettlerXCreate.LOGGER.warn(
                "[CreateShop] prepare_uninstall provider unregister failed shop={} error={}",
                shop.getLocation() == null
                    ? "<unknown>"
                    : shop.getLocation().getInDimensionLocation(),
                ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
          }
        }
      }

      var assignments = standard.getRequestResolverRequestAssignmentDataStore().getAssignments();
      if (assignments == null || assignments.isEmpty()) {
        continue;
      }
      java.util.Set<IToken<?>> requestTokens = new java.util.LinkedHashSet<>();
      for (var tokens : assignments.values()) {
        if (tokens != null) {
          requestTokens.addAll(tokens);
        }
      }
      for (var requestToken : requestTokens) {
        try {
          var request = standard.getRequestHandler().getRequest(requestToken);
          if (request == null) {
            continue;
          }
          var owner = standard.getResolverHandler().getResolverForRequest(request);
          if (!(owner instanceof CreateShopRequestResolver)) {
            continue;
          }
          standard.updateRequestState(request.getId(), RequestState.CANCELLED);
          result.requestsCancelled++;
        } catch (Exception ex) {
          result.errors++;
          TheSettlerXCreate.LOGGER.warn(
              "[CreateShop] prepare_uninstall cancel failed token={} error={}",
              requestToken,
              ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
        }
      }
    }
    return result;
  }

  static ResetLiveStateResult resetLiveState(boolean forceWarehouseQueueClear) {
    ResetLiveStateResult result = new ResetLiveStateResult();
    for (IColony colony : IColonyManager.getInstance().getAllColonies()) {
      result.colonies++;
      if (!(colony.getRequestManager() instanceof IStandardRequestManager standard)) {
        continue;
      }

      java.util.Set<BuildingCreateShop> shops = collectCreateShops(colony);
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
    java.util.Set<BuildingCreateShop> shops = collectCreateShops(colony);
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
          if (isTerminalState(request.getState()) || !isCreateShopOwnedRequest(standard, request)) {
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

    for (var assignmentEntry : store.getAssignments().entrySet()) {
      java.util.Collection<IToken<?>> assigned = assignmentEntry.getValue();
      if (assigned == null || assigned.isEmpty()) {
        continue;
      }
      java.util.Iterator<IToken<?>> iterator = assigned.iterator();
      while (iterator.hasNext()) {
        IToken<?> token = iterator.next();
        if (token == null) {
          iterator.remove();
          result.assignmentPruned++;
          continue;
        }
        try {
          var request = requestHandler.getRequestOrNull(token);
          if (request == null) {
            iterator.remove();
            result.assignmentPruned++;
            continue;
          }
          if (isTerminalState(request.getState())) {
            iterator.remove();
            result.assignmentPruned++;
            continue;
          }
          if (!isCreateShopOwnedRequest(standard, request)) {
            continue;
          }
          if (request.getRequest()
                  instanceof
                  com.minecolonies.api.colony.requestsystem.requestable.deliveryman.Delivery
              && request.getState() == RequestState.CREATED) {
            try {
              standard.assignRequest(token);
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
            iterator.remove();
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
    java.util.Set<IToken<?>> assignedTokens = collectAssignedRequestTokens(standard);
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
        if (request == null || !isCreateShopOwnedRequest(standard, request)) {
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
    java.util.Set<IToken<?>> assignedTokens = collectAssignedRequestTokens(standard);
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
      if (isTerminalState(request.getState())) {
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
      java.util.Iterator<IToken<?>> iterator = queue.getMutableRequestList().iterator();
      while (iterator.hasNext()) {
        IToken<?> queuedToken = iterator.next();
        if (queuedToken == null) {
          iterator.remove();
          result.queueEntriesCleared++;
          continue;
        }
        try {
          var queuedRequest = standard.getRequestHandler().getRequestOrNull(queuedToken);
          if (queuedRequest == null) {
            iterator.remove();
            result.queueEntriesCleared++;
            result.staleCleaned++;
            continue;
          }
          if (!isTerminalState(queuedRequest.getState())) {
            if (!isCreateShopOwnedRequest(standard, queuedRequest)) {
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
            iterator.remove();
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
          iterator.remove();
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
            iterator.remove();
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

  static java.util.Set<BuildingCreateShop> collectCreateShops(IColony colony) {
    java.util.Set<BuildingCreateShop> shops = new java.util.LinkedHashSet<>();
    if (colony == null) {
      return shops;
    }
    var buildingManager = colony.getServerBuildingManager();
    if (buildingManager == null || buildingManager.getBuildings() == null) {
      return shops;
    }
    for (var entry : buildingManager.getBuildings().entrySet()) {
      var building = entry.getValue();
      if (building instanceof BuildingCreateShop shop) {
        shops.add(shop);
      }
    }
    return shops;
  }

  static java.util.Set<IToken<?>> collectAssignedRequestTokens(IStandardRequestManager standard) {
    java.util.Set<IToken<?>> tokens = new java.util.LinkedHashSet<>();
    if (standard == null) {
      return tokens;
    }
    var assignments = standard.getRequestResolverRequestAssignmentDataStore().getAssignments();
    if (assignments == null || assignments.isEmpty()) {
      return tokens;
    }
    for (var assigned : assignments.values()) {
      if (assigned != null) {
        tokens.addAll(assigned);
      }
    }
    return tokens;
  }

  static boolean isCreateShopOwnedRequest(
      IStandardRequestManager standard,
      com.minecolonies.api.colony.requestsystem.request.IRequest<?> request) {
    if (standard == null || request == null) {
      return false;
    }
    try {
      var owner = standard.getResolverHandler().getResolverForRequest(request);
      if (owner instanceof CreateShopRequestResolver) {
        return true;
      }
      if (!request.hasParent()) {
        return false;
      }
      var parent = standard.getRequestHandler().getRequest(request.getParent());
      if (parent == null) {
        return false;
      }
      var parentOwner = standard.getResolverHandler().getResolverForRequest(parent);
      return parentOwner instanceof CreateShopRequestResolver;
    } catch (Exception ignored) {
      return false;
    }
  }

  private static boolean isCreateShopOwnedRootRequest(
      IStandardRequestManager standard,
      com.minecolonies.api.colony.requestsystem.request.IRequest<?> request) {
    if (!isCreateShopOwnedRequest(standard, request)) {
      return false;
    }
    return request != null && !request.hasParent();
  }

  private static boolean hasActiveCreateShopRootRequests(IStandardRequestManager standard) {
    if (standard == null) {
      return false;
    }
    java.util.Set<IToken<?>> tokens = collectAssignedRequestTokens(standard);
    for (IToken<?> token : tokens) {
      if (token == null) {
        continue;
      }
      try {
        var request = standard.getRequestHandler().getRequestOrNull(token);
        if (request == null || !isCreateShopOwnedRootRequest(standard, request)) {
          continue;
        }
        if (!isTerminalState(request.getState())) {
          return true;
        }
      } catch (Exception ignored) {
        return true;
      }
    }
    return false;
  }

  static boolean isTerminalState(RequestState state) {
    return state == RequestState.CANCELLED
        || state == RequestState.COMPLETED
        || state == RequestState.FAILED
        || state == RequestState.RECEIVED
        || state == RequestState.RESOLVED;
  }

  private static boolean isStaleRequestGraphException(Exception ex) {
    if (ex == null) {
      return false;
    }
    String message = ex.getMessage();
    if (message == null || message.isEmpty()) {
      return false;
    }
    String normalized = message.toLowerCase(java.util.Locale.ROOT);
    boolean staleChildren =
        normalized.contains("haschildren()")
            && normalized.contains("request")
            && normalized.contains("null");
    boolean assignmentDrift = normalized.contains("intvalue()");
    return staleChildren || assignmentDrift;
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
