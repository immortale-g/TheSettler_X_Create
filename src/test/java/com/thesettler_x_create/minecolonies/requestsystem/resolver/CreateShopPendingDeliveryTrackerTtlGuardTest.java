package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Guard tests for the TTL and prune invariants of CreateShopPendingDeliveryTracker.
 *
 * <p>The tracker uses a Guava cache with expireAfterWrite(5, MINUTES). If a request takes longer
 * than 5 minutes (e.g. courier jam), the cache evicts the entry and the resolver loses pending
 * state, causing the request to appear stuck. These guards verify:
 *
 * <ol>
 *   <li>The 5-minute TTL is explicitly configured.
 *   <li>pruneIfEmpty only removes entries when ALL state is empty — not on partial clears.
 *   <li>deliveryStarted is included in the prune guard to preserve inflight windows.
 * </ol>
 */
class CreateShopPendingDeliveryTrackerTtlGuardTest {
  private static final String SOURCE =
      "src/main/java/com/thesettler_x_create/minecolonies/requestsystem/resolver/CreateShopPendingDeliveryTracker.java";

  @Test
  void cacheConfiguredWithFiveMinuteTtl() throws Exception {
    String source = Files.readString(Path.of(SOURCE));

    // The Guava cache must use expireAfterWrite with exactly 5 minutes.
    // If this is changed the risk of stuck-pending state increases for long-running couriers.
    assertTrue(source.contains("expireAfterWrite(5, TimeUnit.MINUTES)"));
    assertTrue(source.contains("CacheBuilder.newBuilder()"));
  }

  @Test
  void pruneGuardChecksAllFourStateFields() throws Exception {
    String source = Files.readString(Path.of(SOURCE));

    // pruneIfEmpty must check every state dimension before evicting — removing an entry while any
    // field is still active would silently drop in-progress delivery tracking.
    assertTrue(source.contains("state.getPendingCount() <= 0"));
    assertTrue(source.contains("!state.isDeliveryCreated()"));
    assertTrue(source.contains("!state.isDeliveryStarted()"));
    assertTrue(source.contains("state.getCooldownUntil() <= 0L"));
    assertTrue(source.contains("pending.invalidate(token)"));
  }

  @Test
  void deliveryStartedIsPreservedWhenDeliveryCreatedIsCleared() throws Exception {
    String source = Files.readString(Path.of(SOURCE));

    // When clearDeliveryCreated is called, deliveryStarted must NOT be reset — it must persist
    // until the full delivery window closes so inflight tracking survives partial state updates.
    assertTrue(source.contains("state.setDeliveryCreated(false)"));
    // The clear method must NOT call setDeliveryStarted — only markDeliveryCreated sets it to true.
    String clearSection = source.substring(
        source.indexOf("void clearDeliveryCreated("),
        source.indexOf("void clearDeliveryCreated(") + 200);
    assertTrue(!clearSection.contains("setDeliveryStarted"));
  }
}
