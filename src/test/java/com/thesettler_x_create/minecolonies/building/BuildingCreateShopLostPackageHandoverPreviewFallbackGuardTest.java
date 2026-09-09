package com.thesettler_x_create.minecolonies.building;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class BuildingCreateShopLostPackageHandoverPreviewFallbackGuardTest {
  @Test
  void handoverUsesPreviewUnpackFallbackAndRefreshesRackContainers() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/building/ShopLostPackageHandoverProcessor.java"));

    assertTrue(source.contains("shop.ensureRackContainers();"));
    assertTrue(
        source.contains(
            "List<ItemStack> previewUnpacked = ShopLostPackageInteraction.unpackPackage(candidate);"));
    assertTrue(source.contains("if (unpacked.isEmpty() && !previewUnpacked.isEmpty())"));
  }
}
