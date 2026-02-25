package com.thesettler_x_create.minecolonies.building;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class BuildingCreateShopLostPackageRestartInflightGateGuardTest {
  @Test
  void restartIsBoundedByTrackedInflightRemaining() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/building/BuildingCreateShop.java"));

    assertTrue(source.contains("int trackedRemaining = pickup.getInflightRemaining("));
    assertTrue(
        source.contains(
            "int reorderTarget = Math.min(Math.max(1, remaining), Math.max(0, trackedRemaining));"));
    assertTrue(source.contains("requested.setCount(reorderTarget);"));
  }
}
