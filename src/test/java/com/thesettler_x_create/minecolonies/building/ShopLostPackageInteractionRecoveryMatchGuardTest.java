package com.thesettler_x_create.minecolonies.building;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ShopLostPackageInteractionRecoveryMatchGuardTest {
  @Test
  void lostPackageMatchingFallsBackToSameItemWhenComponentsDrift() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/building/ShopLostPackageInteraction.java"));

    assertTrue(source.contains("matchesForRecovery(content, key)"));
    assertTrue(source.contains("return ItemStack.isSameItem(candidate, key);"));
  }
}
