package com.thesettler_x_create.minecolonies.building;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class BuildingCreateShopHousekeepingGateGuardTest {
  @Test
  void housekeepingIsGatedByAvailableWorkerAndNotWorkingMetadata() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/building/ShopHousekeepingOrchestrator.java"));
    assertTrue(source.contains("!shop.hasHousekeepingAvailableWorker()"));
    assertFalse(source.contains("|| !isWorkerWorking())"));
    assertTrue(source.contains("MAX_CATCHUP_STACKS"));
    assertTrue(source.contains("elapsed / TRANSFER_INTERVAL"));
  }
}
