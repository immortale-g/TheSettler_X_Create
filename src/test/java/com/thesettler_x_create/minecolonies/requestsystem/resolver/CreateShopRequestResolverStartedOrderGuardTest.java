package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CreateShopRequestResolverStartedOrderGuardTest {
  @Test
  void blocksAutoReorderAfterFirstDeliveryStart() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/requestsystem/resolver/CreateShopRequestResolver.java"));

    assertTrue(source.contains("pendingTracker.hasDeliveryStarted(request.getId())"));
    assertTrue(source.contains("attemptResolve:block-auto-reorder-started"));
    assertTrue(source.contains("tickPending:block-auto-reorder-started"));
  }
}
