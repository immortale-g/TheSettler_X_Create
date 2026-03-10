package com.thesettler_x_create.minecolonies.building;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ShopLostPackageReorderUnavailableInteractionGuardTest {
  @Test
  void interactionUsesSingleBackActionAndReturnsToLostPackageDialog() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/building/ShopLostPackageReorderUnavailableInteraction.java"));

    assertTrue(source.contains("lost_package.reorder_unavailable.response_back"));
    assertTrue(source.contains("lost_package.reorder_unavailable.answer_back"));
    assertTrue(source.contains("new ShopLostPackageInteraction("));
  }
}
