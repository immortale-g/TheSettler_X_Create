package com.thesettler_x_create.minecolonies.building;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ShopInflightTrackerGuardTest {
  @Test
  void skipsLostPackagePromptWhileLocalDeliveryIsActive() throws Exception {
    String trackerSource =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/building/ShopInflightTracker.java"));
    String buildingSource =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/building/BuildingCreateShop.java"));

    assertTrue(trackerSource.contains("hasActiveLocalDeliveryChildrenForInflight(colony)"));
    assertTrue(
        trackerSource.contains("lost-package interaction skipped: local delivery still active"));
    assertTrue(
        buildingSource.contains("hasActiveLocalDeliveryChildrenForInflight(IColony colony)"));
  }
}
