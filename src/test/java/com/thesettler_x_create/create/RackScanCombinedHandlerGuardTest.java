package com.thesettler_x_create.create;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * The hut block and each half of a double rack hand out a combined item handler, so any reader that
 * counts or plans stock through {@code getItemHandlerCap()} sees slots twice. Readers must walk the
 * loaded racks (hut left out) and read each through {@code getInventory()}.
 */
class RackScanCombinedHandlerGuardTest {

  @Test
  void pickupHandlerScansSingleRacksWithoutTheHut() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/create/VirtualCreateNetworkItemHandler.java"));
    assertTrue(source.contains("shop.getLoadedRacks()"));
    assertTrue(source.contains("loaded.rack().getInventory()"));
    assertFalse(source.contains("getItemHandlerCap()"));
  }

  @Test
  void outputBlockPullsFromSingleRacks() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/blockentity/CreateShopOutputBlockEntity.java"));
    assertFalse(source.contains("rack.getItemHandlerCap()"));
  }
}
