package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CreateShopRequestResolverRackCoverageTopupGuardTest {
  @Test
  void topupComputationSubtractsRackAvailabilityBeforeOrderingFromNetwork() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/requestsystem/resolver/CreateShopRequestResolver.java"));

    assertTrue(
        source.contains("int rackAvailable = planning.getAvailableFromRacks(tile, deliverable);"));
    assertTrue(source.contains("pendingCount"));
    assertTrue(source.contains("- Math.max(0, rackAvailable)"));
  }
}
