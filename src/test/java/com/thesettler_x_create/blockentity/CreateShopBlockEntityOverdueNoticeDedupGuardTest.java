package com.thesettler_x_create.blockentity;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CreateShopBlockEntityOverdueNoticeDedupGuardTest {
  @Test
  void consumeOverdueNoticesAggregatesByTupleBeforeInteractionTrigger() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/blockentity/CreateShopBlockEntity.java"));

    assertTrue(source.contains("Map<String, NoticeAggregate> grouped"));
    assertTrue(
        source.contains("buildNoticeKey(entry.stackKey, entry.requesterName, entry.address)"));
    assertTrue(source.contains("existing.remaining += entry.remaining;"));
  }
}
