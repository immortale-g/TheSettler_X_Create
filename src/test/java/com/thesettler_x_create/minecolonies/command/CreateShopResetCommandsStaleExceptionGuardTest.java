package com.thesettler_x_create.minecolonies.command;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Seam-audit finding s3-3: {@code isStaleRequestGraphException} used to classify a "the request
 * graph went stale underneath us" failure by lowercasing the exception message and matching literal
 * substrings (e.g. {@code "haschildren()"}) - JVM-generated helpful-NPE text, not a MineColonies
 * contract, so it could silently stop matching after an unrelated JDK or MineColonies change. Fixed
 * to classify by exception type (NPE/ISE) plus whether the failure actually originated inside
 * MineColonies' own code (top stack frame in {@code com.minecolonies.}).
 *
 * <p>The classification now lives in {@code StaleRequestGraphDetector}, shared with the shop's
 * lost-package cancel path, and is covered by its own behaviour test. What stays pinned here is
 * that the commands do not grow a second, text-based copy of it.
 */
class CreateShopResetCommandsStaleExceptionGuardTest {

  @Test
  void classifiesByExceptionTypeAndStackOriginNotMessageText() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/command/CreateShopCommandSupport.java"));

    int method = source.indexOf("static boolean isStaleRequestGraphException(");
    assertTrue(method > 0);
    String body = source.substring(method, Math.min(source.length(), method + 900));

    assertFalse(body.contains("toLowerCase"));
    assertFalse(body.contains("haschildren()"));
    assertFalse(body.contains("intvalue()"));
    assertTrue(body.contains("StaleRequestGraphDetector.isStaleRequestGraph("));
  }

  @Test
  void theSharedDetectorAsksTheStackBeforeTheMessage() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/requestsystem/resolver/StaleRequestGraphDetector.java"));

    assertTrue(source.contains("instanceof NullPointerException"));
    assertTrue(source.contains("instanceof IllegalStateException"));
    assertTrue(source.contains("getStackTrace()"));
    assertTrue(source.contains("startsWith(MINECOLONIES_PACKAGE)"));
    assertTrue(
        source.indexOf("getStackTrace()") < source.indexOf("namesKnownStaleGraphMember(failure"),
        "the message text is the fallback for a stack-trace-less exception, not the first question");
  }
}
