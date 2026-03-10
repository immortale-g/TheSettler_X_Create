package com.thesettler_x_create.minecolonies.building;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class BuildingCreateShopLostPackageReorderStatusGuardTest {
  @Test
  void restartDetailedDistinguishesNoNetworkStock() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/building/BuildingCreateShop.java"));

    assertTrue(source.contains("restartLostPackageDetailed("));
    assertTrue(source.contains("LostPackageReorderStatus.NO_NETWORK_STOCK"));
    assertTrue(
        source.contains(
            "new LostPackageReorderResult(0, LostPackageReorderStatus.NO_NETWORK_STOCK)"));
  }
}
