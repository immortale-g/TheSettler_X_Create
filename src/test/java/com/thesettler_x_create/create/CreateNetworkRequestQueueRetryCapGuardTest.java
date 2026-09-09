package com.thesettler_x_create.create;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Seam-audit finding s2-1: a failed grouped broadcast used to requeue forever with no cap and no
 * visible signal - the deficit got silently re-derived and re-queued every tick, and {@code
 * recordInflight} only fires on success, so the bookkeeping never learned the order was stuck.
 */
class CreateNetworkRequestQueueRetryCapGuardTest {

  @Test
  void requeueGivesUpAfterMaxAttemptsAndLogsLoudly() throws Exception {
    String source =
        Files.readString(
            Path.of("src/main/java/com/thesettler_x_create/create/CreateNetworkRequestQueue.java"));

    assertTrue(source.contains("private static final int MAX_RETRY_ATTEMPTS"));
    int method = source.indexOf("private static void requeueFailedBucket(");
    assertTrue(method > 0);
    String body = source.substring(method);
    assertTrue(body.contains("attempts > MAX_RETRY_ATTEMPTS"));
    // Must not be gated behind Config.DEBUG_LOGGING - that's exactly what made this invisible.
    int giveUpLog = body.indexOf("LOGGER.warn(");
    String giveUpBlock = body.substring(Math.max(0, giveUpLog - 80), giveUpLog);
    assertTrue(giveUpLog > 0);
    assertTrue(!giveUpBlock.contains("DEBUG_LOGGING"));
  }

  @Test
  void bucketTracksFailedAttempts() throws Exception {
    String source =
        Files.readString(
            Path.of("src/main/java/com/thesettler_x_create/create/QueuedRequestBucket.java"));
    assertTrue(source.contains("int failedAttempts;"));
  }
}
