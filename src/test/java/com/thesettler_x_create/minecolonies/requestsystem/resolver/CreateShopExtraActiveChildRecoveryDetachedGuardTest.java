package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * The extra-active-child recovery cancelled every second active local delivery child of a parent.
 * MineColonies then cancels all children of that parent, so it has to stay off once a parent can
 * have several deliveries on the way. It is kept as commented-out code for a possible comeback;
 * this guard pins both halves.
 */
class CreateShopExtraActiveChildRecoveryDetachedGuardTest {
  private static final Path RECONCILE_SOURCE =
      Path.of(
          "src/main/java/com/thesettler_x_create/minecolonies/requestsystem/resolver/CreateShopChildReconciliationService.java");

  @Test
  void extraActiveChildRecoveryIsNotActive() throws Exception {
    String activeCode = CreateShopGuardSource.activeCode(Files.readString(RECONCILE_SOURCE));

    assertFalse(activeCode.contains("deliveryChildRecoveryService.recover("));
    assertFalse(activeCode.contains("activeLocalDeliveryChild"));
    assertFalse(activeCode.contains(CreateShopGuardConstants.EXTRA_ACTIVE_CHILD_RECOVERY));
  }

  @Test
  void extraActiveChildRecoveryIsKeptAsCommentedCode() throws Exception {
    String source = Files.readString(RECONCILE_SOURCE);

    assertTrue(source.contains("//       deliveryChildRecoveryService.recover("));
    assertTrue(source.contains(CreateShopGuardConstants.EXTRA_ACTIVE_CHILD_RECOVERY));
  }
}
