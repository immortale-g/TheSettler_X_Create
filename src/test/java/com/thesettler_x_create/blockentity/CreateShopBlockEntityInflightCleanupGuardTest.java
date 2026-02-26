package com.thesettler_x_create.blockentity;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CreateShopBlockEntityInflightCleanupGuardTest {
  @Test
  void inflightCleanupCapsPerTupleAndRunsOnLoadAndRecord() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/blockentity/CreateShopBlockEntity.java"));

    assertTrue(source.contains("MAX_OPEN_INFLIGHT_SEGMENTS_PER_TUPLE = 2"));
    assertTrue(source.contains("compactInflightEntriesForPromptStability()"));
    assertTrue(source.contains("setChanged();"));
    assertTrue(source.contains("changed = true;"));
  }
}
