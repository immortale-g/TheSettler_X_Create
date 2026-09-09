package com.thesettler_x_create.minecolonies.building;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Seam-audit finding s2-3 (partial-fix follow-up): the production lost-package reorder path
 * (through {@code ShopLostPackageInteraction}) was already fixed to thread a real request UUID
 * through {@code restartLostPackageDetailed}, but the older 5-arg overload - reachable only from
 * the dev-gated test harness ({@code restartLostPackage}) - still passed {@code null}, producing a
 * legacy inflight entry unreachable by the UUID-based cancel path. Fixed by generating a fresh
 * random UUID instead of passing null.
 */
class BuildingCreateShopLostPackageDebugReorderUuidGuardTest {

  @Test
  void fiveArgOverloadNoLongerPassesNullRequestUuid() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/building/BuildingCreateShop.java"));

    // Single-line needle only - this file is checked out with CRLF line endings, so a needle
    // spanning an embedded "\n" (as opposed to "\r\n") never matches.
    int method = source.indexOf("LostPackageReorderResult restartLostPackageDetailed(");
    assertTrue(method > 0, "expected the 5-arg overload to be found");
    // Bounded lookahead instead of a line-ending-sensitive "end of method" marker.
    String body = source.substring(method, Math.min(source.length(), method + 700));

    assertTrue(body.contains("java.util.UUID.randomUUID()"));
    assertFalse(
        body.contains("address, requestedAt, null)"),
        "expected no null requestUuid forwarded from the 5-arg overload");
  }
}
