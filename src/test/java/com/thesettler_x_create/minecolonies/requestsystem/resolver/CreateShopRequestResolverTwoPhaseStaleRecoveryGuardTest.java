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

    assertTrue(lifecycleSource.contains("getParentStaleRecoveryArmedAtForOps()"));
    assertTrue(resolverSource.contains("isStaleRecoveryArmed("));
    assertTrue(
        reconcileSource.contains(
            "if (!resolver.isStaleRecoveryArmedForOps(level, standardManager, request.getId()))"));
    assertTrue(
        lifecycleSource.contains("resolver.getRecheckForOps().scheduleParentChildRecheck(manager, parentToken)"));
    assertTrue(resolverSource.contains("clearStaleRecoveryArm("));
  }
}
