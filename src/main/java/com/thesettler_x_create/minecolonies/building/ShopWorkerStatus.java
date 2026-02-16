package com.thesettler_x_create.minecolonies.building;

import com.thesettler_x_create.minecolonies.job.JobCreateShop;

/** Tracks whether Create Shop workers are assigned and active. */
final class ShopWorkerStatus {
  private final BuildingCreateShop shop;

  ShopWorkerStatus(BuildingCreateShop shop) {
    this.shop = shop;
  }

  boolean hasActiveWorker() {
    return shop.getAllAssignedCitizen().stream()
        .anyMatch(citizen -> citizen.getJob() instanceof JobCreateShop);
  }

  boolean isWorkerWorking() {
    for (var citizen : shop.getAllAssignedCitizen()) {
      if (citizen == null || !(citizen.getJob() instanceof JobCreateShop)) {
        continue;
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
}
