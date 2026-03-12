package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.requestable.deliveryman.Delivery;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.core.colony.requestsystem.management.IStandardRequestManager;
import com.thesettler_x_create.TheSettlerXCreate;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.level.Level;

/** Emits rate-limited root-cause snapshots for unresolved delivery-child assignment/drift issues. */
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
        || !resolver.isDebugLoggingEnabledForOps()
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
    Long last = resolver.getDeliveryRootCauseLastLogTickForOps().get(childToken);
    if (last != null && now - last < 100L) {
      return;
    }

    String assignedResolverClass = "<none>";
    boolean assignedResolverDelivery = false;
    if (assignedResolverToken != null) {
      try {
        Object assignedResolver = manager.getResolverHandler().getResolver(assignedResolverToken);
        assignedResolverClass = resolver.tryDescribeResolver(assignedResolver);
        assignedResolverDelivery =
            assignedResolverClass.contains("DeliveryRequestResolver")
                || assignedResolverClass.contains("WarehouseRequestResolver");
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
        if (!(building
                instanceof
                com.minecolonies.api.colony.buildings.workerbuildings.IWareHouse warehouse)
            || building
                instanceof com.thesettler_x_create.minecolonies.building.BuildingCreateShop) {
          continue;
        }
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
            Object id;
            Object uuid;
            try {
              id = citizen.getClass().getMethod("getId").invoke(citizen);
            } catch (Exception ignored) {
              id = "<na>";
            }
            try {
              uuid = citizen.getClass().getMethod("getUUID").invoke(citizen);
            } catch (Exception ignored) {
              uuid = "<na>";
            }
            courierInfo.add(
                name
                    + "{id="
                    + id
                    + ",uuid="
                    + uuid
                    + ",job="
                    + job
                    + ",deliveryman="
                    + (citizen.getJob() instanceof com.minecolonies.core.colony.jobs.JobDeliveryman)
                    + "}");
          }
        }
        String location = "<unknown>";
        try {
          Object locObj = warehouse.getClass().getMethod("getLocation").invoke(warehouse);
          location = String.valueOf(locObj);
        } catch (Exception ignored) {
          // Best-effort debug only.
        }
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
            + (delivery.getStart() == null ? "<null>" : delivery.getStart().getInDimensionLocation())
            + " deliveryTo="
            + (delivery.getTarget() == null
                ? "<null>"
                : delivery.getTarget().getInDimensionLocation())
            + " warehouses="
            + warehouseDebug;

    String previous = resolver.getDeliveryRootCauseSnapshotsForOps().put(childToken, snapshot);
    if (!snapshot.equals(previous)) {
      TheSettlerXCreate.LOGGER.info("[CreateShop] root-cause delivery snapshot {}", snapshot);
      resolver.getDeliveryRootCauseLastLogTickForOps().put(childToken, now);
    }
  }
}
