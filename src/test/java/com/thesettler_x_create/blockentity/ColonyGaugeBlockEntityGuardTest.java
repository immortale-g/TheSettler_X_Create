package com.thesettler_x_create.blockentity;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ColonyGaugeBlockEntityGuardTest {

  @Test
  void addAndRemovePanelAreIdempotentAgainstAlreadyLinkedOrUnlinkedSlots() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/blockentity/ColonyGaugeBlockEntity.java"));

    // addPanel must refuse a slot that's already linked (would silently overwrite an existing
    // colony/shop link with a new gauge item's data), and removePanel must refuse an already
    // inactive slot (no-op instead of calling disable() twice).
    int addStart = source.indexOf("public boolean addPanel(");
    int addEnd = source.indexOf("public boolean removePanel(");
    String addBody = source.substring(addStart, addEnd);
    assertTrue(addBody.contains("!behaviour.isActive()"));

    int removeEnd = source.indexOf('}', addEnd) + 200;
    String removeBody = source.substring(addEnd, Math.min(removeEnd, source.length()));
    assertTrue(removeBody.contains("behaviour.isActive()"));
    assertTrue(removeBody.contains("behaviour.disable();"));
  }
}
