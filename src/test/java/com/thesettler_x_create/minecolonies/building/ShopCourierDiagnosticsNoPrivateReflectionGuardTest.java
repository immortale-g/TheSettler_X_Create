package com.thesettler_x_create.minecolonies.building;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ShopCourierDiagnosticsNoPrivateReflectionGuardTest {
  @Test
  void diagnosticsAvoidsPrivateFieldMutationFallbacks() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/building/ShopCourierDiagnostics.java"));

    assertFalse(source.contains("setAccessible("));
    assertFalse(source.contains("getDeclaredFields("));
    assertFalse(source.contains("java.lang.reflect.Field"));
  }
}
