package com.thesettler_x_create.minecolonies.building;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class BuildingCreateShopHousekeepingPickupRequestGuardTest {
  @Test
  void housekeepingCreatesNativePickupRequestForMovedOrExistingHutItems() throws Exception {
    String orchestratorSource =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/building/ShopHousekeepingOrchestrator.java"));
    String buildingSource =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/building/BuildingCreateShop.java"));

    assertTrue(orchestratorSource.contains("boolean hutHasItems = tile.hasHutInventoryItems();"));
    assertTrue(orchestratorSource.contains("if (hutHasItems) {"));
    assertTrue(orchestratorSource.contains("int pickupPriority = shop.getPickUpPriority();"));
    assertTrue(
        orchestratorSource.contains(
            "boolean pickupRequested = shop.createNativeHutPickupRequest(pickupPriority);"));
    assertTrue(
        buildingSource.contains(
            "Math.max(pickupPriority, AbstractDeliverymanRequestable.getPlayerActionPriority(false))"));
    assertTrue(buildingSource.contains("return createPickupRequest(effectivePriority);"));
    assertTrue(
        orchestratorSource.contains(
            "housekeeping pickup request priority={} created={} hutHasItems={}"));
    assertTrue(buildingSource.contains("source is the building requester's hut"));
    assertFalse(buildingSource.contains("createHutInventoryPickupRequest"));
  }
}
