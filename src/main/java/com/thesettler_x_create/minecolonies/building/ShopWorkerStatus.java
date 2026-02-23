package com.thesettler_x_create.minecolonies.building;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.entity.ai.JobStatus;
import com.minecolonies.api.entity.citizen.VisibleCitizenStatus;
import com.thesettler_x_create.minecolonies.job.JobCreateShop;
import java.util.Set;

/** Tracks whether Create Shop workers are assigned and active. */
final class ShopWorkerStatus {
  private static final Set<VisibleCitizenStatus> UNAVAILABLE_STATUSES =
      Set.of(
          VisibleCitizenStatus.SICK,
          VisibleCitizenStatus.SLEEP,
          VisibleCitizenStatus.EAT,
          VisibleCitizenStatus.MOURNING,
          VisibleCitizenStatus.RAIDED,
          VisibleCitizenStatus.BAD_WEATHER,
          VisibleCitizenStatus.HOUSE);
  private static final Set<VisibleCitizenStatus> HOUSEKEEPING_BLOCKING_STATUSES =
      Set.of(
          VisibleCitizenStatus.SICK,
          VisibleCitizenStatus.SLEEP,
          VisibleCitizenStatus.EAT,
          VisibleCitizenStatus.MOURNING,
          VisibleCitizenStatus.RAIDED,
          VisibleCitizenStatus.BAD_WEATHER,
          VisibleCitizenStatus.HOUSE);

  private final BuildingCreateShop shop;

  ShopWorkerStatus(BuildingCreateShop shop) {
    this.shop = shop;
  }

  boolean hasActiveWorker() {
    return shop.getAllAssignedCitizen().stream()
        .anyMatch(citizen -> citizen.getJob() instanceof JobCreateShop);
  }

  boolean hasAvailableWorker() {
    for (ICitizenData citizen : shop.getAllAssignedCitizen()) {
      if (citizen == null || !(citizen.getJob() instanceof JobCreateShop)) {
        continue;
      }
      if (!isCitizenUnavailable(citizen)) {
        return true;
      }
    }
    return false;
  }

  boolean hasHousekeepingAvailableWorker() {
    for (ICitizenData citizen : shop.getAllAssignedCitizen()) {
      if (citizen == null || !(citizen.getJob() instanceof JobCreateShop)) {
        continue;
      }
      if (!isHousekeepingBlocked(citizen)) {
        return true;
      }
    }
    return false;
  }

  String describeHousekeepingBlockReason() {
    boolean hasShopWorker = false;
    for (ICitizenData citizen : shop.getAllAssignedCitizen()) {
      if (citizen == null || !(citizen.getJob() instanceof JobCreateShop)) {
        continue;
      }
      hasShopWorker = true;
      if (citizen.isAsleep()) {
        return "asleep";
      }
      VisibleCitizenStatus status = citizen.getStatus();
      if (status != null && HOUSEKEEPING_BLOCKING_STATUSES.contains(status)) {
        return "status:" + status;
      }
      return "available";
    }
    return hasShopWorker ? "blocked" : "no-assigned-shopworker";
  }

  boolean isWorkerWorking() {
    boolean hasShopWorker = false;
    for (ICitizenData citizen : shop.getAllAssignedCitizen()) {
      if (citizen == null || !(citizen.getJob() instanceof JobCreateShop)) {
        continue;
      }
      hasShopWorker = true;
      if (isCitizenUnavailable(citizen)) {
        continue;
      }
      if (citizen.getJobStatus() == JobStatus.WORKING) {
        return true;
      }
      if (citizen.isWorking()) {
        return true;
      }
      try {
        var entity = citizen.getEntity();
        if (entity != null && entity.isPresent()) {
          String meta = entity.get().getRenderMetadata();
          if ("working".equals(meta)) {
            return true;
          }
        }
      } catch (Exception ignored) {
        // Ignore entity lookup issues.
      }
    }
    if (hasShopWorker
        && shop.getColony() != null
        && shop.getColony().getWorld() != null
        && shop.getColony().getWorld().isDay()) {
      // Daytime fallback: avoid hard false-negatives from transient AI metadata/status drift.
      return true;
    }
    return false;
  }

  private boolean isCitizenUnavailable(ICitizenData citizen) {
    if (citizen.isAsleep()) {
      return true;
    }
    VisibleCitizenStatus status = citizen.getStatus();
    return status != null && UNAVAILABLE_STATUSES.contains(status);
  }

  private boolean isHousekeepingBlocked(ICitizenData citizen) {
    if (citizen.isAsleep()) {
      return true;
    }
    VisibleCitizenStatus status = citizen.getStatus();
    return status != null && HOUSEKEEPING_BLOCKING_STATUSES.contains(status);
  }
}
