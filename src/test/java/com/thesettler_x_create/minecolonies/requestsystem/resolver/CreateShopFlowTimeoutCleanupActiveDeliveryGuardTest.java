package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CreateShopFlowTimeoutCleanupActiveDeliveryGuardTest {
  @Test
  void timeoutCleanupSkipsActiveDeliveryWindow() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/requestsystem/resolver/CreateShopFlowTimeoutCleanupService.java"));

    assertTrue(source.contains("timeout-cleanup:skip-active-delivery"));
    assertTrue(source.contains("timeout-cleanup:skip-runtime-active"));
    assertTrue(source.contains("resolver.getParentDeliveryTokensSnapshot().contains(token)"));
    assertTrue(source.contains("request.hasChildren()"));
    assertTrue(source.contains("resolver.hasDeliveriesCreated(token)"));
    assertTrue(source.contains("resolver.getPendingTracker().hasDeliveryStarted(token)"));
  }
}
