package com.thesettler_x_create.minecolonies.registry;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ModMinecoloniesBuildingsTaskModuleGuardTest {
  @Test
  void createShopRegistersCustomWarehouseTaskModuleViewForInflightVisibility() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/registry/ModMinecoloniesBuildings.java"));

    assertTrue(source.contains("\"warehouse_request_queue\""));
    assertTrue(source.contains("CreateShopTaskModule::new"));
    assertTrue(source.contains("CreateShopTaskModuleView::new"));
  }
}
