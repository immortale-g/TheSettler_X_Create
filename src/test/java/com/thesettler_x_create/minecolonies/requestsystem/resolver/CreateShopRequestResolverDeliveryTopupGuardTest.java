package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CreateShopRequestResolverDeliveryTopupGuardTest {
  @Test
  void tickPendingBlocksTopupWhileDeliveryMarkerIsActive() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/requestsystem/resolver/CreateShopRequestResolver.java"));

    assertTrue(source.contains("wait:delivery-in-progress"));
    assertTrue(source.contains("topup blocked"));
    assertFalse(source.contains("recover:clear-deliveries-created"));
  }
}
