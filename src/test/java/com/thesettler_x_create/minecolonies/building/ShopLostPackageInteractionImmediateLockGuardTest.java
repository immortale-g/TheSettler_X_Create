package com.thesettler_x_create.minecolonies.building;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ShopLostPackageInteractionImmediateLockGuardTest {
  @Test
  void responseHandlerLocksInteractionBeforeProcessing() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/building/ShopLostPackageInteraction.java"));

    assertTrue(source.contains("active = false;"));
    assertTrue(source.contains("Re-arm the dialog when nothing was consumed"));
    assertTrue(source.contains("active = true;"));
  }
}
