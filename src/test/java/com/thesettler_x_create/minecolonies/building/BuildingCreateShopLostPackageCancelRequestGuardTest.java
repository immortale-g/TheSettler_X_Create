package com.thesettler_x_create.minecolonies.building;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class BuildingCreateShopLostPackageCancelRequestGuardTest {
  @Test
  void cancelLostPackageCanCancelMatchingCreateShopRequests() throws Exception {
    String buildingSource =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/building/BuildingCreateShop.java"));
    String cancellerSource =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/building/ShopLostPackageRequestCanceller.java"));

    assertTrue(buildingSource.contains("cancelLostPackageRequestAndInflight("));
    assertTrue(buildingSource.contains("new ShopLostPackageRequestCanceller(this)"));
    assertTrue(cancellerSource.contains("cancelMatchingRequests("));
    assertTrue(cancellerSource.contains("matchesLostPackageRequest("));
    assertTrue(cancellerSource.contains("request.getChildren()"));
    assertTrue(cancellerSource.contains("instanceof Delivery"));
    assertTrue(cancellerSource.contains("matchesLostPackageAddress("));
    assertTrue(cancellerSource.contains("requestedAt > 0L"));
    assertTrue(
        cancellerSource.contains(
            "standard.updateRequestState(request.getId(), RequestState.CANCELLED);"));
  }
}
