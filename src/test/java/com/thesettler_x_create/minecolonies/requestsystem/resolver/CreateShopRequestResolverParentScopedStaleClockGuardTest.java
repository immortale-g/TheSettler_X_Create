package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CreateShopRequestResolverParentScopedStaleClockGuardTest {
  @Test
  void staleClockUsesParentScopeInsteadOfChildTokenOnly() throws Exception {
    String lifecycleSource =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/requestsystem/resolver/CreateShopDeliveryChildLifecycleService.java"));
    String reconcileSource =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/requestsystem/resolver/CreateShopChildReconciliationService.java"));

    assertTrue(
        reconcileSource.contains(
            "deliveryChildLifecycleService.isStaleDeliveryChild("));
    assertTrue(
        lifecycleSource.contains(
            "resolver.getParentDeliveryActiveSinceForOps().putIfAbsent(parentToken, now)"));
  }
}
