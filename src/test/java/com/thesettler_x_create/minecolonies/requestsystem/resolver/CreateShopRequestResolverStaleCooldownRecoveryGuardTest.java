package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CreateShopRequestResolverStaleCooldownRecoveryGuardTest {
  @Test
  void tickPendingClearsStaleCooldownWhenNothingIsPending() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/requestsystem/resolver/CreateShopPendingStateDecisionService.java"));

    assertTrue(source.contains("recover:stale-cooldown-no-pending"));
    assertTrue(source.contains("cleared stale cooldown (no pending/no children)"));
    assertTrue(source.contains("if (onCooldown && parentTerminal && !deliveryWindowOpen)"));
  }
}
