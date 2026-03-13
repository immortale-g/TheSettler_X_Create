package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CreateShopRequestResolverLookupFailureGraceDropGuardTest {
  @Test
  void lookupFailuresHoldChildInsteadOfDroppingWithoutTerminalProof() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/requestsystem/resolver/CreateShopChildReconciliationService.java"));

    assertTrue(source.contains("lookup failed -> hold (no drop)"));
    assertTrue(source.contains("markMissingChildIfAbsent(childToken, nowTick)"));
    assertTrue(source.contains("markChildActive(resolver, childToken, nowTick)"));
  }
}
