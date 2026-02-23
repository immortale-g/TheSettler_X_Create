package com.thesettler_x_create.minecolonies.tileentity;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class TileEntityCreateShopHousekeepingGuardTest {
  @Test
  void exposesUnreservedRackDetectionAndHousekeepingMoveMethods() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/tileentity/TileEntityCreateShop.java"));
    assertTrue(source.contains("hasUnreservedRackItems("));
    assertTrue(source.contains("moveUnreservedRackStacksToHut("));
    assertTrue(source.contains("collectRackBudgets("));
    assertTrue(source.contains("simulateInsertCount("));
    assertTrue(!source.contains("probe.copy(), hut"));
  }
}
