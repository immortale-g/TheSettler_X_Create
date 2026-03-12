package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CreateShopRequestResolverAssignmentDriftRecoveryGuardTest {
  @Test
  void tickPendingRecoversAssignmentsFromLocalResolversOnResolverIdDrift() throws Exception {
    String collectorSource =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/requestsystem/resolver/CreateShopPendingTokenCollectorService.java"));
    String ownershipSource =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/requestsystem/resolver/CreateShopResolverOwnership.java"));

    assertTrue(collectorSource.contains("collectAssignedTokensFromLocalResolvers"));
    assertTrue(collectorSource.contains("tickPending assignment drift recovered"));
    assertTrue(ownershipSource.contains("isLocalShopResolver("));
  }
}
