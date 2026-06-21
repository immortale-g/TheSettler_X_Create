package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * After Phase 3.5 the stale-child clock (parentDeliveryActiveSince, deliveryChildActiveSince) and
 * stale-recovery-arm have been removed. MineColonies owns the delivery lifecycle after
 * DELIVERY_CREATED. This guard confirms the removal.
 */
class CreateShopRequestResolverParentScopedStaleClockGuardTest {
  @Test
  void staleClockMapsAreRemovedFromStateStore() throws Exception {
    String storeSource =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/requestsystem/resolver/CreateShopLifecycleStateStore.java"));

    assertFalse(storeSource.contains("parentDeliveryActiveSince"));
    assertFalse(storeSource.contains("deliveryChildActiveSince"));
    assertFalse(storeSource.contains("parentStaleRecoveryArmedAt"));
  }

  @Test
  void reconcileLoopNoLongerCallsIsStaleDeliveryChild() throws Exception {
    String reconcileSource =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/requestsystem/resolver/CreateShopChildReconciliationService.java"));

    assertFalse(reconcileSource.contains("isStaleDeliveryChild("));
    assertFalse(reconcileSource.contains("isStaleRecoveryArmed("));
    // Extra-active-child guard (duplicate children) is still present.
    assertTrue(reconcileSource.contains("extra-active-child-recovery"));
  }
}
