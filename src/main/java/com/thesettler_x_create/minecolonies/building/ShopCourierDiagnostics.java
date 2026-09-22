package com.thesettler_x_create.minecolonies.building;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.jobs.IJob;
import com.minecolonies.api.util.constant.TypeConstants;
import com.minecolonies.core.colony.buildings.AbstractBuilding;
import com.minecolonies.core.colony.jobs.JobDeliveryman;
import com.thesettler_x_create.Config;
import com.thesettler_x_create.DebugLog;
import com.thesettler_x_create.TheSettlerXCreate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import net.minecraft.world.level.Level;

/**
 * Debug utilities for courier assignments and entity state.
 *
 * <p>Read-only: this class only logs. It used to re-spawn and re-register citizens whose entity
 * looked missing, which made MineColonies warn "Missing entity upon adding data to that entity!"
 * and changed colony state only when debug logging was on.
 */
final class ShopCourierDiagnostics {
  private final BuildingCreateShop shop;
  private long lastCourierDebugTime;
  private long lastCourierEntityDebugTime;
  private String lastCourierDebugDump;
  private String lastCourierEntityDump;
  private String lastAssignedCitizensDump;
  private String lastWarehouseCompareDump;
  private final Map<String, String> lastAssignedCitizenInfo = new HashMap<>();
  private final Map<Integer, Boolean> lastAccessResult = new HashMap<>();

  ShopCourierDiagnostics(BuildingCreateShop shop) {
    this.shop = shop;
    this.lastCourierDebugTime = 0L;
    this.lastCourierEntityDebugTime = 0L;
    this.lastCourierDebugDump = "";
    this.lastCourierEntityDump = "";
    this.lastAssignedCitizensDump = "";
    this.lastWarehouseCompareDump = "";
  }

  void debugCourierAssignments(IColony colony) {
    if (!DebugLog.enabled() || colony == null) {
      return;
    }
    Level level = colony.getWorld();
    long now = level == null ? 0L : level.getGameTime();
    if (now != 0L && now - lastCourierDebugTime < Config.COURIER_DEBUG_COOLDOWN.getAsLong()) {
      return;
    }
    lastCourierDebugTime = now;
    var manager = colony.getRequestManager();
    if (!(manager
        instanceof
        com.minecolonies.core.colony.requestsystem.management.IStandardRequestManager
        standardManager)) {
      return;
    }
    java.util.List<String> debugLines = new java.util.ArrayList<>();
    var assignmentStore = standardManager.getRequestResolverRequestAssignmentDataStore();
    var assignments = assignmentStore == null ? null : assignmentStore.getAssignments();
    var typeStore = standardManager.getRequestableTypeRequestResolverAssignmentDataStore();
    var typeAssignments = typeStore == null ? null : typeStore.getAssignments();
    var requestableResolvers =
        typeAssignments == null ? null : typeAssignments.get(TypeConstants.REQUESTABLE);
    if (requestableResolvers != null) {
      int loggedResolvers = 0;
      for (var resolverToken : requestableResolvers) {
        if (loggedResolvers >= 5) {
          break;
        }
        try {
          var resolver = standardManager.getResolverHandler().getResolver(resolverToken);
          if (!(resolver
              instanceof
              com.minecolonies.core.colony.requestsystem.resolvers.DeliveryRequestResolver
              deliveryResolver)) {
            continue;
          }
          var location = deliveryResolver.getLocation();
          String info = location == null ? "<unknown>" : location.toString();
          var assignedRequests = assignments == null ? null : assignments.get(resolverToken);
          int assignedCount = assignedRequests == null ? 0 : assignedRequests.size();
          debugLines.add(
              "deliveryResolver token="
                  + resolverToken
                  + " assignedCount="
                  + assignedCount
                  + " location="
                  + info);
          if (assignedRequests != null) {
            int logged = 0;
            for (var token : assignedRequests) {
              if (logged >= 5) {
                break;
              }
              try {
                var request = standardManager.getRequestHandler().getRequest(token);
                String type =
                    request == null || request.getRequest() == null
                        ? "<null>"
                        : request.getRequest().getClass().getName();
                String state = request == null ? "<null>" : String.valueOf(request.getState());
                debugLines.add("deliveryRequest " + token + " type=" + type + " state=" + state);
                logged++;
              } catch (IllegalArgumentException ignored) {
                // Missing request.
              }
            }
          }
          loggedResolvers++;
        } catch (IllegalArgumentException ignored) {
          // Missing resolver.
        }
      }
    }

    var assignedCitizens = shop.getAllAssignedCitizen();
    debugLines.add("assignedCitizens=" + assignedCitizens.size());
    int loggedCitizens = 0;
    for (var citizen : assignedCitizens) {
      if (loggedCitizens >= 3) {
        break;
      }
      String name = citizen.getName() == null ? "<unknown>" : citizen.getName();
      var job = citizen.getJob();
      String jobName = job == null ? "<none>" : job.getClass().getName();
      String jobState = describeJobState(citizen, job);
      String citizenPos = describeCitizenPosition(citizen);
      debugLines.add(
          "citizen=" + name + " job=" + jobName + " state=" + jobState + " pos=" + citizenPos);
      if ("<entity-null>".equals(citizenPos) && shouldLogCourierEntity(level)) {
        logCitizenEntityDiagnostics(citizen, level);
      }
      loggedCitizens++;
    }

    String dump = String.join(" | ", debugLines);
    if (!dump.equals(lastCourierDebugDump)) {
      lastCourierDebugDump = dump;
      TheSettlerXCreate.LOGGER.info("[CreateShop] courier debug: {}", dump);
    }

    logAssignedCitizensChanges();
    logCourierWorkBuildings(colony);
  }

