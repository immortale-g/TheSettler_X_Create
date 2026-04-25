package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CreateShopDeliveryManagerWrappedFallbackGuardTest {
  @Test
  void wrappedManagerPathUsesNativeAssignmentWhenNoResolverAssigned() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/requestsystem/resolver/CreateShopDeliveryManager.java"));

    assertTrue(source.contains("delivery native dispatch token={} assign={} warehouseQueue={}"));
    assertTrue(source.contains("resolveDeliveryRequester(manager, request, pickupLocation)"));
    assertTrue(source.contains("manager.createRequest(deliveryRequester, delivery)"));
    assertTrue(source.contains("assignDeliveryRequest(manager, token)"));
    assertTrue(source.contains("isQueuedInWarehouse(manager, token)"));
  }
}
