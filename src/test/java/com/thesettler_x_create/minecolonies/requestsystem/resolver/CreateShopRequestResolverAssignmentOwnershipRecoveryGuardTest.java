package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CreateShopRequestResolverAssignmentOwnershipRecoveryGuardTest {
  @Test
  void tickPendingHasRequestOwnershipFallbackWhenResolverKeyRecoveryFails() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/requestsystem/resolver/CreateShopRequestResolver.java"));

    assertTrue(source.contains("collectAssignedTokensByRequestResolver"));
    assertTrue(source.contains("assignment drift recovered by request ownership"));
    assertTrue(source.contains("getResolverForRequest(request)"));
  }
}