  void logAccessCheck(ICitizenData citizen, boolean result) {
    int key = citizen == null ? -1 : citizen.getId();
    Boolean last = lastAccessResult.get(key);
    if (last != null && last == result) {
      return;
    }
    lastAccessResult.put(key, result);
    TheSettlerXCreate.LOGGER.info(
        "[CreateShop] courier access check: {} -> {}", describeCitizen(citizen), result);
  }

  private void logAssignedCitizensChanges() {
    var citizens = shop.getAllAssignedCitizen();
    java.util.List<String> entries = new java.util.ArrayList<>();
    java.util.Map<String, String> currentInfo = new java.util.HashMap<>();
    for (var citizen : citizens) {
      entries.add(describeCitizenAssignmentDetail(citizen));
      String key = describeCitizenKey(citizen);
      currentInfo.put(key, describeCitizenAssignmentDetail(citizen));
      if (DebugLog.enabled()) {
        logCitizenUuidLookup(citizen);
      }
    }
    String dump = String.join(" | ", entries);
    if (!dump.equals(lastAssignedCitizensDump)) {
      lastAssignedCitizensDump = dump;
      TheSettlerXCreate.LOGGER.info(
          "[CreateShop] courier assign change: {}", dump.isEmpty() ? "<none>" : dump);
      logAssignmentDelta("courier hire", lastAssignedCitizenInfo, currentInfo);
      lastAssignedCitizenInfo.clear();
      lastAssignedCitizenInfo.putAll(currentInfo);
    }
  }

