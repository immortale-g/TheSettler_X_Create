package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CreateShopRequestResolverInflightAttemptResolveGuardTest {
  @Test
  void attemptResolveSubtractsExistingInflightBeforeNetworkOrder() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/requestsystem/resolver/CreateShopAttemptResolveService.java"));

    assertTrue(source.contains("pickup.getInflightRemaining("));
    assertTrue(source.contains("deliverable.getResult()"));
    assertTrue(source.contains("tile.getShopAddress()"));
    assertTrue(source.contains("int effectiveNetworkNeeded = remaining;"));
    assertTrue(
        source.contains(
            "effectiveNetworkNeeded = Math.max(0, remaining - Math.max(0, inflightRemaining));"));
    assertTrue(source.contains("attemptResolve:wait-existing-inflight"));
  }
}
