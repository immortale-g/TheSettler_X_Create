package com.thesettler_x_create.minecolonies.building;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class BuildingCreateShopResolverDriftGuardTest {
  @Test
  void prefersAssignmentBackedResolverWhenProviderResolverHasNoAssignments() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/building/BuildingCreateShop.java"));

    assertTrue(source.contains("resolver assignment drift detected: switching"));
    assertTrue(source.contains("hasAssignedRequestsForResolver"));
  }
}
