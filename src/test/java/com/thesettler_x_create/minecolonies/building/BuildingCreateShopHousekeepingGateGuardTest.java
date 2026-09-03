package com.thesettler_x_create.minecolonies.building;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class BuildingCreateShopHousekeepingGateGuardTest {
  @Test
  void housekeepingIsGatedByAvailableWorkerAndActiveWork() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/building/ShopHousekeepingOrchestrator.java"));
    assertTrue(source.contains("return shop.hasHousekeepingAvailableWorker();"));
    assertTrue(source.contains("if (hasActiveLocalDeliveryChildren(colony, pickup))"));
    assertTrue(source.contains("resolver != null && resolver.hasProtectedInventoryWindow()"));
    assertTrue(source.contains("if (elapsed < TRANSFER_INTERVAL)"));
  }
}
