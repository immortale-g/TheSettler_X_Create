package com.thesettler_x_create;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Debug logging is switched in one place. The flag used to be read directly at about 140 sites and
 * through four private wrappers, some of which caught a config that was not loaded yet and some of
 * which did not.
 */
class DebugLogGuardTest {

  @Test
  void onlyDebugLogReadsTheConfigFlag() throws Exception {
    List<String> hits = new ArrayList<>();
    try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
      for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
        String name = file.getFileName().toString();
        if (name.equals("Config.java") || name.equals("DebugLog.java")) {
          continue;
        }
        String source = Files.readString(file);
        if (source.contains("DEBUG_LOGGING.getAsBoolean()")
            || source.contains("boolean isDebugLoggingEnabled")
            || source.contains("boolean isDebugRequests")) {
          hits.add(name);
        }
      }
    }
    assertTrue(hits.isEmpty(), "debug flag read outside DebugLog: " + hits);
  }

  @Test
  void enabledIsSafeWithoutALoadedConfig() {
    // Unit tests run without a loaded config; this must not throw.
    DebugLog.enabled();
    DebugLog.info("[CreateShop] debug log guard {}", "ok");
  }
}
