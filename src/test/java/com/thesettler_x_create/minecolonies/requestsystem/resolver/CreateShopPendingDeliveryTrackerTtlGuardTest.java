package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Guard tests for the TTL and prune invariants of CreateShopPendingDeliveryTracker.
 *
 * <p>State objects are mutated in place, which Guava does not count as a write. With
 * expireAfterWrite a request that took longer than five minutes lost its tracking mid-delivery, so
 * the tracker expires entries on inactivity instead. These guards verify:
 *
 * <ol>
 *   <li>The 5-minute inactivity TTL is explicitly configured.
 *   <li>pruneIfEmpty only removes entries when ALL state is empty, not on partial clears.
 * </ol>
 */
class CreateShopPendingDeliveryTrackerTtlGuardTest {
  private static final String SOURCE =
      "src/main/java/com/thesettler_x_create/minecolonies/requestsystem/resolver/CreateShopPendingDeliveryTracker.java";

  @Test
  void cacheConfiguredWithFiveMinuteInactivityTtl() throws Exception {
    String source = Files.readString(Path.of(SOURCE));
    assertTrue(source.contains("expireAfterAccess(5, TimeUnit.MINUTES)"));
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
