package com.thesettler_x_create.minecolonies.module;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CreateShopStockModuleDualViewGuardTest {
  @Test
  void stockModuleSerializesHutAndStorageStocks() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/module/CreateShopStockModule.java"));

    assertTrue(source.contains("getHutInventoryStock()"));
    assertTrue(source.contains("getRegisteredStorageStock()"));
    assertTrue(source.contains("writeStacks(buf, getHutInventoryStock())"));
    assertTrue(source.contains("writeStacks(buf, getRegisteredStorageStock())"));
  }

  @Test
  void stockLayoutContainsViewToggleButtons() throws Exception {
    String xml =
        Files.readString(
            Path.of(
                "src/main/resources/assets/thesettler_x_create/gui/layouthuts/layoutcreateshop_stock.xml"));

    assertTrue(xml.contains("id=\"viewHut\""));
    assertTrue(xml.contains("id=\"viewStorage\""));
  }
}
