package com.thesettler_x_create;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * {@code debugLogging} is a trace switch, not a switch for whether the mod admits something broke.
 *
 * <p>It used to be both. Every failure the mod caught was logged inside {@code if
 * (DebugLog.enabled())}, so a player who had followed the README and turned the trace off got an
 * empty log out of a shop that had stopped delivering. That is also why the {@code
 * MC_QUEUE_DEQUEUED_WITHOUT_TERMINAL} marker sat on the 1.0 list as "watch for it in the log" - in
 * a release build it could not appear at all.
 *
 * <p>This test keeps the two apart: nothing routed through {@link ProblemLog} may sit behind the
 * debug switch, and the problems that were freed stay free.
 */
class ProblemsAreNotHiddenByDebugLogGuardTest {

  private static final Path MAIN = Path.of("src/main/java");

  /**
   * Problems that used to be invisible without debug logging. Each name is the {@code key} its
   * {@link ProblemLog#once} call passes. If one disappears, either it was renamed - update this
   * list - or a real problem went quiet again.
   */
  private static final List<String> KEYS_THAT_MUST_STAY_VISIBLE =
      List.of(
          "MC_QUEUE_DEQUEUED_WITHOUT_TERMINAL",
          "MC_NO_TERMINAL_CALLBACK",
          "MC_HANDLER_LOST_TOKEN",
          "config-carry-over:",
          "resolver-missing:",
          "pickup-repair-failed:",
          "courier-store-tag-missing",
          "courier-store-unreadable:",
          "gauge-task-restore-failed:",
          "gauge-cancel-failed:",
          "delivery-create-failed:",
          "delivery-link-failed:",
          "delivery-assign-failed:",
          "finish-parent-failed:",
          "reassign-failed:",
          "lost-package-cancel-failed:",
          "lost-package-cancel-fallback-failed:",
          "lost-package-dialog-remove-failed:",
          "chain-sanitize-capped:",
          "chain-origin-unreadable:",
          "provider-repair-failed:");

  @Test
  void noProblemIsReportedFromInsideADebugLoggingBlock() throws Exception {
    List<String> hits = new ArrayList<>();
    int blocksInspected = 0;
    for (Path file : javaFiles()) {
      String source = Files.readString(file);
      for (int at = source.indexOf("DebugLog.enabled()");
          at >= 0;
          at = source.indexOf("DebugLog.enabled()", at + 1)) {
        int open = source.indexOf('{', at);
        if (open < 0) {
          continue;
        }
        blocksInspected++;
        int close = endOfBlock(source, open);
        if (source.substring(open, close).contains("ProblemLog.once(")) {
          hits.add(file.getFileName() + " line " + (1 + countLines(source, at)));
        }
      }
    }
    // Without this the test passes just as happily when the scan above finds nothing at all.
    assertTrue(
        blocksInspected > 100,
        "only " + blocksInspected + " debug-logging blocks were inspected; the scan is broken");
    assertTrue(
        hits.isEmpty(),
        "a problem is reported only while debug logging is on, so a release build stays silent"
            + " about it: "
            + hits);
  }

  @Test
  void everyFreedProblemStillReportsItself() throws Exception {
    String allSources = new StringBuilder(concatenatedSources()).toString();
    List<String> missing = new ArrayList<>();
    for (String key : KEYS_THAT_MUST_STAY_VISIBLE) {
      if (!allSources.contains('"' + key)) {
        missing.add(key);
      }
    }
    assertTrue(
        missing.isEmpty(),
        "these problems no longer report themselves outside debug logging: " + missing);
  }

  @Test
  void problemsAreWarningsAndNothingElseUsesTheMarker() throws Exception {
    String problemLog = Files.readString(MAIN.resolve("com/thesettler_x_create/ProblemLog.java"));

    assertTrue(
        problemLog.contains("LOGGER.warn("),
        "ProblemLog must log at warn level; info is what a player filters out.");
    assertTrue(
        !problemLog.contains("DebugLog.enabled()"),
        "ProblemLog must not consult the debug switch - that is the whole point of it. (The class"
            + " javadoc may name DebugLog; calling it is what is forbidden.)");
  }

  private static int endOfBlock(String source, int openBrace) {
    int depth = 0;
    for (int i = openBrace; i < source.length(); i++) {
      char c = source.charAt(i);
      if (c == '{') {
        depth++;
      } else if (c == '}') {
        depth--;
        if (depth == 0) {
          return i;
        }
      }
    }
    return source.length();
  }

  private static int countLines(String source, int upTo) {
    int lines = 0;
    for (int i = 0; i < upTo; i++) {
      if (source.charAt(i) == '\n') {
        lines++;
      }
    }
    return lines;
  }

  private static List<Path> javaFiles() throws Exception {
    try (Stream<Path> files = Files.walk(MAIN)) {
      return files.filter(f -> f.toString().endsWith(".java")).toList();
    }
  }

  private static String concatenatedSources() throws Exception {
    StringBuilder all = new StringBuilder();
    for (Path file : javaFiles()) {
      all.append(Files.readString(file)).append('\n');
    }
    return all.toString();
  }
}
