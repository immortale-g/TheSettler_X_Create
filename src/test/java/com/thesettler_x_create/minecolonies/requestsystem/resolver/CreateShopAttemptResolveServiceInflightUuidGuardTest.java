package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Companion to CreateShopBlockEntityInflightUuidGuardTest: the call site for finding s1-2. */
class CreateShopAttemptResolveServiceInflightUuidGuardTest {

  @Test
  void checksInflightByRequestUuidBeforeFallingBackToStringMatch() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/requestsystem/resolver/CreateShopAttemptResolveService.java"));

    int uuidCall =
        source.indexOf("pickup.getInflightRemaining(deliverable.getResult(), requestId)");
    assertTrue(uuidCall > 0, "expected UUID-based getInflightRemaining call");

    String afterUuidCall = source.substring(uuidCall);
    assertTrue(
        afterUuidCall.contains("inflightRemaining <= 0"),
        "expected the UUID lookup's result to be checked before falling back");
    int stringFallback = afterUuidCall.indexOf("requesterName, tile.getShopAddress())");
    assertTrue(stringFallback > 0, "expected string-based call to remain as a fallback");
  }
}
