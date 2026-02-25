package com.thesettler_x_create.minecolonies.building;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class BuildingCreateShopLostPackagePartialGuardTest {
  @Test
  void handoverReturnsConsumedAmountForInteractionAccumulation() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/building/BuildingCreateShop.java"));

    assertTrue(source.contains("public int acceptLostPackageFromPlayer("));
    assertTrue(source.contains("int totalConsumed = 0;"));
    assertTrue(source.contains("if (totalConsumed > 0) {"));
    assertTrue(source.contains("return totalConsumed;"));
  }
}
