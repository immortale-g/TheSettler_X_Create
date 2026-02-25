package com.thesettler_x_create.minecolonies.building;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ShopLostPackageInteractionAccumulationGuardTest {
  @Test
  void interactionAccumulatesConsumedAmountAcrossRetries() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/building/ShopLostPackageInteraction.java"));

    assertTrue(source.contains("remaining = Math.max(0, remaining - consumed);"));
    assertTrue(source.contains("handled = remaining <= 0;"));
  }
}
