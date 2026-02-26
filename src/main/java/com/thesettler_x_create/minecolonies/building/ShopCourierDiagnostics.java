package com.thesettler_x_create.minecolonies.building;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.util.constant.TypeConstants;
import com.minecolonies.core.colony.buildings.AbstractBuilding;
import com.thesettler_x_create.Config;
import com.thesettler_x_create.TheSettlerXCreate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.world.level.Level;

/** Debug utilities for courier assignments and entity state. */
final class ShopCourierDiagnostics {
  private static final Set<String> REFLECTION_WARNED =
      java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());

  private final BuildingCreateShop shop;
  private long lastCourierDebugTime;
  private long lastCourierEntityDebugTime;
  private String lastCourierDebugDump;
  private String lastCourierEntityDump;
  private String lastAssignedCitizensDump;
  private String lastEntityRepairDump;
  private String lastWarehouseCompareDump;
  private final Map<String, String> lastAssignedCitizenInfo = new HashMap<>();
  private final Map<Integer, Boolean> lastAccessResult = new HashMap<>();
  private final Map<String, Long> lastEntityRepairAttemptTime = new HashMap<>();

  ShopCourierDiagnostics(BuildingCreateShop shop) {
    this.shop = shop;
    this.lastCourierDebugTime = 0L;
    this.lastCourierEntityDebugTime = 0L;
    this.lastCourierDebugDump = "";
    this.lastCourierEntityDump = "";
    this.lastAssignedCitizensDump = "";
    this.lastEntityRepairDump = "";
    this.lastWarehouseCompareDump = "";
  }

  void debugCourierAssignments(IColony colony) {
    if (!BuildingCreateShop.isDebugRequests() || colony == null) {
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
              com.minecolonies.core.colony.requestsystem.resolvers.DeliveryRequestResolver)) {
            continue;
          }
          String info = "<unknown>";
          try {
            var getLocation = resolver.getClass().getMethod("getLocation");
            Object location = getLocation.invoke(resolver);
            if (location != null) {
              info = location.toString();
            }
          } catch (Exception ignored) {
            // Ignore.
          }
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
      String jobState = "<unknown>";
      String citizenPos = describeCitizenPosition(citizen);
      if (job != null) {
        try {
          var method = job.getClass().getMethod("getState");
          Object state = method.invoke(job);
          jobState = state == null ? "<null>" : state.toString();
        } catch (Exception ignored) {
          // Fallback below.
        }
        if ("<unknown>".equals(jobState)) {
          try {
            var method = job.getClass().getMethod("isWorking");
            Object state = method.invoke(job);
            jobState = state == null ? "<null>" : "isWorking=" + state;
          } catch (Exception ignored) {
            // Ignore.
          }
        }
        jobState = appendJobDetail(job, jobState, "getCurrentRequest");
        jobState = appendJobDetail(job, jobState, "getCurrentRequestToken");
        jobState = appendJobDetail(job, jobState, "getRequestToken");
        jobState = appendJobDetail(job, jobState, "getCurrentTask");
      }
      debugLines.add(
          "citizen=" + name + " job=" + jobName + " state=" + jobState + " pos=" + citizenPos);
      if (job != null) {
        Object currentTask = tryInvoke(job, "getCurrentTask");
        if (currentTask != null) {
          debugLines.add(
              "task class="
                  + currentTask.getClass().getName()
                  + " detail="
                  + describeTask(currentTask));
        }
      }
      if ("<entity-null>".equals(citizenPos)) {
        if (shouldLogCourierEntity(level)) {
          logCitizenEntityDiagnostics(citizen, level);
        }
        String key = describeCitizenKey(citizen);
        if (shouldAttemptEntityRepair(key, level)) {
          attemptCitizenEntityRepair(citizen, level);
        }
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
    int key = citizen == null ? -1 : safeCitizenId(citizen);
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
      if (BuildingCreateShop.isDebugRequests()) {
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
      Level level = shop.getColony() == null ? null : shop.getColony().getWorld();
      for (var citizen : citizens) {
        if (citizen == null || safeCitizenEntityId(citizen) >= 0) {
          continue;
        }
        if (getCitizenEntity(citizen, level) != null) {
          attemptCitizenEntityRepair(citizen, level);
        }
      }
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
      String job = citizen.getJob() == null ? "<none>" : citizen.getJob().getClass().getName();
      if (!job.contains("Deliveryman")) {
        continue;
      }
      Object workBuilding = tryInvoke(citizen, "getWorkBuilding");
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
    Object uuidValue = tryInvoke(citizen, "getUUID");
    if (!(uuidValue instanceof java.util.UUID uuid)) {
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
    Object idValue = tryInvoke(citizen, "getId");
    Object entityIdValue = tryInvoke(citizen, "getEntityId");
    Object uuidValue = tryInvoke(citizen, "getUUID");
    String jobName = citizen.getJob() == null ? "<none>" : citizen.getJob().getClass().getName();
    return "name="
        + name
        + " id="
        + (idValue == null ? "<null>" : idValue)
        + " entityId="
        + (entityIdValue == null ? "<null>" : entityIdValue)
        + " uuid="
        + (uuidValue == null ? "<null>" : uuidValue)
        + " job="
        + jobName;
  }

  private String describeCitizenKey(ICitizenData citizen) {
    if (citizen == null) {
      return "<null-citizen>";
    }
    Object idValue = tryInvoke(citizen, "getId");
    Object uuidValue = tryInvoke(citizen, "getUUID");
    String id = idValue == null ? "<null>" : String.valueOf(idValue);
    String uuid = uuidValue == null ? "<null>" : String.valueOf(uuidValue);
    return id + ":" + uuid;
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
    Object workBuilding = tryInvoke(citizen, "getWorkBuilding");
    if (workBuilding == null) {
      return "<none>";
    }
    Object location = tryInvoke(workBuilding, "getLocation");
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

  private int safeCitizenId(ICitizenData citizen) {
    Object idValue = tryInvoke(citizen, "getId");
    if (idValue instanceof Number number) {
      return number.intValue();
    }
    return -1;
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

  private boolean shouldAttemptEntityRepair(String key, Level level) {
    if (key == null || level == null) {
      return false;
    }
    long now = level.getGameTime();
    long last = lastEntityRepairAttemptTime.getOrDefault(key, 0L);
    if (now == 0L || now - last >= Config.COURIER_ENTITY_DEBUG_COOLDOWN.getAsLong()) {
      lastEntityRepairAttemptTime.put(key, now);
      return true;
    }
    return false;
  }

  private void logCitizenEntityDiagnostics(ICitizenData citizen, Level level) {
    if (citizen == null || level == null) {
      return;
    }
    Object idValue = tryInvoke(citizen, "getId");
    Object entityIdValue = tryInvoke(citizen, "getEntityId");
    Object uuidValue = tryInvoke(citizen, "getUUID");
    int entityId = -1;
    if (entityIdValue instanceof Number number) {
      entityId = number.intValue();
    }
    String entityLookup = "<unknown>";
    if (entityId >= 0 && level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
      var entity = serverLevel.getEntity(entityId);
      entityLookup = entity == null ? "<missing>" : entity.getClass().getName();
    }
    String uuidInfo = uuidValue == null ? "<null>" : uuidValue.toString();
    String uuidLookup = "<n/a>";
    boolean hasUuidEntity = false;
    if (uuidValue instanceof java.util.UUID uuid
        && level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
      var entity = serverLevel.getEntity(uuid);
      if (entity != null) {
        uuidLookup =
            entity.getClass().getName()
                + " pos="
                + entity.blockPosition()
                + " dim="
                + serverLevel.dimension().location();
        hasUuidEntity = true;
      } else {
        uuidLookup = "<missing>";
      }
    }
    String dump =
        "id="
            + (idValue == null ? "<null>" : idValue)
            + " entityId="
            + (entityId >= 0 ? entityId : "<null>")
            + " uuid="
            + uuidInfo
            + " idLookup="
            + entityLookup
            + " uuidLookup="
            + uuidLookup;
    if (!dump.equals(lastCourierEntityDump)) {
      lastCourierEntityDump = dump;
      TheSettlerXCreate.LOGGER.info("[CreateShop] courier debug: citizen entity missing {}", dump);
    }
    if (hasUuidEntity && entityId < 0) {
      attemptCitizenEntityRepair(citizen, level);
    }
  }

  private void attemptCitizenEntityRepair(ICitizenData citizen, Level level) {
    if (citizen == null || level == null) {
      return;
    }
    boolean invoked = false;
    String result = "<unknown>";
    try {
      var method = citizen.getClass().getMethod("updateEntityIfNecessary", Level.class);
      method.invoke(citizen, level);
      invoked = true;
      result = "ok";
    } catch (NoSuchMethodException ignored) {
      try {
        var method = citizen.getClass().getMethod("updateEntityIfNecessary");
        method.invoke(citizen);
        invoked = true;
        result = "ok";
      } catch (Exception ex) {
        result = ex.getMessage() == null ? "<error>" : ex.getMessage();
      }
    } catch (Exception ex) {
      result = ex.getMessage() == null ? "<error>" : ex.getMessage();
    }
    boolean forceInvoked = false;
    String forceResult = "<skipped>";
    boolean spawnInvoked = false;
    String spawnResult = "<skipped>";
    boolean registerInvoked = false;
    String registerResult = "<skipped>";
    boolean hasEntity = getCitizenEntity(citizen, level) != null;
    if (safeCitizenEntityId(citizen) < 0 && hasEntity) {
      forceResult = attemptForceEntityId(citizen, level);
      forceInvoked = !"<skipped>".equals(forceResult);
    }
    if (invoked && safeCitizenEntityId(citizen) < 0 && !hasEntity) {
      spawnResult = attemptSpawnOrCreateCitizen(citizen, level);
      spawnInvoked = !"<skipped>".equals(spawnResult);
    }
    if (safeCitizenEntityId(citizen) < 0 && !hasEntity) {
      registerResult = attemptRegisterCivilian(citizen, level);
      registerInvoked = !"<skipped>".equals(registerResult);
    }
    if (safeCitizenEntityId(citizen) < 0 && !hasEntity) {
      forceResult = attemptForceEntityId(citizen, level);
      forceInvoked = !"<skipped>".equals(forceResult);
    }
    String dump =
        "id="
            + safeCitizenId(citizen)
            + " updateInvoked="
            + invoked
            + " updateResult="
            + result
            + " spawnInvoked="
            + spawnInvoked
            + " spawnResult="
            + spawnResult
            + " registerInvoked="
            + registerInvoked
            + " registerResult="
            + registerResult
            + " forceInvoked="
            + forceInvoked
            + " forceResult="
            + forceResult;
    if (!dump.equals(lastEntityRepairDump)) {
      lastEntityRepairDump = dump;
      TheSettlerXCreate.LOGGER.info("[CreateShop] courier entity repair: {}", dump);
    }
  }

  private String attemptSpawnOrCreateCitizen(ICitizenData citizen, Level level) {
    if (citizen == null || level == null || shop.getColony() == null) {
      return "<skipped>";
    }
    try {
      var manager = shop.getColony().getCitizenManager();
      if (manager == null) {
        return "<no-manager>";
      }
      var method =
          manager
              .getClass()
              .getMethod(
                  "spawnOrCreateCitizen",
                  com.minecolonies.api.colony.ICitizenData.class,
                  Level.class);
      method.invoke(manager, citizen, level);
      return "ok";
    } catch (NoSuchMethodException ex) {
      return "<no-method>";
    } catch (Exception ex) {
      return ex.getMessage() == null ? "<error>" : ex.getMessage();
    }
  }

  private String attemptRegisterCivilian(ICitizenData citizen, Level level) {
    if (citizen == null || level == null || shop.getColony() == null) {
      return "<skipped>";
    }
    Object entity = getCitizenEntity(citizen, level);
    if (entity == null) {
      return "<no-entity>";
    }
    try {
      var manager = shop.getColony().getCitizenManager();
      if (manager == null) {
        return "<no-manager>";
      }
      java.lang.reflect.Method target = null;
      for (var method : manager.getClass().getMethods()) {
        if (!"registerCivilian".equals(method.getName()) || method.getParameterCount() != 1) {
          continue;
        }
        target = method;
        break;
      }
      if (target == null) {
        return "<no-method>";
      }
      target.invoke(manager, entity);
      return "ok";
    } catch (Exception ex) {
      return ex.getMessage() == null ? "<error>" : ex.getMessage();
    }
  }

  private Object getCitizenEntity(ICitizenData citizen, Level level) {
    java.util.Optional<com.minecolonies.api.entity.citizen.AbstractEntityCitizen> opt =
        citizen.getEntity();
    if (opt.isPresent()) {
      return opt.get();
    }
    Object uuidValue = tryInvoke(citizen, "getUUID");
    if (uuidValue instanceof java.util.UUID uuid
        && level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
      return serverLevel.getEntity(uuid);
    }
    return null;
  }

  private String attemptForceEntityId(ICitizenData citizen, Level level) {
    if (citizen == null || level == null) {
      return "<skipped>";
    }
    Object entityOpt = tryInvoke(citizen, "getEntity");
    boolean hasEntityOpt = entityOpt instanceof java.util.Optional<?>;
    boolean entityOptPresent = hasEntityOpt && ((java.util.Optional<?>) entityOpt).isPresent();
    Object entity = getCitizenEntity(citizen, level);
    if (!(entity instanceof net.minecraft.world.entity.Entity mcEntity)) {
      Object uuidValue = tryInvoke(citizen, "getUUID");
      String uuidInfo = uuidValue instanceof java.util.UUID uuid ? uuid.toString() : "<null>";
      String lookup = "<n/a>";
      if (uuidValue instanceof java.util.UUID uuid
          && level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
        var found = serverLevel.getEntity(uuid);
        lookup =
            found == null
                ? "<missing>"
                : found.getClass().getName()
                    + " pos="
                    + found.blockPosition()
                    + " dim="
                    + serverLevel.dimension().location();
      }
      String optInfo = hasEntityOpt ? (entityOptPresent ? "present" : "empty") : "n/a";
      return "<no-entity opt=" + optInfo + " uuid=" + uuidInfo + " uuidLookup=" + lookup + ">";
    }
    Object citizenUuidValue = tryInvoke(citizen, "getUUID");
    if (citizenUuidValue instanceof java.util.UUID citizenUuid) {
      java.util.UUID entityUuid = mcEntity.getUUID();
      if (!citizenUuid.equals(entityUuid)) {
        return "<uuid-mismatch citizen=" + citizenUuid + " entity=" + entityUuid + ">";
      }
    }
    int id = mcEntity.getId();
    try {
      java.lang.reflect.Method target = null;
      for (var method : citizen.getClass().getMethods()) {
        if (!"setEntity".equals(method.getName()) || method.getParameterCount() != 1) {
          continue;
        }
        Class<?> param = method.getParameterTypes()[0];
        if (param.isAssignableFrom(entity.getClass())) {
          target = method;
          break;
        }
      }
      if (target != null) {
        target.invoke(citizen, entity);
        return "setEntity ok id=" + id;
      }
    } catch (Exception ex) {
      return ex.getMessage() == null ? "<error>" : ex.getMessage();
    }
    try {
      var method = citizen.getClass().getMethod("setEntityId", int.class);
      method.invoke(citizen, id);
      return "setEntityId(int) ok id=" + id;
    } catch (NoSuchMethodException ignored) {
      // Fallthrough to other options.
    } catch (Exception ex) {
      return ex.getMessage() == null ? "<error>" : ex.getMessage();
    }
    try {
      var method = citizen.getClass().getMethod("setEntityId", Integer.class);
      method.invoke(citizen, Integer.valueOf(id));
      return "setEntityId(Integer) ok id=" + id;
    } catch (NoSuchMethodException ignored) {
      // Fallthrough to field.
    } catch (Exception ex) {
      return ex.getMessage() == null ? "<error>" : ex.getMessage();
    }
    return "<no-public-setter>";
  }

  private int safeCitizenEntityId(ICitizenData citizen) {
    Object entityIdValue = tryInvoke(citizen, "getEntityId");
    if (entityIdValue instanceof Number number) {
      return number.intValue();
    }
    return -1;
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
    try {
      var method = citizen.getClass().getMethod("getPosition");
      Object pos = method.invoke(citizen);
      if (pos != null) {
        return pos.toString();
      }
    } catch (Exception ignored) {
      // Ignore.
    }
    return "<unknown>";
  }

  private Object tryInvoke(Object target, String methodName) {
    if (target == null) {
      return null;
    }
    try {
      var method = target.getClass().getMethod(methodName);
      return method.invoke(target);
    } catch (Exception ex) {
      logReflectionFailure(target, methodName, ex);
      return null;
    }
  }

  private void logReflectionFailure(Object target, String methodName, Exception ex) {
    if (!BuildingCreateShop.isDebugRequests() || target == null) {
      return;
    }
    String key =
        target.getClass().getName() + "#" + methodName + ":" + ex.getClass().getSimpleName();
    if (!REFLECTION_WARNED.add(key)) {
      return;
    }
    TheSettlerXCreate.LOGGER.info(
        "[CreateShop] reflection call failed {} err={}",
        key,
        ex.getMessage() == null ? "<null>" : ex.getMessage());
  }

  private String describeTask(Object task) {
    if (task == null) {
      return "<none>";
    }
    StringBuilder detail = new StringBuilder();
    Object token = tryInvoke(task, "getRequestToken");
    if (token != null) {
      detail.append("token=").append(token).append(" ");
    }
    Object request = tryInvoke(task, "getRequest");
    if (request != null) {
      detail.append("request=").append(request).append(" ");
    }
    Object requester = tryInvoke(task, "getRequester");
    if (requester != null) {
      detail.append("requester=").append(requester).append(" ");
    }
    Object from = tryInvoke(task, "getFrom");
    if (from != null) {
      detail.append("from=").append(from).append(" ");
    }
    Object to = tryInvoke(task, "getTo");
    if (to != null) {
      detail.append("to=").append(to).append(" ");
    }
    Object location = tryInvoke(task, "getLocation");
    if (location != null) {
      detail.append("location=").append(location).append(" ");
    }
    Object pickup = tryInvoke(task, "getPickupLocation");
    if (pickup != null) {
      detail.append("pickup=").append(pickup).append(" ");
    }
    Object delivery = tryInvoke(task, "getDeliveryLocation");
    if (delivery != null) {
      detail.append("delivery=").append(delivery).append(" ");
    }
    Object start = tryInvoke(task, "getStart");
    if (start != null) {
      detail.append("start=").append(start).append(" ");
    }
    Object target = tryInvoke(task, "getTarget");
    if (target != null) {
      detail.append("target=").append(target).append(" ");
    }
    String result = detail.toString().trim();
    return result.isEmpty() ? task.toString() : result;
  }

  private String appendJobDetail(Object job, String base, String methodName) {
    if (job == null) {
      return base;
    }
    try {
      var method = job.getClass().getMethod(methodName);
      Object value = method.invoke(job);
      if (value != null) {
        return base + " " + methodName + "=" + value;
      }
    } catch (Exception ignored) {
      // Ignore.
    }
    return base;
  }
}
