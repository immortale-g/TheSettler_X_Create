package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CreateShopRequestResolverStaleDeliveryRecoveryGuardTest {
  @Test
  void resolverContainsStaleDeliveryRecoveryPath() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/requestsystem/resolver/CreateShopRequestResolver.java"));
    String reconcileSource =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/requestsystem/resolver/CreateShopChildReconciliationService.java"));
    String recoverySource =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/requestsystem/resolver/CreateShopDeliveryChildRecoveryService.java"));

    assertTrue(source.contains("isStaleDeliveryChild("));
    assertTrue(source.contains("recoverStaleDeliveryChild("));
    assertTrue(source.contains("isLocalShopDeliveryChild("));
    assertTrue(source.contains("isDeliveryFromPickup("));
    assertTrue(source.contains("parentDeliveryActiveSince"));
    assertTrue(reconcileSource.contains("skip (non-local delivery child)"));
    assertTrue(source.contains("stale delivery-child recovery"));
    assertTrue(source.contains("stale-child-recovery"));
    assertTrue(recoverySource.contains("manager.updateRequestState("));
    assertTrue(
        recoverySource.contains(
            "childToken, com.minecolonies.api.colony.requestsystem.request.RequestState.CANCELLED"));
  }
}
