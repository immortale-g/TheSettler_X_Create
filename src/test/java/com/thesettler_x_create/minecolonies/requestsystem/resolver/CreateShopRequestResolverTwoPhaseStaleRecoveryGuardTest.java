package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * After Phase 3.5 the two-phase stale recovery (arm + recheck + mutation) has been removed.
 * MineColonies owns courier delivery retries; the shop no longer polls or forces recovery.
 */
class CreateShopRequestResolverTwoPhaseStaleRecoveryGuardTest {
  @Test
  void staleRecoveryArmAndRecheckAreRemoved() throws Exception {
    String lifecycleSource =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/requestsystem/resolver/CreateShopDeliveryChildLifecycleService.java"));
    String reconcileSource =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/requestsystem/resolver/CreateShopChildReconciliationService.java"));
    String resolverSource =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/requestsystem/resolver/CreateShopRequestResolver.java"));

    assertFalse(lifecycleSource.contains("getParentStaleRecoveryArmedAt"));
    assertFalse(reconcileSource.contains("isStaleRecoveryArmed("));
    assertFalse(reconcileSource.contains("stale-child-recovery"));
    assertFalse(resolverSource.contains("clearParentStaleRecoveryArm("));
  }
}
