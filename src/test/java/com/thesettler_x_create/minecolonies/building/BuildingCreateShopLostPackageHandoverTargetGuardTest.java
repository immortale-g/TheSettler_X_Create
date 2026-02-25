package com.thesettler_x_create.minecolonies.building;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class BuildingCreateShopLostPackageHandoverTargetGuardTest {
  @Test
  void handoverConsumesOnlyUpToTargetAcrossMultiplePackages() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/building/BuildingCreateShop.java"));

    assertTrue(source.contains("int targetAmount = Math.max(1, remaining);"));
    assertTrue(
        source.contains("slot < inventory.getContainerSize() && totalConsumed < targetAmount"));
    assertTrue(source.contains("int consumeTarget = Math.min(targetAmount - totalConsumed"));
  }
}
