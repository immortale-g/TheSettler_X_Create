package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.core.colony.buildings.modules.BuildingModules;
import com.minecolonies.core.colony.jobs.JobDeliveryman;
import com.minecolonies.core.colony.requestsystem.management.IStandardRequestManager;
import com.thesettler_x_create.DebugLog;
import com.thesettler_x_create.TheSettlerXCreate;
import net.minecraft.world.level.Level;

/** Centralizes request pending/cooldown state mutations to avoid split write paths. */
final class CreateShopRequestStateMutatorService {
  void markOrderedWithPending(
      CreateShopRequestResolver resolver, Level level, IToken<?> requestToken, int pendingCount) {
    if (resolver == null || requestToken == null) {
      return;
    }
    if (level != null) {
      resolver.getCooldown().markRequestOrdered(level, requestToken);
    }
    resolver.getPendingTracker().setPendingCount(requestToken, Math.max(0, pendingCount));
  }

  void markOrderedWithPendingAtLeastOne(
      CreateShopRequestResolver resolver, Level level, IToken<?> requestToken, int pendingCount) {
    markOrderedWithPending(resolver, level, requestToken, Math.max(1, pendingCount));
  }

  void clearOrderedAndPending(CreateShopRequestResolver resolver, IToken<?> requestToken) {
    if (resolver == null || requestToken == null) {
      return;
    }
    resolver.getCooldown().clearRequestCooldown(requestToken);
    resolver.getPendingTracker().remove(requestToken);
  }

  /**
   * Opens the delivery window: marks the parent as having at least 1 pending unit. Stale-child
   * clock tracking has been removed — MineColonies owns the delivery lifecycle after this point.
   */
  void openDeliveryWindow(
      CreateShopRequestResolver resolver,
      Level level,
      IToken<?> parentToken,
      IToken<?> childToken,
      int pendingCount) {
    if (resolver == null || parentToken == null) {
      return;
    }
    markOrderedWithPendingAtLeastOne(resolver, level, parentToken, Math.max(1, pendingCount));
  }

  void clearMissingChild(CreateShopRequestResolver resolver, IToken<?> childToken) {
    if (resolver == null || childToken == null) {
      return;
    }
    resolver.clearMissingChildSince(childToken);
  }

  void setParentChildrenSnapshot(
      CreateShopRequestResolver resolver,
      IToken<?> parentToken,
      int childCount,
      String childrenState) {
    if (resolver == null || parentToken == null) {
      return;
    }
    resolver.setParentChildrenSnapshot(parentToken, childCount, childrenState);
  }

  void clearParentChildrenSnapshot(CreateShopRequestResolver resolver, IToken<?> parentToken) {
    if (resolver == null || parentToken == null) {
      return;
    }
    resolver.clearParentChildrenSnapshot(parentToken);
  }

  void markParentChildDropLog(
      CreateShopRequestResolver resolver, IToken<?> parentToken, long nowTick) {
    if (resolver == null || parentToken == null) {
      return;
    }
    resolver.markParentChildDropLastLogTick(parentToken, nowTick);
  }

  void clearPendingTokenState(
      CreateShopRequestResolver resolver, IToken<?> token, boolean clearFlowState) {
    if (resolver == null || token == null) {
      return;
    }
    clearOrderedAndPending(resolver, token);
    resolver.clearParentChildrenSnapshot(token);
    resolver.clearDeliveryChildLedgerForParent(token);
    resolver.clearMissingChildSince(token);
    resolver.clearRootCauseTracking(token);
    resolver.clearRetryingReassignAttempt(token);
    if (clearFlowState) {
      resolver.getFlowStateMachine().remove(token);
    }
  }

  void clearPendingTokenState(
      CreateShopRequestResolver resolver,
      IStandardRequestManager manager,
      IToken<?> token,
      boolean clearFlowState) {
    clearPendingTokenState(resolver, token, clearFlowState);
  }

  /**
   * Whether MineColonies still holds the delivery child as unfinished. Such a child is
   * MineColonies' to finish or cancel, and the outcome reaches us through the requester callbacks,
   * so recovery paths must leave it alone.
   */
  boolean isUnfinishedDeliveryChild(IStandardRequestManager manager, IToken<?> childToken) {
    if (manager == null || childToken == null) {
      return false;
    }
    try {
      IRequest<?> child = manager.getRequestHandler().getRequestOrNull(childToken);
      return child != null && !CreateShopRequestResolver.isTerminalRequestState(child.getState());
    } catch (Exception ignored) {
      return false;
    }
  }

  /**
   * Drops our own tracking for a delivery child that MineColonies no longer holds.
   *
   * <p>This used to fail the child and then clean the warehouse queue, the courier task queue and
   * the request data by hand. MineColonies does all of that itself when a request is cancelled or
   * failed (DeliverymenRequestResolver#onAssignedRequestCancelled calls onTaskDeletion and removes
   * the token from the warehouse queue), and removing request data without that path is exactly
   * what leaves a courier stuck on a task token that no longer resolves. Nothing here touches
   * MineColonies state any more; a dead courier token is reported, not repaired.
   */
  void forgetVanishedDeliveryChild(
      CreateShopRequestResolver resolver,
      IStandardRequestManager manager,
      IToken<?> childToken,
      String source) {
    if (resolver == null || childToken == null) {
      return;
    }
    clearMissingChild(resolver, childToken);
    resolver.clearRootCauseTracking(childToken);
    int deadCourierTokens = reportDeadCourierTaskTokens(manager, childToken, source);
    DebugLog.info(
        "[CreateShop] vanished child forgotten source={} child={} deadCourierTokens={}",
        source,
        childToken,
        deadCourierTokens);
  }

  private int reportDeadCourierTaskTokens(
      IStandardRequestManager manager, IToken<?> childToken, String source) {
    if (manager == null || isUnfinishedDeliveryChild(manager, childToken)) {
      return 0;
    }
    int found = 0;
    try {
      var colony = manager.getColony();
      var buildingManager = colony == null ? null : colony.getServerBuildingManager();
      var buildings = buildingManager == null ? null : buildingManager.getBuildings();
      if (buildings == null) {
        return 0;
      }
      for (var building : buildings.values()) {
        var couriers =
            building == null ? null : building.getModule(BuildingModules.WAREHOUSE_COURIERS);
        if (couriers == null || couriers.getAssignedCitizen() == null) {
          continue;
        }
        for (var citizen : couriers.getAssignedCitizen()) {
          if (citizen == null || !(citizen.getJob() instanceof JobDeliveryman job)) {
            continue;
          }
          // getTaskQueue() is a read-only copy.
          if (job.getTaskQueue().contains(childToken)) {
            found++;
            TheSettlerXCreate.LOGGER.warn(
                "[CreateShop] MC_COURIER_DEAD_TASK_TOKEN courier={} child={} source={}: the"
                    + " courier still queues a delivery MineColonies no longer knows",
                citizen.getName(),
                childToken,
                source);
          }
        }
      }
    } catch (Exception ignored) {
      // Diagnostics only.
    }
    return found;
  }
}
