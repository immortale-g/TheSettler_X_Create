package com.thesettler_x_create.minecolonies.building;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class BuildingCreateShopHousekeepingResolverGuardTest {
  @Test
  void housekeepingSkipsRackToHutMovesWhileResolverHasActiveWork() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/building/BuildingCreateShop.java"));

    assertTrue(source.contains("resolver != null && resolver.hasActiveWork()"));
    assertTrue(
        source.contains("housekeeping blocked reason=resolver-active-work pendingUnreserved={}"));
  }
}
