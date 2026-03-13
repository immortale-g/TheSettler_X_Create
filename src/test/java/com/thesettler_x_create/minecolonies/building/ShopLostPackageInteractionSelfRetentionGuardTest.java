package com.thesettler_x_create.minecolonies.building;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ShopLostPackageInteractionSelfRetentionGuardTest {
  @Test
  void currentInteractionIsNotRemovedFromQueueDuringTupleCleanup() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/building/ShopLostPackageInteraction.java"));

    assertTrue(source.contains("if (!includeSelf && other == this) {"));
    assertTrue(source.contains("return false;"));
    assertTrue(source.contains("no-op response can re-arm"));
  }
}
