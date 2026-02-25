package com.thesettler_x_create.minecolonies.building;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ShopWarehouseRegistrarQueueModuleGuardTest {
  @Test
  void warehouseRegistrationRequiresQueueModuleOnly() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/building/ShopWarehouseRegistrar.java"));

    assertTrue(source.contains("BuildingModules.WAREHOUSE_REQUEST_QUEUE"));
    assertFalse(source.contains("BuildingModules.WAREHOUSE_COURIERS"));
  }
}
