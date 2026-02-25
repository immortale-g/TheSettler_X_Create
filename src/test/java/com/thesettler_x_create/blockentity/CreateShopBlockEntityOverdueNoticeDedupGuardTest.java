package com.thesettler_x_create.blockentity;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CreateShopBlockEntityOverdueNoticeDedupGuardTest {
  @Test
  void consumeOverdueNoticesKeepsSegmentsSeparateWithoutSumming() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/blockentity/CreateShopBlockEntity.java"));

    assertTrue(source.contains("Map<String, InflightNotice> uniqueNotices"));
    assertTrue(source.contains("buildNoticeSegmentKey("));
    assertTrue(source.contains("entry.requestedAt,"));
    assertTrue(source.contains("entry.remaining);"));
    assertTrue(source.contains("uniqueNotices.putIfAbsent("));
    assertFalse(source.contains("existing.remaining += entry.remaining;"));
  }
}
