package com.thesettler_x_create.minecolonies.building;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CreateShopBlueprintBoundRackGuardTest {
  @Test
  void rackIndexDoesNotDiscoverUnregisteredRacksByRadius() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/building/ShopRackIndex.java"));

    assertTrue(source.contains("shop.getContainerList().iterator()"));
    assertTrue(source.contains("iterator.remove()"));
    assertFalse(source.contains("scanRackBox("));
    assertFalse(source.contains("shop.addContainer("));
    assertFalse(source.contains("getLocation().getInDimensionLocation()"));
  }

  @Test
  void housekeepingOnlyUsesRegisteredContainers() throws Exception {
    // The rack-scan itself lives in ShopRackAccess (extracted from TileEntityCreateShop in the
    // pre-1.0 hardening pass); housekeeping (still on TileEntityCreateShop) reuses it rather than
    // scanning containers itself.
    String rackAccessSource =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/tileentity/ShopRackAccess.java"));
    String tileSource =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/tileentity/TileEntityCreateShop.java"));

    assertTrue(
        rackAccessSource.contains("for (BlockPos pos : owner.getBuilding().getContainers())"));
    assertFalse(rackAccessSource.contains("housekeeping rack fallback scan active"));
    assertFalse(rackAccessSource.contains("rackPositions.add(new BlockPos"));
    assertFalse(tileSource.contains("housekeeping rack fallback scan active"));
    assertFalse(tileSource.contains("rackPositions.add(new BlockPos"));
  }

  @Test
  void resolverPlanningDoesNotScanUnregisteredRacks() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/requestsystem/resolver/CreateShopResolverPlanning.java"));

    assertTrue(source.contains("tile.getBuilding().getContainers()"));
    assertFalse(source.contains("scanRacksAroundShop"));
    assertFalse(source.contains("fallback rack scan"));
    assertFalse(source.contains("unregistered racks"));
  }
}
