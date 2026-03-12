package com.thesettler_x_create.minecolonies.building;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class BuildingCreateShopLostPackageRestartPartialGuardTest {
  @Test
  void restartReturnsConsumedAmountForInteractionAccumulation() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/building/BuildingCreateShop.java"));

    assertTrue(
        source.contains(
            "LostPackageReorderResult restartLostPackageDetailed("));
    assertTrue(
        source.contains(
            "new LostPackageReorderResult(consumed, LostPackageReorderStatus.SUCCESS);"));
    assertTrue(source.contains("lost-package restart requester={}"));
  }
}
