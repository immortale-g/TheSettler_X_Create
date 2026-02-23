package com.thesettler_x_create.create;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CreateNetworkFacadeInboundCapacityGuardTest {
  @Test
  void normalizeStacksUsesCapacityPlannerInsteadOfOneItemInboundCheck() throws Exception {
    String source =
        Files.readString(
            Path.of("src/main/java/com/thesettler_x_create/create/CreateNetworkFacade.java"));

    assertTrue(source.contains("planInboundAcceptedStacks"));
    assertFalse(source.contains("shop.canAcceptInbound(requestStack)"));
  }
}
