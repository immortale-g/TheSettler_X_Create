package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * After Phase 3.5, stale delivery-child recovery has been removed. MineColonies owns the delivery
 * lifecycle after DELIVERY_CREATED. The extra-active-child guard (duplicate children for one
 * parent) is retained. This guard confirms both the removal and the remaining invariants.
 */
class CreateShopRequestResolverStaleDeliveryRecoveryGuardTest {
  @Test
  void staleDeliveryChildRecoveryIsRemoved() throws Exception {
    String lifecycleSource =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/requestsystem/resolver/CreateShopDeliveryChildLifecycleService.java"));
    String reconcileSource =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/requestsystem/resolver/CreateShopChildReconciliationService.java"));
    String lifecycleStoreSource =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/requestsystem/resolver/CreateShopLifecycleStateStore.java"));

    assertFalse(lifecycleSource.contains("isStaleDeliveryChild("));
    assertFalse(reconcileSource.contains("stale-child-recovery"));
    assertFalse(reconcileSource.contains("stale delivery-child recovery"));
    assertFalse(lifecycleStoreSource.contains("parentDeliveryActiveSince"));
  }

  @Test
  void extraActiveChildGuardAndLocalOriginCheckAreRetained() throws Exception {
    String reconcileSource =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/requestsystem/resolver/CreateShopChildReconciliationService.java"));
    String resolverSource =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/requestsystem/resolver/CreateShopRequestResolver.java"));

    assertTrue(reconcileSource.contains(CreateShopGuardConstants.EXTRA_ACTIVE_CHILD_RECOVERY));
    assertTrue(reconcileSource.contains("isLocalShopDeliveryChild("));
    assertTrue(reconcileSource.contains("skip (non-local delivery child)"));
    assertTrue(resolverSource.contains("lifecycleStateStore"));
    // Recovery service (for duplicate-child cancellation) still exists
    assertTrue(reconcileSource.contains("deliveryChildRecoveryService.recover("));
  }
}
