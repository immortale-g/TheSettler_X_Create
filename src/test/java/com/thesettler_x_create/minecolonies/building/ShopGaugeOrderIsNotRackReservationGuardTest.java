package com.thesettler_x_create.minecolonies.building;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * A gauge order is colony goods waiting in the hut buffer for a package, not rack stock spoken for
 * by a Create request. It used to be booked into the pickup reservation ledger all the same, where
 * every reader had to know which reservations stood for goods the racks never held. The amount now
 * comes off the gauge queue, and the two readers that need it ask for it by name.
 */
class ShopGaugeOrderIsNotRackReservationGuardTest {
  private static final String MAIN = "src/main/java/com/thesettler_x_create/";

  @Test
  void theGaugeQueueDoesNotBookIntoThePickupLedger() throws Exception {
    String queue = read(MAIN + "minecolonies/building/ShopGaugeQueue.java");

    assertFalse(queue.contains("pickup.reserve("));
    assertFalse(queue.contains("pickup.release("));
    assertFalse(queue.contains("pickup.consumeReservedForRequest("));
    assertTrue(queue.contains("int owedToGaugeTasks(java.util.function.Predicate<ItemStack>"));
  }

  @Test
  void aPickupLeavesStandingWhatTheShopOwesItsGauges() throws Exception {
    String policy = read(MAIN + "minecolonies/building/ShopPickupKeepPolicy.java");

    assertTrue(policy.contains("shop.getOwedToGauges(candidate ->"));
    assertTrue(
        policy.contains("ShopStockAccounting.pickupKeepAmount(rackStock, reserved, owedToGauges)"));
  }

  @Test
  void theResolverDoesNotHandOutWhatAGaugeIsWaitingFor() throws Exception {
    String planning =
        read(MAIN + "minecolonies/requestsystem/resolver/CreateShopResolverPlanning.java");

    assertTrue(planning.contains("shop.getOwedToGauges(deliverable::matches)"));
    assertTrue(planning.contains("int available = Math.max(0, total - owedToGauges);"));
  }

  @Test
  void nobodyKeepsAGaugeOrderAliveInTheReservationLedgerAnymore() throws Exception {
    String tracker = read(MAIN + "minecolonies/building/ShopInflightTracker.java");
    String tick =
        read(MAIN + "minecolonies/requestsystem/resolver/CreateShopTickPendingService.java");
    String pickup = read(MAIN + "blockentity/CreateShopBlockEntity.java");

    assertFalse(tracker.contains("gaugeRequests"));
    assertFalse(tick.contains("GaugeReservation"));
    assertFalse(pickup.contains("getReservedForExcluding"));
  }

  private static String read(String path) throws Exception {
    return Files.readString(Path.of(path));
  }
}
