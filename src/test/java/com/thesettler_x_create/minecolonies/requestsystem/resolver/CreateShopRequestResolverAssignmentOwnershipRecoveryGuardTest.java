package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CreateShopRequestResolverAssignmentOwnershipRecoveryGuardTest {
  @Test
  void tickPendingHasRequestOwnershipFallbackWhenResolverKeyRecoveryFails() throws Exception {
    String resolverSource =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/requestsystem/resolver/CreateShopRequestResolver.java"));
    String ownershipSource =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/requestsystem/resolver/CreateShopResolverOwnership.java"));

    assertTrue(resolverSource.contains("ownership.collectAssignedTokensByRequestResolver"));
    assertTrue(resolverSource.contains("tickPending owner-sync"));
    assertTrue(ownershipSource.contains("getResolverForRequest(request)"));
  }
}
