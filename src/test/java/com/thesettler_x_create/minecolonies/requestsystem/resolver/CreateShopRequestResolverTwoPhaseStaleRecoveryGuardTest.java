package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CreateShopRequestResolverTwoPhaseStaleRecoveryGuardTest {
  @Test
  void staleRecoveryRequiresArmAndRecheckBeforeMutation() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/requestsystem/resolver/CreateShopRequestResolver.java"));

    assertTrue(source.contains("parentStaleRecoveryArmedAt"));
    assertTrue(source.contains("isStaleRecoveryArmed("));
    assertTrue(
        source.contains("if (!isStaleRecoveryArmed(level, standardManager, request.getId()))"));
    assertTrue(source.contains("recheck.scheduleParentChildRecheck(manager, parentToken)"));
    assertTrue(source.contains("clearStaleRecoveryArm("));
  }
}