  private void logCourierWorkBuildings(IColony colony) {
    if (colony == null) {
      return;
    }
    var manager = colony.getServerBuildingManager();
    if (manager == null) {
      return;
    }
    var warehouses = manager.getWareHouses();
    if (warehouses == null || warehouses.isEmpty()) {
      return;
    }
    java.util.Set<String> warehousePos = new HashSet<>();
    for (var wh : warehouses) {
      if (wh instanceof AbstractBuilding building) {
        warehousePos.add(String.valueOf(building.getLocation().getInDimensionLocation()));
      }
    }
    java.util.List<String> entries = new java.util.ArrayList<>();
    for (var citizen : colony.getCitizenManager().getCitizens()) {
      if (!(citizen.getJob() instanceof JobDeliveryman)) {
        continue;
      }
      var workBuilding = citizen.getWorkBuilding();
      String workPos = workBuilding == null ? "<null>" : String.valueOf(workBuilding);
      boolean isWarehouse = warehousePos.contains(workPos);
      entries.add(
          describeCitizen(citizen) + " workBuilding=" + workPos + " isWarehouse=" + isWarehouse);
    }
    String dump = String.join(" | ", entries);
    if (!dump.equals(lastWarehouseCompareDump)) {
      lastWarehouseCompareDump = dump;
      TheSettlerXCreate.LOGGER.info(
          "[CreateShop] courier workbuilding compare: {}", dump.isEmpty() ? "<none>" : dump);
    }
  }

  private void logCitizenUuidLookup(ICitizenData citizen) {
    if (citizen == null || shop.getColony() == null) {
      return;
    }
    Level level = shop.getColony().getWorld();
    if (!(level instanceof net.minecraft.server.level.ServerLevel serverLevel)) {
      return;
    }
    java.util.UUID uuid = citizen.getUUID();
    if (uuid == null) {
      return;
    }
    var entity = serverLevel.getEntity(uuid);
    String lookup =
        entity == null
            ? "<missing>"
            : entity.getClass().getName()
                + " pos="
                + entity.blockPosition()
                + " dim="
                + serverLevel.dimension().location();
    String dump = "uuid=" + uuid + " lookup=" + lookup;
    if (!dump.equals(lastCourierEntityDump)) {
      lastCourierEntityDump = dump;
      TheSettlerXCreate.LOGGER.info("[CreateShop] courier assign uuid lookup: {}", dump);
    }
  }

  private String describeCitizen(ICitizenData citizen) {
    if (citizen == null) {
      return "<null-citizen>";
    }
    String name = citizen.getName() == null ? "<unknown>" : citizen.getName();
    java.util.UUID uuid = citizen.getUUID();
    String jobName = citizen.getJob() == null ? "<none>" : citizen.getJob().getClass().getName();
    return "name="
        + name
        + " id="
        + citizen.getId()
        + " uuid="
        + (uuid == null ? "<null>" : uuid)
        + " job="
        + jobName;
  }

  private String describeCitizenKey(ICitizenData citizen) {
    if (citizen == null) {
      return "<null-citizen>";
    }
    java.util.UUID uuid = citizen.getUUID();
    return citizen.getId() + ":" + (uuid == null ? "<null>" : uuid);
  }

  private String describeCitizenAssignmentDetail(ICitizenData citizen) {
    if (citizen == null) {
      return "<null-citizen>";
    }
    String base = describeCitizen(citizen);
    String workBuilding = describeCitizenWorkBuilding(citizen);
    return base + " workBuilding=" + workBuilding;
  }

  private String describeCitizenWorkBuilding(ICitizenData citizen) {
    var workBuilding = citizen.getWorkBuilding();
    if (workBuilding == null) {
      return "<none>";
    }
    var location = workBuilding.getLocation();
    if (location != null) {
      return workBuilding.getClass().getName() + "@" + location;
    }
    return workBuilding.getClass().getName();
  }

