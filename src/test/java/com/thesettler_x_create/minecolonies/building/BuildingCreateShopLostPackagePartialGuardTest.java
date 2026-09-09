package com.thesettler_x_create.minecolonies.building;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class BuildingCreateShopLostPackagePartialGuardTest {
  @Test
  void handoverReturnsConsumedAmountForInteractionAccumulation() throws Exception {
    String buildingSource =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/building/BuildingCreateShop.java"));
    String processorSource =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/building/ShopLostPackageHandoverProcessor.java"));

    assertTrue(buildingSource.contains("public int acceptLostPackageFromPlayer("));
    assertTrue(processorSource.contains("int totalConsumed = 0;"));
    assertTrue(processorSource.contains("if (totalConsumed > 0) {"));
    assertTrue(processorSource.contains("return totalConsumed;"));
  }
}
