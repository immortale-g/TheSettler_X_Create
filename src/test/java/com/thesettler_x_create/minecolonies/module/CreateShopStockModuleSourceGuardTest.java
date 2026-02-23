package com.thesettler_x_create.minecolonies.module;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CreateShopStockModuleSourceGuardTest {
  @Test
  void stockModuleUsesRegisteredStorageInsteadOfNetworkSummary() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/module/CreateShopStockModule.java"));

    assertTrue(source.contains("getRegisteredStorageStock()"));
    assertFalse(source.contains("LogisticsManager.getSummaryOfNetwork"));
  }
}
