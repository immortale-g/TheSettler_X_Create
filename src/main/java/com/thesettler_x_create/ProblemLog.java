package com.thesettler_x_create;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reports something that went wrong, whatever {@code debugLogging} says.
 *
 * <p>{@link DebugLog} is a trace: it says what the mod did, step by step, and a player is meant to
 * be able to turn that off. It was carrying the failures too, which meant a shop could stop
 * delivering, lose a gauge task on world load or ignore a config value and write nothing at all to
 * the log of anyone who had followed the README and switched debug logging off. A bug the player
 * cannot see is a bug nobody can report.
 *
 * <p>Use this for a broken invariant, a swallowed exception or a feature that just went dead - not
 * for a path the mod takes on purpose. "No warehouse has the item" is not a problem; "MineColonies
 * refused to create the delivery we already reserved goods for" is.
 *
 * <p>Every message is a {@code WARN} carrying {@link #MARKER}, so a bug report can be reduced to
 * one grep. Reporting is deduplicated per key, because these sites sit in tick loops: the same
 * problem on the same request says nothing new the tenth time. The key set is bounded and, once
 * full, starts over rather than going quiet - staying silent is the failure mode this class exists
 * to remove.
 *
 * <p>Pass an exception as {@code ex.toString()}, not as the exception, unless a stack trace is
 * genuinely wanted; see {@code LoggerThrowableOverloadGuardTest} for what a stray Throwable
 * argument costs.
 */
public final class ProblemLog {
  /** In every line this class writes, so a player can find all of them at once. */
  public static final String MARKER = "[CreateShop][problem]";

  /** How many distinct problems are remembered before the dedupe starts over. */
  static final int MAX_KEYS = 256;

  private static final Set<String> REPORTED =
      Collections.newSetFromMap(new ConcurrentHashMap<>(MAX_KEYS));

  private ProblemLog() {}

  /**
   * Reports a problem once per {@code key}, at warn level, regardless of {@code debugLogging}.
   *
   * @param key what makes this occurrence distinct - a short cause name plus the token, shop or
   *     item it happened on. Two occurrences that share a key are reported once.
   * @param message an SLF4J pattern. Says what broke and what the player will notice, not only
   *     which call failed.
   */
  public static void once(String key, String message, Object... args) {
    if (!shouldReport(key)) {
      return;
    }
    TheSettlerXCreate.LOGGER.warn(MARKER + " " + message, args);
  }

  /**
   * Whether this key has not been reported yet. Package-private for the test that pins the
   * start-over behaviour.
   */
  static boolean shouldReport(String key) {
    String actualKey = key == null ? "<no-key>" : key;
    if (REPORTED.size() >= MAX_KEYS) {
      // Start over instead of falling silent. A long session with many distinct failing tokens
      // would otherwise hide every problem after the first few hundred.
      REPORTED.clear();
    }
    return REPORTED.add(actualKey);
  }

  /** Forgets what has been reported. For tests. */
  static void reset() {
    REPORTED.clear();
  }
}
