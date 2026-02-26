package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CreateShopRequestResolverDeliveryCancelMissingPickupGuardTest {
  @Test
  void cancelledDeliveryRequeuesParentWhenPickupIsTemporarilyMissing() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/requestsystem/resolver/CreateShopRequestResolver.java"));

    assertTrue(source.contains("delivery-cancel-missing-pickup"));
    assertTrue(source.contains("clearDeliveriesCreated(parentToken);"));
    assertTrue(source.contains("cooldown.markRequestOrdered(level, parentToken);"));
  }
}
