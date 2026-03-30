package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.requestable.deliveryman.Delivery;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.core.colony.buildings.modules.BuildingModules;
import com.minecolonies.core.colony.jobs.JobDeliveryman;
import com.minecolonies.core.colony.requestsystem.management.IStandardRequestManager;
import com.thesettler_x_create.TheSettlerXCreate;
import net.minecraft.world.item.ItemStack;
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
    resolver.markParentDeliveryActiveIfAbsent(
        parentToken, level == null ? 0L : level.getGameTime());
    clearStaleRecoveryArm(resolver, parentToken);
    if (childToken != null) {
      resolver.markChildActive(childToken, level == null ? 0L : level.getGameTime());
    }
  }

  void closeDeliveryWindow(
      CreateShopRequestResolver resolver, IToken<?> parentToken, IToken<?> childToken) {
    if (resolver == null) {
      return;
    }
    if (childToken != null) {
      resolver.clearChildActive(childToken);
    }
    if (parentToken != null) {
      resolver.clearParentDeliveryActive(parentToken);
      clearStaleRecoveryArm(resolver, parentToken);
      resolver.clearDeliveriesCreated(parentToken);
    }
  }

  void completeDeliveryWindow(
      CreateShopRequestResolver resolver, IToken<?> parentToken, IToken<?> childToken) {
    if (resolver == null) {
      return;
    }
    if (childToken != null) {
      resolver.clearChildActive(childToken);
    }
    if (parentToken != null) {
      resolver.clearParentDeliveryActive(parentToken);
      clearStaleRecoveryArm(resolver, parentToken);
    }
  }

  boolean armStaleRecoveryIfMissing(
      CreateShopRequestResolver resolver, IToken<?> parentToken, long nowTick) {
    if (resolver == null || parentToken == null) {
      return false;
    }
    return resolver.armStaleRecoveryIfMissing(parentToken, nowTick);
  }

  void clearStaleRecoveryArm(CreateShopRequestResolver resolver, IToken<?> parentToken) {
    if (resolver == null || parentToken == null) {
      return;
    }
    resolver.clearParentStaleRecoveryArm(parentToken);
  }

  Long markParentDeliveryActiveIfAbsent(
      CreateShopRequestResolver resolver, IToken<?> parentToken, long nowTick) {
    if (resolver == null || parentToken == null) {
      return null;
    }
    return resolver.markParentDeliveryActiveIfAbsent(parentToken, nowTick);
  }

  void clearParentDeliveryActive(CreateShopRequestResolver resolver, IToken<?> parentToken) {
    if (resolver == null || parentToken == null) {
      return;
    }
    resolver.clearParentDeliveryActive(parentToken);
  }

  void markChildActive(CreateShopRequestResolver resolver, IToken<?> childToken, long sinceTick) {
    if (resolver == null || childToken == null) {
      return;
    }
    resolver.markChildActive(childToken, sinceTick);
  }

  void clearChildActive(CreateShopRequestResolver resolver, IToken<?> childToken) {
    if (resolver == null || childToken == null) {
      return;
    }
    resolver.clearChildActive(childToken);
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
    resolver.clearDeliveriesCreated(token);
    resolver.clearParentDeliveryActive(token);
    resolver.clearParentChildCompletedSeen(token);
    resolver.clearParentStaleRecoveryArm(token);
    resolver.clearParentChildrenSnapshot(token);
    resolver.clearDeliveryChildLedgerForParent(token);
    resolver.clearChildActive(token);
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
    if (resolver == null || manager == null || token == null) {
      return;
    }
    resolver.clearTrackedChildrenForParent(manager, token);
  }

  void finalizeOrphanDeliveryChild(
      CreateShopRequestResolver resolver,
      IStandardRequestManager manager,
      IToken<?> childToken,
      String source) {
    if (resolver == null || manager == null || childToken == null) {
      return;
    }
    boolean parentDetached = false;
    try {
      IRequest<?> orphanRequest = manager.getRequestHandler().getRequestOrNull(childToken);
      if (orphanRequest != null && orphanRequest.hasParent()) {
        IToken<?> parentToken = orphanRequest.getParent();
        IRequest<?> parentRequest =
            parentToken == null ? null : manager.getRequestHandler().getRequestOrNull(parentToken);
        if (parentRequest != null) {
          parentRequest.removeChild(childToken);
        }
        orphanRequest.setParent(null);
        parentDetached = true;
      }
    } catch (Exception ignored) {
      // Best effort: orphan may already be detached in native graph.
    }
    try {
      manager.updateRequestState(
          childToken, com.minecolonies.api.colony.requestsystem.request.RequestState.FAILED);
    } catch (Exception ignored) {
      // Best effort: some native child state transitions are restricted.
    }
    int assignmentRemoved = 0;
    try {
      var store = manager.getRequestResolverRequestAssignmentDataStore();
      if (store != null && store.getAssignments() != null) {
        for (var assigned : store.getAssignments().values()) {
          if (assigned == null || assigned.isEmpty()) {
            continue;
          }
          while (assigned.remove(childToken)) {
            assignmentRemoved++;
          }
        }
      }
    } catch (Exception ignored) {
      // Best effort only.
    }
    int queueRemoved = 0;
    int courierTasksCleared = 0;
    DeliverySignature orphanSignature = null;
    try {
      IRequest<?> orphanRequest = manager.getRequestHandler().getRequestOrNull(childToken);
      orphanSignature = DeliverySignature.fromRequest(orphanRequest);
    } catch (Exception ignored) {
      orphanSignature = null;
    }
    try {
      var colony = manager.getColony();
      var buildingManager = colony == null ? null : colony.getServerBuildingManager();
      var buildings = buildingManager == null ? null : buildingManager.getBuildings();
      if (buildings != null && !buildings.isEmpty()) {
        for (var entry : buildings.entrySet()) {
          var building = entry.getValue();
          if (building == null) {
            continue;
          }
          var queue = building.getModule(BuildingModules.WAREHOUSE_REQUEST_QUEUE);
          if (queue == null
              || queue.getMutableRequestList() == null
              || queue.getMutableRequestList().isEmpty()) {
            // keep going; courier task cleanup still relevant.
          } else {
            while (queue.getMutableRequestList().remove(childToken)) {
              queueRemoved++;
            }
          }
          var couriers = building.getModule(BuildingModules.WAREHOUSE_COURIERS);
          if (couriers == null || couriers.getAssignedCitizen() == null) {
            continue;
          }
          for (var citizen : couriers.getAssignedCitizen()) {
            if (citizen == null || !(citizen.getJob() instanceof JobDeliveryman job)) {
              continue;
            }
            try {
              while (job.getTaskQueue() != null && job.getTaskQueue().remove(childToken)) {
                courierTasksCleared++;
              }
            } catch (Exception ignored) {
              // Best effort queue cleanup.
            }
            try {
              var current = job.getCurrentTask();
              if (current != null) {
                boolean tokenMatch = childToken.equals(current.getId());
                boolean signatureMatch =
                    orphanSignature != null && orphanSignature.matches(current);
                if (tokenMatch || signatureMatch) {
                  job.onTaskDeletion(current.getId());
                  job.finishRequest(false);
                  courierTasksCleared++;
                }
              }
            } catch (Exception ignored) {
              // Best effort task interruption.
            }
          }
        }
      }
    } catch (Exception ignored) {
      // Best effort only.
    }
    try {
      manager.getRequestHandler().cleanRequestData(childToken);
    } catch (Exception ignored) {
      // Best effort only.
    }
    clearChildActive(resolver, childToken);
    clearMissingChild(resolver, childToken);
    resolver.clearRootCauseTracking(childToken);
    if (resolver.isDebugLoggingEnabled()) {
      TheSettlerXCreate.LOGGER.info(
          "[CreateShop] orphan child finalized source={} child={} parentDetached={} assignmentsRemoved={} queueRemoved={} courierTasksCleared={}",
          source,
          childToken,
          parentDetached,
          assignmentRemoved,
          queueRemoved,
          courierTasksCleared);
    }
  }

  private record DeliverySignature(
      net.minecraft.core.BlockPos from, net.minecraft.core.BlockPos to, ItemStack stack) {
    static DeliverySignature fromRequest(IRequest<?> request) {
      if (request == null || !(request.getRequest() instanceof Delivery delivery)) {
        return null;
      }
      if (delivery.getStack() == null || delivery.getStack().isEmpty()) {
        return null;
      }
      return new DeliverySignature(
          delivery.getStart().getInDimensionLocation(),
          delivery.getTarget().getInDimensionLocation(),
          delivery.getStack().copy());
    }

    boolean matches(IRequest<?> request) {
      if (request == null || !(request.getRequest() instanceof Delivery delivery)) {
        return false;
      }
      if (!from.equals(delivery.getStart().getInDimensionLocation())
          || !to.equals(delivery.getTarget().getInDimensionLocation())) {
        return false;
      }
      ItemStack other = delivery.getStack();
      if (other == null || other.isEmpty() || stack == null || stack.isEmpty()) {
        return false;
      }
      return ItemStack.isSameItemSameComponents(stack, other)
          && other.getCount() == stack.getCount();
    }
  }
}
