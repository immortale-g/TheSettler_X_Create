package com.thesettler_x_create.minecolonies.building;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Diagnostics must only observe. The courier diagnostics used to repair citizens whose entity
 * looked missing, which changed colony state only when debug logging was on and made MineColonies
 * warn "Missing entity upon adding data to that entity!". They also reflectively called
 * getCurrentTask(), which on a courier pulls work out of the warehouse queue.
 */
class ShopCourierDiagnosticsReadOnlyGuardTest {

  private static final Path SOURCE =
      Path.of(
          "src/main/java/com/thesettler_x_create/minecolonies/building/ShopCourierDiagnostics.java");

  @Test
  void diagnosticsDoNotRepairCitizens() throws Exception {
    String source = Files.readString(SOURCE);

    assertFalse(source.contains("updateEntityIfNecessary"));
    assertFalse(source.contains("setEntity("));
    assertFalse(source.contains("spawnOrCreateCitizen"));
    assertFalse(source.contains("registerCivilian"));
    assertFalse(source.contains("EntityRepair"));
  }

  @Test
  void diagnosticsDoNotCallGetCurrentTask() throws Exception {
    String source = Files.readString(SOURCE);

    assertFalse(source.contains("\"getCurrentTask\""));
    assertFalse(source.contains(".getCurrentTask()"));
  }

  @Test
  void aCourierQueueIsReadThroughTheQueueItself() throws Exception {
    String source = Files.readString(SOURCE);
    // The replacement for the three dead getCurrentRequest* reflection calls. getTaskQueue() is
    // the same task without getCurrentTask()'s side effect of pulling warehouse work.
    assertTrue(source.contains("courier.getTaskQueue()"));
  }
}
