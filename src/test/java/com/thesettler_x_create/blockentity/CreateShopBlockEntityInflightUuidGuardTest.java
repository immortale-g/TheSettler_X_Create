package com.thesettler_x_create.blockentity;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Seam-audit finding s1-2: every other inflight lifecycle step (cancel, clear, hand-off) was
 * migrated to UUID-first matching, but {@code getInflightRemaining} - the "is a network order
 * already on its way" check consulted before placing a new one - stayed string-only, so a citizen
 * rename/reassignment between recording and checking could cause a duplicate order.
 */
class CreateShopBlockEntityInflightUuidGuardTest {

  @Test
  void hasUuidBasedGetInflightRemainingOverload() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/blockentity/CreateShopBlockEntity.java"));

    int method =
        source.indexOf(
            "public int getInflightRemaining(ItemStack stackKey, @Nullable UUID requestUuid)");
    assertTrue(method > 0);
    // Bounded lookahead instead of hunting for a line-ending-sensitive "end of method" marker -
    // this file is checked out with CRLF line endings, which broke a "\n  }\n" search.
    String body = source.substring(method, Math.min(source.length(), method + 600));
    assertTrue(body.contains("requestUuid.equals(entry.requestUuid)"));
  }
}
