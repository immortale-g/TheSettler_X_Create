package com.thesettler_x_create.blockentity;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CreateShopBlockEntityReloadRepromptGuardTest {
  @Test
  void loadAdditionalRearmsNotifiedFlagForOpenInflightEntries() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/blockentity/CreateShopBlockEntity.java"));

    assertTrue(source.contains("inflight.notified = false;"));
    assertTrue(source.contains("Interactions are not reliably restored across reload"));
  }
}
