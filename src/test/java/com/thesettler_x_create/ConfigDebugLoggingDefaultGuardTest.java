package com.thesettler_x_create;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Seam-audit findings s3-7/s4-4: {@code DEBUG_LOGGING} defaulted to {@code true}, so every
 * production/survival server would ship with this mod's (fairly chatty) debug logging on by
 * default. Deliberately deferred until the pre-1.0 hardening pass; flipped to default {@code false}
 * here.
 */
class ConfigDebugLoggingDefaultGuardTest {

  @Test
  void debugLoggingDefaultsToFalse() throws Exception {
    String source = Files.readString(Path.of("src/main/java/com/thesettler_x_create/Config.java"));
    assertTrue(source.contains(".define(\"debugLogging\", false);"));
  }
}