  private void logAssignmentDelta(
      String label, Map<String, String> previousInfo, Map<String, String> currentInfo) {
    if (previousInfo == null) {
      return;
    }
    java.util.List<String> added = new java.util.ArrayList<>();
    java.util.List<String> removed = new java.util.ArrayList<>();
    java.util.List<String> changed = new java.util.ArrayList<>();
    for (var entry : currentInfo.entrySet()) {
      if (!previousInfo.containsKey(entry.getKey())) {
        added.add(entry.getValue());
      } else {
        String prev = previousInfo.get(entry.getKey());
        if (prev != null && !prev.equals(entry.getValue())) {
          changed.add(prev + " -> " + entry.getValue());
        }
      }
    }
    for (var entry : previousInfo.entrySet()) {
      if (!currentInfo.containsKey(entry.getKey())) {
        removed.add(entry.getValue());
      }
    }
    if (added.isEmpty() && removed.isEmpty() && changed.isEmpty()) {
      return;
    }
    String addedDump = added.isEmpty() ? "<none>" : String.join(" | ", added);
    String removedDump = removed.isEmpty() ? "<none>" : String.join(" | ", removed);
    String changedDump = changed.isEmpty() ? "<none>" : String.join(" | ", changed);
    TheSettlerXCreate.LOGGER.info(
        "[CreateShop] {} delta: added={} removed={} changed={}",
        label,
        addedDump,
        removedDump,
        changedDump);
  }

  /**
   * Describes what a citizen's job is doing. Deliberately does not ask the job: MineColonies has no
   * {@code getState}, {@code isWorking}, {@code getCurrentRequest}, {@code getCurrentRequestToken}
   * or {@code getRequestToken} on {@link IJob}, so the reflective versions of these lines only ever
   * printed {@code <unknown>}. {@code isWorking()} lives on the citizen, and a courier's queue is
   * read through {@link JobDeliveryman#getTaskQueue()} - never {@code getCurrentTask()}, which
   * assigns warehouse work as a side effect.
   */
  private String describeJobState(ICitizenData citizen, IJob<?> job) {
    if (job == null) {
      return "<none>";
    }
    StringBuilder state = new StringBuilder("isWorking=").append(citizen.isWorking());
    if (job instanceof JobDeliveryman courier) {
      var queue = courier.getTaskQueue();
      if (queue == null) {
        state.append(" taskQueue=<null>");
      } else {
        state.append(" taskQueue=").append(queue.size());
        if (!queue.isEmpty()) {
          state.append(" firstTask=").append(queue.get(0));
        }
      }
    }
    return state.toString();
  }

  private boolean shouldLogCourierEntity(Level level) {
    long now = level == null ? 0L : level.getGameTime();
    if (now == 0L
        || now - lastCourierEntityDebugTime >= Config.COURIER_ENTITY_DEBUG_COOLDOWN.getAsLong()) {
      lastCourierEntityDebugTime = now;
      return true;
    }
    return false;
  }

  private void logCitizenEntityDiagnostics(ICitizenData citizen, Level level) {
    if (citizen == null || level == null) {
      return;
    }
    java.util.UUID uuid = citizen.getUUID();
    String uuidLookup = "<n/a>";
    if (uuid != null && level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
      var entity = serverLevel.getEntity(uuid);
      if (entity != null) {
        uuidLookup =
            entity.getClass().getName()
                + " pos="
                + entity.blockPosition()
                + " dim="
                + serverLevel.dimension().location();
      } else {
        uuidLookup = "<missing>";
      }
    }
    String dump =
        "id="
            + citizen.getId()
            + " uuid="
            + (uuid == null ? "<null>" : uuid)
            + " uuidLookup="
            + uuidLookup;
    if (!dump.equals(lastCourierEntityDump)) {
      lastCourierEntityDump = dump;
      TheSettlerXCreate.LOGGER.info("[CreateShop] courier debug: citizen entity missing {}", dump);
    }
  }

  private String describeCitizenPosition(ICitizenData citizen) {
    if (citizen == null) {
      return "<unknown>";
    }
    java.util.Optional<com.minecolonies.api.entity.citizen.AbstractEntityCitizen> entityOpt =
        citizen.getEntity();
    if (entityOpt.isPresent()) {
      var mcEntity = entityOpt.get();
      var pos = mcEntity.blockPosition();
      var dim = mcEntity.level().dimension();
      return "pos=" + pos + " dim=" + dim.location();
    }
    return "<entity-null>";
  }
}
