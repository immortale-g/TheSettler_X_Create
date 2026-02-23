package com.thesettler_x_create.minecolonies.building;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ShopWorkerStatusAvailabilityGuardTest {
  @Test
  void availabilityGateUsesUnavailableStatusFilter() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/building/ShopWorkerStatus.java"));
    assertTrue(source.contains("boolean hasAvailableWorker()"));
    assertTrue(source.contains("VisibleCitizenStatus.SICK"));
    assertTrue(source.contains("VisibleCitizenStatus.SLEEP"));
    assertTrue(source.contains("VisibleCitizenStatus.EAT"));
    assertTrue(source.contains("VisibleCitizenStatus.MOURNING"));
    assertTrue(source.contains("VisibleCitizenStatus.RAIDED"));
  }
}
