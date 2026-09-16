package com.thesettler_x_create;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Debug logging stays on by default until the 1.0.0 release, so beta and pre-release reports come
 * with traceable request and delivery flows (the README says so too). Seam-audit findings
 * s3-7/s4-4 still apply: the 1.0.0 release commit flips this to {@code false}, together with this
 * test and the README note.
 */
class ConfigDebugLoggingDefaultGuardTest {

  @Test
  void debugLoggingDefaultsToTrueUntilRelease() throws Exception {
    String source = Files.readString(Path.of("src/main/java/com/thesettler_x_create/Config.java"));
    assertTrue(source.contains(".define(\"debugLogging\", true);"));
  }
}
