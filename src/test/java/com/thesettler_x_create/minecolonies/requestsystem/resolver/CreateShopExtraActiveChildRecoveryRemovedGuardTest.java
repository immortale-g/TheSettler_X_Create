package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * The extra-active-child recovery cancelled every second active local delivery child of a parent.
 * MineColonies answers a cancelled child by cancelling all children of the parent, so with several
 * deliveries on the way it wiped them all. It was detached in 0.4.0 and removed with its service.
 */
class CreateShopExtraActiveChildRecoveryRemovedGuardTest {
  private static final Path RESOLVER_DIR =
      Path.of("src/main/java/com/thesettler_x_create/minecolonies/requestsystem/resolver");

  @Test
  void recoveryServiceIsGone() {
    assertFalse(Files.exists(RESOLVER_DIR.resolve("CreateShopDeliveryChildRecoveryService.java")));
  }

  @Test
  void reconcileDoesNotCancelDeliveryChildren() throws Exception {
    String source =
        Files.readString(RESOLVER_DIR.resolve("CreateShopChildReconciliationService.java"));

    assertFalse(source.contains("deliveryChildRecoveryService"));
    assertFalse(source.contains("activeLocalDeliveryChild"));
    assertFalse(source.contains(CreateShopGuardConstants.EXTRA_ACTIVE_CHILD_RECOVERY));
    assertFalse(source.contains("updateRequestState("));
  }
}
