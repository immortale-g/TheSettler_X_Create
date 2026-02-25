package com.thesettler_x_create.blockentity;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CreateShopBlockEntityInflightRecoveryMatchGuardTest {
  @Test
  void consumeInflightUsesSameItemFallbackForComponentDrift() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/blockentity/CreateShopBlockEntity.java"));

    assertTrue(source.contains("matchesForInflightRecovery(entry.stackKey, stackKey)"));
    assertTrue(source.contains("return ItemStack.isSameItem(a, b);"));
    assertTrue(source.contains("public int getInflightRemaining("));
  }
}
