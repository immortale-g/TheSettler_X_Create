package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CreateShopRequestResolverTwoPhaseStaleRecoveryGuardTest {
  @Test
  void staleRecoveryRequiresArmAndRecheckBeforeMutation() throws Exception {
    String resolverSource =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/requestsystem/resolver/CreateShopRequestResolver.java"));
    String lifecycleSource =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/requestsystem/resolver/CreateShopDeliveryChildLifecycleService.java"));
    String reconcileSource =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/requestsystem/resolver/CreateShopChildReconciliationService.java"));

    assertTrue(lifecycleSource.contains("getParentStaleRecoveryArmedAt(parentToken)"));
    assertTrue(reconcileSource.contains("deliveryChildLifecycleService.isStaleRecoveryArmed("));
    assertTrue(
        reconcileSource.contains(
            "if (!deliveryChildLifecycleService.isStaleRecoveryArmed("));
    assertTrue(
        lifecycleSource.contains("resolver.getRecheck().scheduleParentChildRecheck(manager, parentToken)"));
    assertTrue(resolverSource.contains("clearStaleRecoveryArm("));
  }
}
