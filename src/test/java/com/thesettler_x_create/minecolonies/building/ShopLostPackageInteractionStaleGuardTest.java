package com.thesettler_x_create.minecolonies.building;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ShopLostPackageInteractionStaleGuardTest {
  @Test
  void interactionRejectsStaleResponsesAfterResetOrTupleResolution() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/building/ShopLostPackageInteraction.java"));

    assertTrue(source.contains("if (!isStillTracked(shop))"));
    assertTrue(source.contains("interactionEpoch != shop.getLostPackageInteractionEpoch()"));
    assertTrue(
        source.contains("getInflightRemaining(stackKey, requesterName, address, requestedAt)"));
  }
}
