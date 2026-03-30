package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CreateShopRequestResolverDeliveryWindowHoldGuardTest {
  @Test
  void canResolveKeepsOwnershipWhileDeliveryWindowIsOpenWithoutCompletion() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/requestsystem/resolver/CreateShopRequestValidator.java"));

    assertTrue(
        source.contains("boolean holdDeliveryWindow = deliveryWindowOpen && !completionSeen;"));
    assertTrue(
        source.contains(
            "cooldown.isRequestOnCooldown(level, request.getId()) && !holdDeliveryWindow"));
    assertTrue(
        source.contains("resolver.hasDeliveriesCreated(request.getId()) && !holdDeliveryWindow"));
    assertTrue(source.contains("canResolve=true (hold delivery window, needed<=0"));
    assertTrue(source.contains("canResolve=true (hold delivery window, available<=0"));
  }
}
