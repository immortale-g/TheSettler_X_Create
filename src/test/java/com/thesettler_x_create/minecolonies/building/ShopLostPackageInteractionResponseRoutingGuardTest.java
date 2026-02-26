package com.thesettler_x_create.minecolonies.building;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ShopLostPackageInteractionResponseRoutingGuardTest {
  @Test
  void lostPackageInquiryUsesLiteralContentForStableResponseLookup() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/building/ShopLostPackageInteraction.java"));

    assertTrue(source.contains("return Component.literal("));
    assertTrue(source.contains("Delivery seems lost for "));
    assertTrue(source.contains(". Item: "));
  }
}
