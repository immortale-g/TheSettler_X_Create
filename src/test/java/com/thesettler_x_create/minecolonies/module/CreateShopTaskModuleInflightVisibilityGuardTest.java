package com.thesettler_x_create.minecolonies.module;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CreateShopTaskModuleInflightVisibilityGuardTest {
  @Test
  void taskTabKeepsPreDeliveryInflightVisibleButHidesFinishedParentsWithoutChildren()
      throws Exception {
    String moduleSource =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/module/CreateShopTaskModule.java"));

    assertTrue(
        moduleSource.contains(
            "if (resolver.hasParentChildCompletedSeen(token) && !request.hasChildren()) {"));
    assertTrue(
        moduleSource.contains(
            "if (request.getState() != RequestState.IN_PROGRESS && !request.hasChildren()) {"));
  }
}
