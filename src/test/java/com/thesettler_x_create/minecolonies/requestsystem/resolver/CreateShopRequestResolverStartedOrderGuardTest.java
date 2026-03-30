package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CreateShopRequestResolverStartedOrderGuardTest {
  @Test
  void blocksAutoReorderAfterFirstDeliveryStart() throws Exception {
    String attemptResolveSource =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/requestsystem/resolver/CreateShopAttemptResolveService.java"));
    String topupSource =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/requestsystem/resolver/CreateShopPendingTopupService.java"));

    assertTrue(topupSource.contains("pendingTracker.hasDeliveryStarted(request.getId())"));
    assertTrue(attemptResolveSource.contains("attemptResolve:block-auto-reorder-started"));
    assertTrue(topupSource.contains("tickPending:block-auto-reorder-started"));
  }
}
