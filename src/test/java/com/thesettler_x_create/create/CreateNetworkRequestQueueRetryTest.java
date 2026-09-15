package com.thesettler_x_create.create;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.thesettler_x_create.create.CreateLogisticsBridge.Outcome;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * The queue flushes every server tick, so a refused broadcast must not be retried on the next one.
 * Without the cooldown an unreachable network would be rescanned 20 times a second for as long as
 * it stays unreachable.
 */
class CreateNetworkRequestQueueRetryTest {

  @Test
  void transientRefusalsAreRetried() {
    assertTrue(CreateNetworkRequestQueue.shouldRetry(Outcome.PACKAGER_BUSY, 1));
    assertTrue(CreateNetworkRequestQueue.shouldRetry(Outcome.NO_PACKAGER, 1));
  }

  @Test
  void permanentRefusalsAreNotRetried() {
    assertFalse(CreateNetworkRequestQueue.shouldRetry(Outcome.ERROR, 1));
    assertFalse(CreateNetworkRequestQueue.shouldRetry(Outcome.EMPTY_ORDER, 1));
    assertFalse(CreateNetworkRequestQueue.shouldRetry(null, 1));
  }

  @Test
  void dispatchIsNeverRetried() {
    assertFalse(CreateNetworkRequestQueue.shouldRetry(Outcome.DISPATCHED, 1));
  }

  @Test
  void retriesStopAtTheAttemptLimit() {
    assertTrue(CreateNetworkRequestQueue.shouldRetry(Outcome.PACKAGER_BUSY, 2));
    assertFalse(CreateNetworkRequestQueue.shouldRetry(Outcome.PACKAGER_BUSY, 3));
    assertFalse(CreateNetworkRequestQueue.shouldRetry(Outcome.PACKAGER_BUSY, 99));
  }

  @Test
  void cooldownIsLongerThanOneTick() {
    assertTrue(CreateNetworkRequestQueue.retryCooldownFlushes() > 1);
  }

  @Test
  void flushHonoursThePolicyAndTheCooldown() throws Exception {
    String source =
        Files.readString(
            Path.of("src/main/java/com/thesettler_x_create/create/CreateNetworkRequestQueue.java"));
    assertTrue(source.contains("if (!shouldRetry(outcome, bucket.attempts)) {"));
    assertTrue(source.contains("bucket.retryAfterFlush = flushCounter + RETRY_COOLDOWN_FLUSHES;"));
    assertTrue(source.contains("if (bucket.retryAfterFlush > flushCounter) {"));
  }
}
