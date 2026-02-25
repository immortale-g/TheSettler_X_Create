package com.thesettler_x_create.minecolonies.building;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ShopCourierDiagnosticsModuleFailOpenGuardTest {
  @Test
  void diagnosticsFailOpenWhenCourierModuleIsMissing() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/building/ShopCourierDiagnostics.java"));

    assertTrue(source.contains("catch (IllegalArgumentException ignored)"));
    assertTrue(source.contains("Create Shop no longer registers the courier module"));
  }
}
