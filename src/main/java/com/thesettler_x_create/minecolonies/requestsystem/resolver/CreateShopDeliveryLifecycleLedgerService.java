package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.request.RequestState;
import com.minecolonies.api.colony.requestsystem.requestable.deliveryman.Delivery;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.core.colony.buildings.modules.BuildingModules;
import com.minecolonies.core.colony.jobs.JobDeliveryman;
import com.minecolonies.core.colony.requestsystem.management.IStandardRequestManager;
import com.thesettler_x_create.TheSettlerXCreate;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.items.IItemHandler;

/** Tracks one runtime lifecycle ledger per delivery-child token and emits diagnosis snapshots. */
final class CreateShopDeliveryLifecycleLedgerService {
  void observeChild(
      CreateShopRequestResolver resolver,
      IStandardRequestManager manager,
      Level level,
      IToken<?> parentToken,
      IToken<?> childToken,
      IRequest<?> child,
      IToken<?> assignedResolverToken,
      String source) {
    if (resolver == null
        || manager == null
        || level == null
        || parentToken == null
        || childToken == null
        || child == null) {
      return;
    }
    Map<IToken<?>, CreateShopDeliveryChildLedgerEntry> map = resolver.getDeliveryChildLedger();
    CreateShopDeliveryChildLedgerEntry entry =
        map.computeIfAbsent(childToken, CreateShopDeliveryChildLedgerEntry::new);
    long now = level.getGameTime();
    if (entry.getFirstSeenAtTick() < 0L) {
      entry.setFirstSeenAtTick(now);
    }
    entry.setParentToken(parentToken);
    RequestState state = child.getState();
    String stateLabel = state == null ? "<null>" : state.toString();
    entry.setLastSeenState(stateLabel);
    entry.setLastSeenAtTick(now);
    entry.setLastOwnerResolver(
        assignedResolverToken == null ? "<none>" : assignedResolverToken.toString());
    if (state == RequestState.CREATED && entry.getCreatedSeenAtTick() < 0L) {
      entry.setCreatedSeenAtTick(now);
    }
    if (state == RequestState.ASSIGNED && entry.getAssignedSeenAtTick() < 0L) {
      entry.setAssignedSeenAtTick(now);
    }
    if (state == RequestState.IN_PROGRESS && entry.getInProgressSeenAtTick() < 0L) {
      entry.setInProgressSeenAtTick(now);
    }
    ItemStack deliveryStack =
        child.getRequest() instanceof Delivery delivery
            ? delivery.getStack().copy()
            : ItemStack.EMPTY;
    BlockPos deliveryStart =
        child.getRequest() instanceof Delivery delivery
            ? delivery.getStart().getInDimensionLocation()
            : null;
    BlockPos deliveryTarget =
        child.getRequest() instanceof Delivery delivery
            ? delivery.getTarget().getInDimensionLocation()
            : null;
    WarehouseSnapshot snapshot =
        inspectWarehouseSnapshot(manager, childToken, deliveryStack, deliveryStart, deliveryTarget);
    entry.setLastQueueContains(snapshot.queueContains());
    entry.setLastCourierCount(snapshot.courierCount());
    entry.setLastCourierTaskMatchCount(snapshot.courierTaskMatchCount());
    entry.setLastCourierCarryMatchCount(snapshot.courierCarryMatchCount());
    entry.setLastCourierAtSourceMatchCount(snapshot.courierAtSourceMatchCount());
    entry.setLastCourierAtTargetMatchCount(snapshot.courierAtTargetMatchCount());
    if (state == RequestState.IN_PROGRESS && snapshot.courierTaskMatchCount() > 0) {
      int patched = ensureOngoingDeliveryMarker(manager, childToken);
      if (patched > 0) {
        entry.setDiagnosisCode("ONGOING_MARKER_PATCHED");
        entry.setDiagnosisDetail(
            "courier-task matched, ongoing delivery marker ensured count=" + patched);
      }
    }
    if (state == RequestState.IN_PROGRESS
        && !snapshot.queueContains()
        && snapshot.courierTaskMatchCount() > 0
        && snapshot.courierAtTargetMatchCount() > 0) {
      int forced = forceFinishMatchingCourierTasks(manager, childToken);
      if (forced > 0) {
        entry.setDiagnosisCode("FORCE_FINISH_AT_TARGET");
        entry.setDiagnosisDetail("force-finished stuck courier task count=" + forced);
      }
    }
    if (snapshot.courierTaskMatchCount() > 0 && entry.getPickupConfirmedAtTick() < 0L) {
      entry.setPickupConfirmedAtTick(now);
      entry.setDiagnosisCode("COURIER_PICKUP_CONFIRMED");
      entry.setDiagnosisDetail(
          "pickup confirmed by courier task match (carryMatches="
              + snapshot.courierCarryMatchCount()
              + ")");
    }

    if (CreateShopRequestResolver.isTerminalRequestState(state)) {
      if (entry.getTerminalSeenAtTick() < 0L) {
        entry.setTerminalSeenAtTick(now);
      }
      entry.setTerminalSource(source == null ? "poll" : source);
      entry.setDiagnosisCode("TERMINAL_SEEN");
      entry.setDiagnosisDetail("child reached terminal state via " + entry.getTerminalSource());
      logLedger(resolver, entry, now, "terminal");
      return;
    }

    // Diagnose stalled non-terminal children.
    if (state == RequestState.IN_PROGRESS && entry.getInProgressSeenAtTick() > 0L) {
      long inProgressAge = now - entry.getInProgressSeenAtTick();
      if (!snapshot.queueContains() && inProgressAge >= 100L) {
        entry.setDiagnosisCode("MC_QUEUE_DEQUEUED_WITHOUT_TERMINAL");
        entry.setDiagnosisDetail(
            "inProgressAge="
                + inProgressAge
                + " queueContains=false couriers="
                + snapshot.courierCount());
      } else if (inProgressAge >= Math.max(100L, resolver.getInflightTimeoutTicksSafe())) {
        entry.setDiagnosisCode("MC_NO_TERMINAL_CALLBACK");
        entry.setDiagnosisDetail(
            "inProgressAge="
                + inProgressAge
                + " queueContains="
                + snapshot.queueContains()
                + " couriers="
                + snapshot.courierCount());
      }
    }
    logLedger(resolver, entry, now, "poll");
  }

