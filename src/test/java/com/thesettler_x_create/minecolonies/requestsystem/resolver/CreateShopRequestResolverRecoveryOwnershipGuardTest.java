package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * After Phase 3.5 the stale-recovery-arm check is gone. The ownership guard (prevent recovery on
 * foreign-resolver-owned parents) is retained in CreateShopDeliveryChildRecoveryService which is
 * used only for the extra-active-child guard.
 */
class CreateShopRequestResolverRecoveryOwnershipGuardTest {
  @Test
  void ownershipGuardIsRetainedInRecoveryServiceForExtraActiveChildCase() throws Exception {
    // RecoveryService is the second top-level class in DeliveryChildLifecycleService.java
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/requestsystem/resolver/CreateShopDeliveryChildLifecycleService.java"));

    assertTrue(
        source.contains("if (!ownership.isRequestOwnedByLocalResolver(manager, parentRequest))"));
    // clearStaleRecoveryArm was removed in Phase 3.5 — ownership bail-out no longer clears stale
    // arm.
    assertFalse(source.contains("clearStaleRecoveryArm(resolver, parentRequest.getId())"));
  }
}
