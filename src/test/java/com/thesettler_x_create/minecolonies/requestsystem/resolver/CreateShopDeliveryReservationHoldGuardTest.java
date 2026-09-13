package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CreateShopDeliveryReservationHoldGuardTest {
  private static final String RESOLVER_DIR =
      "src/main/java/com/thesettler_x_create/minecolonies/requestsystem/resolver/";

  @Test
  void keepsPickupReservationUntilTheItemsLeaveTheShop() throws Exception {
    String reconciliationSource =
        Files.readString(Path.of(RESOLVER_DIR + "CreateShopChildReconciliationService.java"));
    String completionSource =
        Files.readString(Path.of(RESOLVER_DIR + "CreateShopDeliveryCompletionService.java"));
    String observationSource =
        Files.readString(Path.of(RESOLVER_DIR + "CreateShopPickupObservationService.java"));

    assertFalse(reconciliationSource.contains("reservation release on courier pickup"));
    assertFalse(reconciliationSource.contains("pickup.release(parentRequestId)"));
    assertTrue(reconciliationSource.contains("held by reservation="));
    // Consumed once, by exactly the amount a courier took out, never again on arrival.
    assertTrue(
        observationSource.contains(
            "pickup.consumeReservedForRequest(allocation.owner(), taken, allocation.amount())"));
    assertTrue(
        observationSource.contains(
            "!CreateShopDeliveryOriginMatcher.isDeliveryFromShopHut(delivery, shop)"));
    // Only deliveries from before 0.4.0, which do not start at the hut, still consume on arrival.
    int consume = completionSource.indexOf("pickup.consumeReservedForRequest(");
    int hutCheck =
        completionSource.indexOf(
            "!CreateShopDeliveryOriginMatcher.isDeliveryFromShopHut(delivery, shop)");
    assertTrue(hutCheck >= 0 && consume > hutCheck);
    assertEquals(consume, completionSource.lastIndexOf("pickup.consumeReservedForRequest("));
  }

  @Test
  void shopHutReportsTakenItemsToTheResolver() throws Exception {
    String hutSource =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/tileentity/TileEntityCreateShop.java"));
    String resolverSource =
        Files.readString(Path.of(RESOLVER_DIR + "CreateShopRequestResolver.java"));
    String observationSource =
        Files.readString(Path.of(RESOLVER_DIR + "CreateShopPickupObservationService.java"));

    assertTrue(hutSource.contains("public IItemHandler getItemHandlerCap(Direction side)"));
    assertTrue(hutSource.contains("new ObservedHutItemHandler("));
    assertTrue(hutSource.contains("onHutItemsTaken(slot, taken);"));
    assertTrue(
        hutSource.contains(
            "resolver.onHutItemsTaken(shop.getColony().getRequestManager(), taken)"));
    assertTrue(
        resolverSource.contains(
            "pickupObservationService.onHutItemsTaken(this, pickupTracker, manager, taken)"));
    // Reading the courier queue must not assign work to the courier.
    assertTrue(observationSource.contains("job.getTaskQueue()"));
    assertFalse(observationSource.contains(".getCurrentTask()"));
  }
}