  void observeMissingChild(
      CreateShopRequestResolver resolver,
      Level level,
      IToken<?> parentToken,
      IToken<?> childToken,
      String source,
      String detail) {
    if (resolver == null || level == null || parentToken == null || childToken == null) {
      return;
    }
    Map<IToken<?>, CreateShopDeliveryChildLedgerEntry> map = resolver.getDeliveryChildLedger();
    CreateShopDeliveryChildLedgerEntry entry =
        map.computeIfAbsent(childToken, CreateShopDeliveryChildLedgerEntry::new);
    long now = level.getGameTime();
    if (entry.getFirstSeenAtTick() < 0L) {
      entry.setFirstSeenAtTick(now);
    }
    entry.setParentToken(parentToken);
    entry.setLastSeenAtTick(now);
    entry.setDiagnosisCode("MC_HANDLER_LOST_TOKEN");
    entry.setDiagnosisDetail(
        (source == null ? "missing" : source) + " " + (detail == null ? "" : detail));
    logLedger(resolver, entry, now, "missing");
  }

  void observeCallbackTerminal(
      CreateShopRequestResolver resolver,
      Level level,
      IToken<?> parentToken,
      IToken<?> childToken,
      String callbackType) {
    if (resolver == null || level == null || parentToken == null || childToken == null) {
      return;
    }
    Map<IToken<?>, CreateShopDeliveryChildLedgerEntry> map = resolver.getDeliveryChildLedger();
    CreateShopDeliveryChildLedgerEntry entry =
        map.computeIfAbsent(childToken, CreateShopDeliveryChildLedgerEntry::new);
    long now = level.getGameTime();
    if (entry.getFirstSeenAtTick() < 0L) {
      entry.setFirstSeenAtTick(now);
    }
    entry.setParentToken(parentToken);
    entry.setLastSeenAtTick(now);
    entry.setTerminalSeenAtTick(now);
    entry.setTerminalSource(callbackType == null ? "callback" : callbackType);
    entry.setDiagnosisCode("TERMINAL_CALLBACK");
    entry.setDiagnosisDetail("terminal callback received");
    logLedger(resolver, entry, now, "callback");
  }

