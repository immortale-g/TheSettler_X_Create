package com.thesettler_x_create.minecolonies.module;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CreateShopStockModuleDualViewGuardTest {
  @Test
  void stockModuleSerializesRegisteredStorageOnly() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/module/CreateShopStockModule.java"));

    assertFalse(source.contains("getHutInventoryStock()"));
    assertTrue(source.contains("getRegisteredStorageStock()"));
    assertTrue(source.contains("writeStacks(buf, getRegisteredStorageStock())"));
  }

  @Test
  void stockLayoutHasNoManualRequestOrViewToggleButtons() throws Exception {
    String xml =
        Files.readString(
            Path.of(
                "src/main/resources/assets/thesettler_x_create/gui/layouthuts/layoutcreateshop_stock.xml"));

    assertFalse(xml.contains("id=\"viewHut\""));
    assertFalse(xml.contains("id=\"viewStorage\""));
    assertFalse(xml.contains("id=\"requestAll\""));
  }

  @Test
  void createShopKeepsNativeWorkerModuleForForcePickupFlow() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/registry/ModMinecoloniesBuildings.java"));

    assertTrue(source.contains("new WorkerBuildingModule("));
    assertTrue(source.contains("WorkerBuildingModuleView::new"));
    assertFalse(source.contains("BuildingModules.WAREHOUSE_COURIERS"));
  }
}
