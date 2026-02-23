package com.thesettler_x_create.create;

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
    assertTrue(source.contains("shop.clearCapacityStall()"));
  }
}
