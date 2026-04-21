package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CreateShopDeliveryManagerRequesterBindingTest {
  @Test
  void createsDeliveryChildWithCreateShopDisplayWrapperAroundNativeWarehouseRequester()
      throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/requestsystem/resolver/CreateShopDeliveryManager.java"));

    assertTrue(
        source.contains(
            "IRequester deliveryRequester = resolveDeliveryRequester(manager, request, pickupLocation);"));
    assertTrue(source.contains("manager.createRequest(deliveryRequester, delivery)"));
    assertTrue(source.contains("new CreateShopDeliveryRequester(requester, sourceLocation)"));
    assertTrue(source.contains("isWarehouseDeliveryResolver("));
    assertTrue(source.contains("candidate instanceof AbstractWarehouseRequestResolver"));
    assertTrue(source.contains("!(candidate instanceof CreateShopRequestResolver)"));
    assertTrue(source.contains("getCreateShopDeliveryResolverBlacklist"));
    assertTrue(source.contains("assignRequest(request, blacklist)"));
  }
}
