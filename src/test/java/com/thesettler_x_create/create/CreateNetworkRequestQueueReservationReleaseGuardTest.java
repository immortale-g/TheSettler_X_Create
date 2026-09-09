package com.thesettler_x_create.create;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Seam-audit finding s2-1 (partial-fix follow-up): giving up on a Create network request after
 * {@code MAX_RETRY_ATTEMPTS} failed broadcasts dropped the bucket without releasing the reserved
 * amount {@code CreateShopAttemptResolveService} had already spoken for via {@code
 * pickup.reserve(...)} - the requester's own log message admitted as much. The reservation had its
 * own TTL and would self-heal eventually, but not releasing it immediately left items needlessly
 * locked until that TTL expired. Fixed by releasing exactly the abandoned amount (per stack, not a
 * blanket release for the whole request) as part of giving up.
 */
class CreateNetworkRequestQueueReservationReleaseGuardTest {

  @Test
  void giveUpPathReleasesTheAbandonedReservation() throws Exception {
    String source =
        Files.readString(
            Path.of("src/main/java/com/thesettler_x_create/create/CreateNetworkRequestQueue.java"));

    int method = source.indexOf("private static void requeueFailedBucket(");
    assertTrue(method > 0);
    String body = source.substring(method, Math.min(source.length(), method + 1200));

    assertTrue(body.contains("attempts > MAX_RETRY_ATTEMPTS"));
    assertTrue(
        body.contains(
            "failed.facade.releaseAbandonedReservation(key.requestUuid, failed.stacks);"));
  }

  @Test
  void facadeExposesPerStackReservationRelease() throws Exception {
    String source =
        Files.readString(
            Path.of("src/main/java/com/thesettler_x_create/create/CreateNetworkFacade.java"));

    int method = source.indexOf("void releaseAbandonedReservation(");
    assertTrue(method > 0);
    String body = source.substring(method, Math.min(source.length(), method + 700));

    assertTrue(
        body.contains("pickup.consumeReservedForRequest(requestUuid, stack, stack.getCount());"));
  }
}
