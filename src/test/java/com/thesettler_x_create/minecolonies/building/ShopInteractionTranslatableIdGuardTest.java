package com.thesettler_x_create.minecolonies.building;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ShopInteractionTranslatableIdGuardTest {
  @Test
  void capacityStallUsesTranslatableInteractionId() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/building/ShopCapacityStallInteraction.java"));

    assertTrue(
        source.contains(
            "Component.translatable(\"com.thesettler_x_create.interaction.createshop.capacity_stall.id\")"));
    assertFalse(source.contains("Component.literal(\"createshop_capacity_stall\")"));
  }

  @Test
  void lostPackageKeepsConfiguredTranslatableInteractionId() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/building/ShopLostPackageInteraction.java"));

    assertTrue(
        source.contains(
            "Component.translatable(\"com.thesettler_x_create.interaction.createshop.lost_package.id\")"));
    assertFalse(source.contains("return Component.literal(runtimeId);"));
  }
}
