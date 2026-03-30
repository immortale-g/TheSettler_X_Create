package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CreateShopRequestResolverRetryingHoldReassignGuardTest {
  @Test
  void retryingReassignAllowsDeliveryWindowHoldWithoutCanResolveGate() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/requestsystem/resolver/CreateShopRetryingReassignService.java"));

    assertTrue(source.contains("boolean deliveryWindowHold ="));
    assertTrue(source.contains("resolver.getPendingTracker().hasDeliveryStarted(requestToken)"));
    assertTrue(source.contains("!resolver.hasParentChildCompletedSeen(requestToken)"));
    assertTrue(
        source.contains(
            "if (!deliveryWindowHold && !resolver.canResolveRequest(manager, casted))"));
    assertTrue(source.contains("hold={}"));
  }
}
