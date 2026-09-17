package com.thesettler_x_create.create;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CreateNetworkFacadeCapacityStallSignalGuardTest {
  @Test
  void normalizeSignalsAndClearsCapacityStallState() throws Exception {
    String source =
        Files.readString(
            Path.of("src/main/java/com/thesettler_x_create/create/CreateNetworkFacade.java"));
    assertTrue(source.contains("shop.noteCapacityStall("));
    // Scoped on purpose: an unrelated request that fits must not wipe a stall about another item.
    assertTrue(source.contains("shop.clearCapacityStallFor(consolidated)"));
    assertFalse(source.contains("shop.clearCapacityStall()"));
  }
}
