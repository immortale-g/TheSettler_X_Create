package com.thesettler_x_create;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * A problem has to reach the log of a player who switched debug logging off, and it has to do that
 * without filling the log, because every caller sits in a tick loop.
 */
class ProblemLogTest {

  @BeforeEach
  void clearWhatWasReported() {
    ProblemLog.reset();
  }

  @Test
  void theSameProblemIsReportedOnce() {
    assertTrue(ProblemLog.shouldReport("delivery-create-failed:abc"));
    assertFalse(ProblemLog.shouldReport("delivery-create-failed:abc"));
    assertFalse(ProblemLog.shouldReport("delivery-create-failed:abc"));
  }

  @Test
  void aDifferentProblemIsItsOwnReport() {
    assertTrue(ProblemLog.shouldReport("delivery-create-failed:abc"));
    assertTrue(ProblemLog.shouldReport("delivery-create-failed:def"));
    assertTrue(ProblemLog.shouldReport("delivery-link-failed:abc"));
  }

  @Test
  void aMissingKeyIsStillDeduplicated() {
    assertTrue(ProblemLog.shouldReport(null));
    assertFalse(ProblemLog.shouldReport(null));
  }

  @Test
  void pastTheCapItStartsOverInsteadOfGoingQuiet() {
    for (int i = 0; i < ProblemLog.MAX_KEYS; i++) {
      assertTrue(ProblemLog.shouldReport("problem-" + i), "key " + i + " should be new");
    }
    // The cap is reached, so the set starts over. The point is that something is still reported:
    // a long session with many distinct failing tokens must not silence every later problem.
    assertTrue(ProblemLog.shouldReport("problem-past-the-cap"));
    assertTrue(ProblemLog.shouldReport("problem-0"));
  }

  @Test
  void reportingDoesNotNeedALoadedConfig() {
    // Unlike DebugLog this never reads the config. Nothing here may throw without one.
    ProblemLog.once("problem-log-test", "[test] {} {}", "one", 2);
  }

  @Test
  void everyLineCarriesTheMarkerPlayersAreToldToGrepFor() {
    assertTrue(ProblemLog.MARKER.contains("problem"), ProblemLog.MARKER);
  }
}
