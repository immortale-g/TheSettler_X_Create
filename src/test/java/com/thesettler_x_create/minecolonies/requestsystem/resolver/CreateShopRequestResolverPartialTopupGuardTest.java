package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CreateShopRequestResolverPartialTopupGuardTest {
  @Test
  void tickPendingOrdersNetworkTopupForOutstandingPartialDeficit() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/requestsystem/resolver/CreateShopRequestResolver.java"));

    assertTrue(source.contains("int topupNeeded ="));
    assertTrue(source.contains("pendingCount"));
    assertTrue(source.contains("- Math.max(0, reservedForRequest)"));
    assertTrue(source.contains("- Math.max(0, rackAvailable)"));
    assertTrue(source.contains("tickPending:network-topup"));
    assertTrue(
        source.contains(
            "stockResolver.requestFromNetwork(tile, deliverable, topupCount, requesterName)"));
    assertTrue(source.contains("pickup.reserve(requestId, stack.copy(), stack.getCount())"));
  }
}
