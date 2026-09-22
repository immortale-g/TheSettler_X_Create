package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.requestable.deliveryman.Delivery;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.core.colony.requestsystem.management.IStandardRequestManager;
import com.minecolonies.core.colony.requestsystem.resolvers.DeliveryRequestResolver;
import com.minecolonies.core.colony.requestsystem.resolvers.core.AbstractWarehouseRequestResolver;
import com.thesettler_x_create.DebugLog;
import com.thesettler_x_create.TheSettlerXCreate;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.level.Level;

/**
 * Emits rate-limited root-cause snapshots for unresolved delivery-child assignment/drift issues.
 */
final class CreateShopDeliveryRootCauseSnapshotService {
  void logSnapshot(
      CreateShopRequestResolver resolver,
      IStandardRequestManager manager,
      Level level,
      IRequest<?> parent,
      IRequest<?> child,
      IToken<?> childToken,
      IToken<?> assignedResolverToken) {
    if (resolver == null
        || !DebugLog.enabled()
        || manager == null
        || level == null
        || parent == null
        || child == null
        || childToken == null) {
      return;
    }
    if (!(child.getRequest() instanceof Delivery delivery)) {
      return;
    }

    long now = level.getGameTime();
    Long last = resolver.getRootCauseLastLogTick(childToken);
    if (last != null && now - last < 100L) {
      return;
    }

    String assignedResolverClass = "<none>";
    boolean assignedResolverDelivery = false;
    if (assignedResolverToken != null) {
      try {
        Object assignedResolver = manager.getResolverHandler().getResolver(assignedResolverToken);
        assignedResolverClass = resolver.tryDescribeResolver(assignedResolver);
        // Against the class, not its name: WarehouseConcreteRequestResolver does not contain
        // "WarehouseRequestResolver" and used to fall through here as assignedResolverDelivery
        // =false while a warehouse resolver was in fact assigned.
        assignedResolverDelivery =
            assignedResolver instanceof DeliveryRequestResolver
                || assignedResolver instanceof AbstractWarehouseRequestResolver;
      } catch (Exception ignored) {
        assignedResolverClass = "<missing>";
      }
    }

    List<String> warehouseDebug = new ArrayList<>();
    var buildingManager =
        manager.getColony() == null ? null : manager.getColony().getServerBuildingManager();
    if (buildingManager != null && buildingManager.getBuildings() != null) {
      for (var entry : buildingManager.getBuildings().entrySet()) {
        Object building = entry.getValue();
        if (!CreateShopWarehouseFilter.isRelevantWarehouse(building)) {
          continue;
        }
        var warehouse = (com.minecolonies.api.colony.buildings.workerbuildings.IWareHouse) building;
        var queue =
            warehouse.getModule(
                com.minecolonies.core.colony.buildings.modules.BuildingModules
                    .WAREHOUSE_REQUEST_QUEUE);
        boolean queueContains =
            queue != null
                && queue.getMutableRequestList() != null
                && queue.getMutableRequestList().contains(childToken);
        var couriers =
            warehouse.getModule(
                com.minecolonies.core.colony.buildings.modules.BuildingModules.WAREHOUSE_COURIERS);
        int courierCount =
            couriers == null || couriers.getAssignedCitizen() == null
                ? 0
                : couriers.getAssignedCitizen().size();
        List<String> courierInfo = new ArrayList<>();
        if (couriers != null && couriers.getAssignedCitizen() != null) {
          for (var citizen : couriers.getAssignedCitizen()) {
            if (citizen == null) {
              continue;
            }
            String name = citizen.getName() == null ? "<unknown>" : citizen.getName();
            String job =
                citizen.getJob() == null ? "<none>" : citizen.getJob().getClass().getSimpleName();
            String currentTask = "<none>";
            String taskQueue = "<na>";
            if (citizen.getJob()
                instanceof com.minecolonies.core.colony.jobs.JobDeliveryman jobDeliveryman) {
              try {
                IRequest<?> task = CreateShopCourierTasks.peekCurrentTask(manager, jobDeliveryman);
                currentTask = task == null ? "<none>" : String.valueOf(task.getId());
              } catch (Exception ignored) {
                currentTask = "<error>";
              }
              try {
                var queueTokens = jobDeliveryman.getTaskQueue();
                taskQueue =
                    queueTokens == null
                        ? "<null>"
                        : "size="
                            + queueTokens.size()
                            + ",contains="
                            + queueTokens.contains(childToken);
              } catch (Exception ignored) {
                taskQueue = "<error>";
              }
            }
            courierInfo.add(
                name
                    + "{id="
                    + citizen.getId()
                    + ",uuid="
                    + citizen.getUUID()
                    + ",job="
                    + job
                    + ",deliveryman="
                    + (citizen.getJob() instanceof com.minecolonies.core.colony.jobs.JobDeliveryman)
                    + ",currentTask="
                    + currentTask
                    + ",taskQueue="
                    + taskQueue
                    + "}");
          }
        }
        String location = String.valueOf(warehouse.getLocation());
        warehouseDebug.add(
            "warehouse{loc="
                + location
                + ",queueContains="
                + queueContains
                + ",couriers="
                + courierCount
                + ",courierInfo="
                + courierInfo
                + "}");
      }
    }

    String snapshot =
        "parent="
            + parent.getId()
            + " child="
            + childToken
            + " childState="
            + child.getState()
            + " assignedResolver="
            + (assignedResolverToken == null ? "<none>" : assignedResolverToken)
            + " assignedResolverClass="
            + assignedResolverClass
            + " assignedResolverDelivery="
            + assignedResolverDelivery
            + " deliveryFrom="
            + (delivery.getStart() == null
                ? "<null>"
                : delivery.getStart().getInDimensionLocation())
            + " deliveryTo="
            + (delivery.getTarget() == null
                ? "<null>"
                : delivery.getTarget().getInDimensionLocation())
            + " warehouses="
            + warehouseDebug;

    String previous = resolver.putRootCauseSnapshot(childToken, snapshot);
    if (!snapshot.equals(previous)) {
      TheSettlerXCreate.LOGGER.info("[CreateShop] root-cause delivery snapshot {}", snapshot);
      resolver.markRootCauseLastLogTick(childToken, now);
    }
  }
}
