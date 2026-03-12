package com.thesettler_x_create.minecolonies.building;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class BuildingCreateShopLostPackageCancelRequestGuardTest {
  @Test
  void cancelLostPackageCanCancelMatchingCreateShopRequests() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/building/BuildingCreateShop.java"));

    assertTrue(source.contains("cancelLostPackageRequestAndInflight("));
    assertTrue(source.contains("cancelMatchingLostPackageRequests(stackKey, requesterName, address, requestedAt)"));
    assertTrue(source.contains("matchesLostPackageRequest("));
    assertTrue(source.contains("request.getChildren()"));
    assertTrue(source.contains("instanceof Delivery"));
    assertTrue(source.contains("matchesLostPackageAddress("));
    assertTrue(source.contains("requestedAt > 0L"));
    assertTrue(
        source.contains("standard.updateRequestState(request.getId(), RequestState.CANCELLED);"));
  }
}
