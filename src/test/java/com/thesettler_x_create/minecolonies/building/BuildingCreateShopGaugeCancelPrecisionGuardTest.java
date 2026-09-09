package com.thesettler_x_create.minecolonies.building;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Seam-audit finding s1-1: {@code cancelPendingGaugeRequests} used to fall back to a live,
 * item-only scan of every other open request when the transient tracking map didn't have an entry -
 * two Gauges requesting the same item from the same shop could then have one cancel the other's
 * still-wanted request. Fixed together with s1-4 (persist the tracking map instead of relying on a
 * fallback that can't tell two gauges apart).
 */
class BuildingCreateShopGaugeCancelPrecisionGuardTest {

  @Test
  void cancelDoesNotFallBackToItemOnlyLiveScan() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/building/BuildingCreateShop.java"));

    int method = source.indexOf("public int cancelPendingGaugeRequests(");
    // Bounded lookahead instead of a line-ending-sensitive "end of method" marker - this file is
    // checked out with CRLF line endings, which broke a "\n  }\n" search.
    String body = source.substring(method, Math.min(source.length(), method + 1400));

    // The old fallback matched by getRequestsMadeByRequester(...) + ItemStack.isSameItem alone,
    // with no address check - it must be gone from this method entirely.
    assertFalse(body.contains("getRequestsMadeByRequester"));
    // The map-based path (now the only path) must still filter by address.
    assertTrue(body.contains("task.gaugeAddress().equals(gaugeAddress)"));
  }

  @Test
  void pendingGaugeRequestsIsPersistedToNbt() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/building/BuildingCreateShop.java"));

    assertTrue(source.contains("tag.put(\"PendingGaugeRequests\", list);"));
    assertTrue(source.contains("compound.contains(\"PendingGaugeRequests\", 9)"));
    // The tracking field's own comment must no longer claim to be transient.
    int fieldComment = source.indexOf("Persisted: maps pending colony-request token");
    assertTrue(fieldComment > 0);
  }
}