  private WarehouseSnapshot inspectWarehouseSnapshot(
      IStandardRequestManager manager,
      IToken<?> childToken,
      ItemStack deliveryStack,
      BlockPos deliveryStart,
      BlockPos deliveryTarget) {
    boolean queueContains = false;
    int courierCount = 0;
    int courierTaskMatches = 0;
    int courierCarryMatches = 0;
    int courierAtSourceMatches = 0;
    int courierAtTargetMatches = 0;
    try {
      var buildingManager =
          manager.getColony() == null ? null : manager.getColony().getServerBuildingManager();
      if (buildingManager != null && buildingManager.getBuildings() != null) {
        for (var entry : buildingManager.getBuildings().entrySet()) {
          Object building = entry.getValue();
          if (!(building
                  instanceof
                  com.minecolonies.api.colony.buildings.workerbuildings.IWareHouse
                  warehouse)
              || building
                  instanceof com.thesettler_x_create.minecolonies.building.BuildingCreateShop) {
            continue;
          }
          var queue = warehouse.getModule(BuildingModules.WAREHOUSE_REQUEST_QUEUE);
          if (queue != null && queue.getMutableRequestList() != null) {
            queueContains |= queue.getMutableRequestList().contains(childToken);
          }
          var couriers = warehouse.getModule(BuildingModules.WAREHOUSE_COURIERS);
          if (couriers != null && couriers.getAssignedCitizen() != null) {
            courierCount += couriers.getAssignedCitizen().size();
            for (var citizen : couriers.getAssignedCitizen()) {
              if (citizen == null || !(citizen.getJob() instanceof JobDeliveryman job)) {
                continue;
              }
              IRequest<?> currentTask;
              try {
                currentTask = job.getCurrentTask();
              } catch (Exception ignored) {
                currentTask = null;
              }
              if (currentTask == null || !childToken.equals(currentTask.getId())) {
                continue;
              }
              courierTaskMatches++;
              if (hasCourierCarryingStack(citizen, deliveryStack)) {
                courierCarryMatches++;
              }
              try {
                var entityOpt = citizen.getEntity();
                if (entityOpt != null && entityOpt.isPresent()) {
                  BlockPos courierPos = entityOpt.get().blockPosition();
                  if (courierPos != null) {
                    if (deliveryStart != null && courierPos.distManhattan(deliveryStart) <= 2) {
                      courierAtSourceMatches++;
                    }
                    if (deliveryTarget != null && courierPos.distManhattan(deliveryTarget) <= 2) {
                      courierAtTargetMatches++;
                    }
                  }
                }
              } catch (Exception ignored) {
                // Best-effort diagnostics only.
              }
            }
          }
        }
      }
    } catch (Exception ignored) {
      // Diagnostic best effort only.
    }
    return new WarehouseSnapshot(
        queueContains,
        courierCount,
        courierTaskMatches,
        courierCarryMatches,
        courierAtSourceMatches,
        courierAtTargetMatches);
  }

  private int forceFinishMatchingCourierTasks(
      IStandardRequestManager manager, IToken<?> childToken) {
    if (manager == null || childToken == null) {
      return 0;
    }
    int forced = 0;
    try {
      var buildingManager =
          manager.getColony() == null ? null : manager.getColony().getServerBuildingManager();
      if (buildingManager == null || buildingManager.getBuildings() == null) {
        return 0;
      }
      for (var entry : buildingManager.getBuildings().entrySet()) {
        Object building = entry.getValue();
        if (!(building
                instanceof
                com.minecolonies.api.colony.buildings.workerbuildings.IWareHouse
                warehouse)
            || building
                instanceof com.thesettler_x_create.minecolonies.building.BuildingCreateShop) {
          continue;
        }
        var couriers = warehouse.getModule(BuildingModules.WAREHOUSE_COURIERS);
        if (couriers == null || couriers.getAssignedCitizen() == null) {
          continue;
        }
        for (var citizen : couriers.getAssignedCitizen()) {
          if (citizen == null || !(citizen.getJob() instanceof JobDeliveryman job)) {
            continue;
          }
          IRequest<?> currentTask;
          try {
            currentTask = job.getCurrentTask();
          } catch (Exception ignored) {
            currentTask = null;
          }
          if (currentTask == null || !childToken.equals(currentTask.getId())) {
            continue;
          }
          try {
            job.finishRequest(true);
            forced++;
          } catch (Exception ignored) {
            // Best effort only.
          }
        }
      }
    } catch (Exception ignored) {
      return forced;
    }
    return forced;
  }

