package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Seam-audit finding s1-3: the 5-minute TTL was too short to survive a slow courier leg (traffic
 * jam, sleep cycle) without expiring mid-delivery. ROADMAP.md Phase 4.3 already proposed 30
 * minutes.
 */
class CreateShopPendingDeliveryTrackerTtlGuardTest {

  @Test
  void ttlIsThirtyMinutesNotFive() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/requestsystem/resolver/CreateShopPendingDeliveryTracker.java"));

    assertTrue(source.contains("expireAfterWrite(30, TimeUnit.MINUTES)"));
    assertFalse(source.contains("expireAfterWrite(5, TimeUnit.MINUTES)"));
  }
}
