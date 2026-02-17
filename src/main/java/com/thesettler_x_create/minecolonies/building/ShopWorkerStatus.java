package com.thesettler_x_create.minecolonies.building;

import com.minecolonies.api.colony.ICitizenData;
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

  private final BuildingCreateShop shop;

  ShopWorkerStatus(BuildingCreateShop shop) {
    this.shop = shop;
  }

  boolean hasActiveWorker() {
    return shop.getAllAssignedCitizen().stream()
        .anyMatch(citizen -> citizen.getJob() instanceof JobCreateShop);
  }

  boolean isWorkerWorking() {
    for (ICitizenData citizen : shop.getAllAssignedCitizen()) {
      if (citizen == null || !(citizen.getJob() instanceof JobCreateShop)) {
        continue;
      }
      if (isCitizenUnavailable(citizen)) {
        continue;
      }
      if (citizen.isWorking()) {
        return true;
      }
      if (citizen.getJobStatus() == com.minecolonies.api.entity.ai.JobStatus.WORKING) {
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
    return false;
  }

  private boolean isCitizenUnavailable(ICitizenData citizen) {
    if (citizen.isAsleep()) {
      return true;
    }
    VisibleCitizenStatus status = citizen.getStatus();
    return status != null && UNAVAILABLE_STATUSES.contains(status);
  }
}
