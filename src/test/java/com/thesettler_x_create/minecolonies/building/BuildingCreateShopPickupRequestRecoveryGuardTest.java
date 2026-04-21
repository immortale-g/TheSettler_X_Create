package com.thesettler_x_create.minecolonies.building;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class BuildingCreateShopPickupRequestRecoveryGuardTest {
  @Test
  void createShopRepairsStaleNativePickupRequestsBeforeCreatingNewOnes() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/building/BuildingCreateShop.java"));

    assertTrue(source.contains("public boolean createPickupRequest(int pickupPriority)"));
    assertTrue(source.contains("getOpenRequestsByRequestableType().get(TypeConstants.PICKUP)"));
    assertTrue(source.contains("if (!(request.getRequest() instanceof Pickup))"));
    assertTrue(source.contains("super.onRequestedRequestCancelled(standard, request);"));
    assertTrue(source.contains("super.onRequestedRequestComplete(standard, request);"));
    assertTrue(source.contains("manager.reassignRequest(token, Collections.emptyList());"));
    assertTrue(source.contains("manager.assignRequest(token);"));
    assertTrue(source.contains("resolver instanceof PickupRequestResolver"));
    assertTrue(source.contains("pruneStalePickupRequestToken(token, \"missing-request\")"));
    assertTrue(source.contains("return super.createPickupRequest(pickupPriority);"));
  }
}
