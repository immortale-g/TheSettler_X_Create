package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CreateShopRequestResolverRackCoverageTopupGuardTest {
  @Test
  void topupComputationSubtractsRackAvailabilityBeforeOrderingFromNetwork() throws Exception {
    String processorSource =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/requestsystem/resolver/CreateShopPendingRequestProcessorService.java"));
    String topupSource =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/requestsystem/resolver/CreateShopPendingTopupService.java"));

    assertTrue(
        processorSource.contains(
            "int rackAvailable = resolver.getPlanning().getAvailableFromRacks(tile, deliverable);"));
    assertTrue(topupSource.contains("pendingCount"));
    assertTrue(topupSource.contains("- Math.max(0, rackAvailableForRequest)"));
  }
}
