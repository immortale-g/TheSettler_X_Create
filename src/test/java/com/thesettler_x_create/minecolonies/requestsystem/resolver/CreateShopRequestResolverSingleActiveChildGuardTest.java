package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CreateShopRequestResolverSingleActiveChildGuardTest {
  @Test
  void tickPendingEnforcesSingleActiveLocalDeliveryChildPerParent() throws Exception {
    String reconcileSource =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/requestsystem/resolver/CreateShopChildReconciliationService.java"));

    assertTrue(reconcileSource.contains("IToken<?> activeLocalDeliveryChild = null;"));
    assertTrue(reconcileSource.contains("deliveryChildRecoveryService.recover("));
    assertTrue(reconcileSource.contains("extra-active-child-recovery"));
  }
}
