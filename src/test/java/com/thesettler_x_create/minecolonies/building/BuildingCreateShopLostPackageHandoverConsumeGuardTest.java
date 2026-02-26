package com.thesettler_x_create.minecolonies.building;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class BuildingCreateShopLostPackageHandoverConsumeGuardTest {
  @Test
  void handoverSkipsPackageRemovalWhenInflightCannotBeConsumed() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/building/BuildingCreateShop.java"));

    assertTrue(
        source.contains("previewAccepted = tile.planInboundAcceptedStacks(previewUnpacked)"));
    assertTrue(source.contains("skip: no inflight remainder for consumeTarget"));
    assertTrue(source.contains("if (consumeTarget > 0 && consumed <= 0)"));
  }
}
