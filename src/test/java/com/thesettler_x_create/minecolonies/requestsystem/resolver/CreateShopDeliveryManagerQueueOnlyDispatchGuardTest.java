package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CreateShopDeliveryManagerQueueOnlyDispatchGuardTest {
  @Test
  void usesNativeAssignmentWithWarehouseCourierHandoffOnly() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/requestsystem/resolver/CreateShopDeliveryManager.java"));

    assertTrue(source.contains("assignDeliveryRequest(manager, token);"));
    assertTrue(source.contains("isQueuedInWarehouse(manager, token);"));
    assertFalse(source.contains("queue.addRequest(token)"));
    assertFalse(source.contains("job.addRequest(token, 0);"));
    assertFalse(source.contains("queue.getMutableRequestList().remove(token)"));
    assertFalse(source.contains("shop.getModule(BuildingModules.WAREHOUSE_COURIERS)"));
  }
}
