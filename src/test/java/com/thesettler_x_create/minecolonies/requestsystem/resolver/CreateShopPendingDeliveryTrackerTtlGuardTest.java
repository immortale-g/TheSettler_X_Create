package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Guard tests for the TTL and prune invariants of CreateShopPendingDeliveryTracker.
 *
 * <p>State objects are mutated in place, which Guava does not count as a write. With
 * expireAfterWrite a request lost its tracking mid-delivery once the TTL passed after creation, so
 * the tracker expires entries on inactivity instead. Seam-audit finding s1-3 separately raised the
 * TTL to 30 minutes so a slow courier leg (traffic jam, sleep cycle) survives. These guards verify:
 *
 * <ol>
 *   <li>The 30-minute inactivity TTL is explicitly configured.
 *   <li>pruneIfEmpty only removes entries when ALL state is empty, not on partial clears.
 * </ol>
 */
class CreateShopPendingDeliveryTrackerTtlGuardTest {
  private static final String SOURCE =
      "src/main/java/com/thesettler_x_create/minecolonies/requestsystem/resolver/CreateShopPendingDeliveryTracker.java";

  @Test
  void cacheConfiguredWithThirtyMinuteInactivityTtl() throws Exception {
    String source = Files.readString(Path.of(SOURCE));
    assertTrue(source.contains("expireAfterAccess(30, TimeUnit.MINUTES)"));
    assertFalse(source.contains("expireAfterWrite("));
    assertTrue(source.contains("CacheBuilder.newBuilder()"));
  }

  @Test
  void pruneGuardChecksAllStateFields() throws Exception {
    String source = Files.readString(Path.of(SOURCE));
    // pruneIfEmpty must check every state dimension before evicting; removing an entry while any
    // field is still active would silently drop tracking.
    assertTrue(source.contains("state.getPendingCount() <= 0"));
    assertTrue(source.contains("!state.isDeliveryStarted()"));
    assertTrue(source.contains("state.getCooldownUntil() <= 0L"));
    assertTrue(source.contains("pending.invalidate(token)"));
  }
}
