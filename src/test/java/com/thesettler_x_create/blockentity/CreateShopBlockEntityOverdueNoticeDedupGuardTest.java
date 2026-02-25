package com.thesettler_x_create.blockentity;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CreateShopBlockEntityOverdueNoticeDedupGuardTest {
  @Test
  void consumeOverdueNoticesTriggersSinglePromptByItemAndAddress() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/blockentity/CreateShopBlockEntity.java"));

    assertTrue(source.contains("Map<String, InflightEntry> bestPerPromptKey"));
    assertTrue(source.contains("buildNoticePromptKey(entry.stackKey, entry.address)"));
    assertTrue(source.contains("selected.notified = true;"));
    assertTrue(source.contains("return java.util.List.of("));
    assertFalse(source.contains("existing.remaining += entry.remaining;"));
  }
}
