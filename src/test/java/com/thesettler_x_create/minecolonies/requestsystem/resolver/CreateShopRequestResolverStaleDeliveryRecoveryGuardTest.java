package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CreateShopRequestResolverStaleDeliveryRecoveryGuardTest {
  @Test
  void resolverContainsStaleDeliveryRecoveryPath() throws Exception {
    String resolverSource =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/requestsystem/resolver/CreateShopRequestResolver.java"));
    String lifecycleStoreSource =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/requestsystem/resolver/CreateShopLifecycleStateStore.java"));
    String lifecycleSource =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/requestsystem/resolver/CreateShopDeliveryChildLifecycleService.java"));
    String reconcileSource =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/requestsystem/resolver/CreateShopChildReconciliationService.java"));
    String recoverySource =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/requestsystem/resolver/CreateShopDeliveryChildRecoveryService.java"));
    String originSource =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/requestsystem/resolver/CreateShopDeliveryOriginMatcher.java"));

    assertTrue(lifecycleSource.contains("isStaleDeliveryChild("));
    assertTrue(reconcileSource.contains("stale-child-recovery"));
    assertTrue(originSource.contains("isLocalShopDeliveryChild("));
    assertTrue(originSource.contains("isDeliveryFromLocalShopStart("));
    assertTrue(resolverSource.contains("lifecycleStateStore"));
    assertTrue(lifecycleStoreSource.contains("parentDeliveryActiveSince"));
    assertTrue(reconcileSource.contains("skip (non-local delivery child)"));
    assertTrue(reconcileSource.contains("stale delivery-child recovery"));
    assertTrue(reconcileSource.contains("stale-child-recovery"));
    assertTrue(recoverySource.contains("manager.updateRequestState("));
    assertTrue(
        recoverySource.contains(
            "childToken, com.minecolonies.api.colony.requestsystem.request.RequestState.CANCELLED"));
  }
}
