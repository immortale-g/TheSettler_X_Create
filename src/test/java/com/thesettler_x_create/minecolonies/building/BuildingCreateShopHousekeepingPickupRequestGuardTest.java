package com.thesettler_x_create.minecolonies.building;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class BuildingCreateShopHousekeepingPickupRequestGuardTest {
  @Test
  void housekeepingCreatesNativePickupRequestForMovedOrExistingHutItems() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/building/BuildingCreateShop.java"));

    assertTrue(source.contains("boolean hutHasItems = tile.hasHutInventoryItems();"));
    assertTrue(source.contains("if (moved > 0 || hutHasItems) {"));
    assertTrue(source.contains("int pickupPriority = getPickUpPriority();"));
    assertTrue(
        source.contains("boolean pickupRequested = createNativeHutPickupRequest(pickupPriority);"));
    assertTrue(source.contains("return createPickupRequest(pickupPriority);"));
    assertTrue(
        source.contains(
            "housekeeping pickup request priority={} created={} moved={} hutHasItems={}"));
    assertTrue(source.contains("source is the building requester's hut"));
    assertFalse(source.contains("createHutInventoryPickupRequest"));
  }
}
