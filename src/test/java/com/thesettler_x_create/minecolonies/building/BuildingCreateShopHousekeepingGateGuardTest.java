package com.thesettler_x_create.minecolonies.building;

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
                "src/main/java/com/thesettler_x_create/minecolonies/building/BuildingCreateShop.java"));
    assertTrue(source.contains("!hasHousekeepingAvailableWorker()"));
    assertTrue(!source.contains("|| !isWorkerWorking())"));
    assertTrue(source.contains("HOUSEKEEPING_MAX_CATCHUP_STACKS"));
    assertTrue(source.contains("elapsed / HOUSEKEEPING_TRANSFER_INTERVAL"));
  }
}
