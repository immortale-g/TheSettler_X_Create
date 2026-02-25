package com.thesettler_x_create.minecolonies.building;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class BuildingCreateShopWarehouseAccessGuardTest {
  @Test
  void warehouseAccessUsesWarehouseDeliverymanRoleWithoutShopCourierModule() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/building/BuildingCreateShop.java"));

    assertTrue(source.contains("canAccessWareHouse(ICitizenData citizen)"));
    assertTrue(
        source.contains(
            "citizen.getJob() instanceof com.minecolonies.core.colony.jobs.JobDeliveryman"));
    assertTrue(source.contains("migrateLegacyShopCourierAssignments()"));
    assertTrue(source.contains("legacy.getAssignedCitizen()"));
    assertTrue(source.contains("legacy.getAssignedEntities()"));
    assertTrue(!source.contains("hasDeliveryman("));
  }
}
