package com.thesettler_x_create.minecolonies.tileentity;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class TileEntityCreateShopCapacityPlannerRackOnlyGuardTest {
  @Test
  void capacityPlannerUsesRackSimulationWithoutHutFallback() throws Exception {
    String tileSource =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/tileentity/TileEntityCreateShop.java"));
    assertTrue(tileSource.contains("planInboundAcceptedStacks"));

    // The actual simulation lives in ShopRackAccess (extracted from TileEntityCreateShop in the
    // pre-1.0 hardening pass).
    String rackAccessSource =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/tileentity/ShopRackAccess.java"));
    assertTrue(rackAccessSource.contains("List<ItemStack> planInboundAcceptedStacks("));
    assertFalse(rackAccessSource.contains("virtualHut"));
  }
}
