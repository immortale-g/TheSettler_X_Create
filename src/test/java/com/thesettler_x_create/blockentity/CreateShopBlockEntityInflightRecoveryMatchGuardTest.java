package com.thesettler_x_create.blockentity;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CreateShopBlockEntityInflightRecoveryMatchGuardTest {
  @Test
  void consumeInflightUsesSameItemFallbackForComponentDrift() throws Exception {
    String ledgerSource =
        Files.readString(
            Path.of("src/main/java/com/thesettler_x_create/blockentity/ShopInflightLedger.java"));
    String shellSource =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/blockentity/CreateShopBlockEntity.java"));

    assertTrue(ledgerSource.contains("matchesForInflightRecovery(entry.stackKey, stackKey)"));
    assertTrue(ledgerSource.contains("return ItemStack.isSameItem(a, b);"));
    // The public API surface (delegating wrapper) must still expose this method.
    assertTrue(shellSource.contains("public int getInflightRemaining("));
  }
}
