package com.thesettler_x_create.minecolonies.ai;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class EntityAIWorkCreateShopStuckStatusGuardTest {
  @Test
  void workerUsesStuckStatusWhileCapacityStallIsActive() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/ai/EntityAIWorkCreateShop.java"));
    assertTrue(source.contains("building.hasCapacityStall()"));
    assertTrue(source.contains("JobStatus.STUCK"));
  }

  @Test
  void urgentWorkKeepsAiOutOfIdleOutsideDaytime() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/ai/EntityAIWorkCreateShop.java"));
    assertTrue(source.contains("hasUrgentWork()"));
    assertTrue(source.contains("shouldWorkNow()"));
    assertTrue(source.contains("currentBuilding.hasUrgentWork()"));
  }
}
