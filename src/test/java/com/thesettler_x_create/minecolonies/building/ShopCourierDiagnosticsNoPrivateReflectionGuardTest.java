package com.thesettler_x_create.minecolonies.building;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * The courier diagnostics must reach MineColonies through its API, not by name.
 *
 * <p>The field half of this guard has been here since the diagnostics stopped repairing citizens.
 * The method half was missing, and that blind spot let ten reflective calls survive for seven
 * months against methods MineColonies does not have ({@code getState}, {@code isWorking} on a job,
 * {@code getCurrentRequest}, {@code getEntityId}, {@code getPosition}): every one of them threw
 * into a {@code catch} and printed {@code <unknown>} into the log. A compiler cannot see a method
 * name in a string, so this test does.
 */
class ShopCourierDiagnosticsNoPrivateReflectionGuardTest {

  private static final Path SOURCE =
      Path.of(
          "src/main/java/com/thesettler_x_create/minecolonies/building/ShopCourierDiagnostics.java");

  @Test
  void diagnosticsAvoidsPrivateFieldMutationFallbacks() throws Exception {
    String source = Files.readString(SOURCE);

    assertFalse(source.contains("setAccessible("));
    assertFalse(source.contains("getDeclaredFields("));
    assertFalse(source.contains("java.lang.reflect.Field"));
  }

  @Test
  void diagnosticsCallsMineColoniesByTypeNotByName() throws Exception {
    String source = Files.readString(SOURCE);

    assertFalse(
        source.contains("getMethod("),
        "ShopCourierDiagnostics must call MineColonies through its interfaces. A reflective call"
            + " against a method that no longer exists (or never did) fails into a catch and"
            + " silently degrades the log line instead of failing the build.");
    assertFalse(source.contains("getDeclaredMethod("));
    assertFalse(source.contains("java.lang.reflect.Method"));
  }
}