  private int ensureOngoingDeliveryMarker(IStandardRequestManager manager, IToken<?> childToken) {
    if (manager == null || childToken == null) {
      return 0;
    }
    int patched = 0;
    try {
      var buildingManager =
          manager.getColony() == null ? null : manager.getColony().getServerBuildingManager();
      if (buildingManager == null || buildingManager.getBuildings() == null) {
        return 0;
      }
      for (var entry : buildingManager.getBuildings().entrySet()) {
        Object building = entry.getValue();
        if (!(building
                instanceof
                com.minecolonies.api.colony.buildings.workerbuildings.IWareHouse
                warehouse)
            || building
                instanceof com.thesettler_x_create.minecolonies.building.BuildingCreateShop) {
          continue;
        }
        var couriers = warehouse.getModule(BuildingModules.WAREHOUSE_COURIERS);
        if (couriers == null || couriers.getAssignedCitizen() == null) {
          continue;
        }
        for (var citizen : couriers.getAssignedCitizen()) {
          if (citizen == null || !(citizen.getJob() instanceof JobDeliveryman job)) {
            continue;
          }
          IRequest<?> currentTask;
          try {
            currentTask = job.getCurrentTask();
          } catch (Exception ignored) {
            currentTask = null;
          }
          if (currentTask == null || !childToken.equals(currentTask.getId())) {
            continue;
          }
          job.addConcurrentDelivery(childToken);
          patched++;
        }
      }
    } catch (Exception ignored) {
      return patched;
    }
    return patched;
  }

  private boolean hasCourierCarryingStack(
      com.minecolonies.api.colony.ICitizenData citizen, ItemStack deliveryStack) {
    if (citizen == null || deliveryStack == null || deliveryStack.isEmpty()) {
      return false;
    }
    try {
      var entityOpt = citizen.getEntity();
      if (entityOpt == null || entityOpt.isEmpty()) {
        return false;
      }
      Object entity = entityOpt.get();
      var method = entity.getClass().getMethod("getInventoryCitizen");
      Object invObj = method.invoke(entity);
      if (!(invObj instanceof IItemHandler inv)) {
        return false;
      }
      int found = 0;
      for (int i = 0; i < inv.getSlots(); i++) {
        ItemStack slot = inv.getStackInSlot(i);
        if (slot.isEmpty() || !ItemStack.isSameItemSameComponents(slot, deliveryStack)) {
          continue;
        }
        found += slot.getCount();
        if (found >= deliveryStack.getCount()) {
          return true;
        }
      }
      return false;
    } catch (Exception ignored) {
      return false;
    }
  }

  private void logLedger(
      CreateShopRequestResolver resolver,
      CreateShopDeliveryChildLedgerEntry entry,
      long now,
      String source) {
    if (!resolver.isDebugLoggingEnabled()) {
      return;
    }
    Long last = resolver.getDeliveryLedgerLastLogTick(entry.getChildToken());
    if (last != null && now - last < 40L) {
      return;
    }
    resolver.markDeliveryLedgerLastLogTick(entry.getChildToken(), now);
    TheSettlerXCreate.LOGGER.info(
        "[CreateShop] delivery-child-ledger source={} child={} parent={} state={} owner={} queueContains={} couriers={} courierTaskMatches={} courierCarryMatches={} courierAtSourceMatches={} courierAtTargetMatches={} pickupConfirmedAt={} firstSeen={} createdAt={} assignedAt={} inProgressAt={} terminalAt={} terminalSource={} diagnosis={} detail={}",
        source,
        entry.getChildToken(),
        entry.getParentToken(),
        entry.getLastSeenState(),
        entry.getLastOwnerResolver(),
        entry.isLastQueueContains(),
        entry.getLastCourierCount(),
        entry.getLastCourierTaskMatchCount(),
        entry.getLastCourierCarryMatchCount(),
        entry.getLastCourierAtSourceMatchCount(),
        entry.getLastCourierAtTargetMatchCount(),
        entry.getPickupConfirmedAtTick(),
        entry.getFirstSeenAtTick(),
        entry.getCreatedSeenAtTick(),
        entry.getAssignedSeenAtTick(),
        entry.getInProgressSeenAtTick(),
        entry.getTerminalSeenAtTick(),
        entry.getTerminalSource(),
        entry.getDiagnosisCode(),
        entry.getDiagnosisDetail());
  }

  record WarehouseSnapshot(
      boolean queueContains,
      int courierCount,
      int courierTaskMatchCount,
      int courierCarryMatchCount,
      int courierAtSourceMatchCount,
      int courierAtTargetMatchCount) {}
}
