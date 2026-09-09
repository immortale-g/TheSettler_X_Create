package com.thesettler_x_create.minecolonies.building;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Clean-code audit finding a2-1: {@code attemptCitizenEntityRepair} and its helpers used to
 * reflectively call {@code updateEntityIfNecessary}/{@code setEntity}/{@code getUUID} (on {@code
 * CitizenData}) and {@code spawnOrCreateCitizen}/{@code registerCivilian} (on the citizen manager)
 * via {@code getMethod}/{@code invoke} - all of them turned out to be public methods on
 * MineColonies' concrete {@code CitizenData}/{@code CitizenManager} classes (verified against the
 * decompiled MineColonies source), so the reflection bought nothing but let a MineColonies rename
 * silently degrade to a no-op instead of a compile error. {@code
 * ShopCourierDiagnosticsNoPrivateReflectionGuardTest} only ever asserted against {@code
 * setAccessible}/{@code getDeclaredFields}, so it never actually covered this {@code
 * getMethod}/{@code invoke} reflection - this test closes that gap.
 *
 * <p>The remaining reflection in this file ({@code tryInvoke}, {@code describeTask}, {@code
 * appendJobDetail}) is untouched - genuinely read-only diagnostic dumping of heterogeneous request
 * task/job objects, which is what this class is actually documented to do.
 */
class ShopCourierDiagnosticsEntityRepairTypedGuardTest {

  private static final Path SOURCE =
      Path.of("src/main/java/com/thesettler_x_create/minecolonies/building/ShopCourierDiagnostics.java");

  @Test
  void entityRepairMethodsUseTypedMineColoniesCallsNotReflection() throws Exception {
    String source = Files.readString(SOURCE);

    int repairStart = source.indexOf("private void attemptCitizenEntityRepair(");
    int safeEntityIdStart = source.indexOf("private int safeCitizenEntityId(");
    int safeEntityIdEnd = source.indexOf("}", source.indexOf("{", safeEntityIdStart));
    assertTrue(repairStart > 0);
    assertTrue(safeEntityIdEnd > repairStart);
    String repairFamily = source.substring(repairStart, safeEntityIdEnd);

    assertFalse(repairFamily.contains("getMethod("));
    assertFalse(repairFamily.contains(".invoke("));
    assertFalse(repairFamily.contains("NoSuchMethodException"));

    assertTrue(repairFamily.contains("cd.updateEntityIfNecessary()"));
    assertTrue(repairFamily.contains("manager.spawnOrCreateCitizen(citizen, level)"));
    assertTrue(repairFamily.contains("cm.registerCivilian(mcEntity)"));
    assertTrue(repairFamily.contains("cd.setEntity(ace)"));
  }

  @Test
  void diagnosticDumpingReflectionIsUntouched() throws Exception {
    String source = Files.readString(SOURCE);
    // tryInvoke/describeTask/appendJobDetail deliberately keep reflecting into heterogeneous
    // request task/job types for display purposes only - that is this class's actual documented
    // purpose and isn't the a2-1 finding.
    assertTrue(source.contains("private Object tryInvoke(Object target, String methodName)"));
  }
}
