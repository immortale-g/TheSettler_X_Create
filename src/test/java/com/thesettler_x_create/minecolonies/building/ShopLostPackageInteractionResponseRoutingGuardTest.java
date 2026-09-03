package com.thesettler_x_create.minecolonies.building;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ShopLostPackageInteractionResponseRoutingGuardTest {
  @Test
  void lostPackageInquiryUsesTranslatableTextAndResponseRoutingStaysIndexBased() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/building/ShopLostPackageInteraction.java"));

    // Response routing is keyed by the fixed ".id" translation key and a numeric response index
    // (onServerResponseTriggered(int, ...)), not by matching the inquiry text - so the inquiry
    // itself is free to be localized.
    assertTrue(
        source.contains(
            "Component.translatable(\"com.thesettler_x_create.interaction.createshop.lost_package.id\")"));
    assertTrue(source.contains("void onServerResponseTriggered(int response,"));

    assertTrue(source.contains("return Component.translatable("));
    assertTrue(
        source.contains(
            "\"com.thesettler_x_create.interaction.createshop.lost_package.inquiry\","));
  }
}
