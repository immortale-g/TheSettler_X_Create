package com.thesettler_x_create.item;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * {@code TranslatableContents} validates its arguments in the constructor and only accepts
 * Component, Number, Boolean or String. Passing a raw UUID crashed the client on every tooltip of a
 * tuned Network Link Tuner.
 */
class StockLinkLinkerTooltipArgumentGuardTest {
  @Test
  void storedNetworkTooltipPassesTheUuidAsAString() throws Exception {
    String source =
        Files.readString(
            Path.of("src/main/java/com/thesettler_x_create/item/StockLinkLinkerItem.java"));

    assertTrue(source.contains("tag.getUUID(FREQ_TAG).toString()));"));
    assertFalse(source.contains("tag.getUUID(FREQ_TAG)));"));
  }
}
