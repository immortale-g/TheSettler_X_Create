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

    // A pickup is asked for when a courier may take something, not merely when the combined
    // inventory holds something: the racks are in there too and their stock is kept, so the plain
    // "is anything there" question called a courier over every 25 seconds for goods it was never
    // allowed to carry away (seen in game on 2026-09-18).
    assertTrue(orchestratorSource.contains("boolean hutHasItems = shop.hasItemsAPickupMayTake();"));
    assertFalse(orchestratorSource.contains("tile.hasHutInventoryItems()"));
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
        buildingSource.contains("return createPickupRequest(NATIVE_HUT_PICKUP_QUANTITY, true);"));
    assertTrue(
        orchestratorSource.contains(
            "housekeeping pickup request priority={} created={} hutHasItems={}"));
    assertTrue(buildingSource.contains("source is the building requester's hut"));
    assertFalse(buildingSource.contains("createHutInventoryPickupRequest"));
  }
}
