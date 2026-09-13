package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * One place orders from the Create network, it counts what is already coming before ordering, and
 * ordering reserves nothing: goods are reserved when they arrive. The arithmetic is tested in
 * {@code ShopStockAccountingTest} and the inflight rules in {@code InflightBookTest}.
 */
class CreateShopNetworkOrderGuardTest {
  private static final String MAIN = "src/main/java/com/thesettler_x_create/";
  private static final String RESOLVER = MAIN + "minecolonies/requestsystem/resolver/";

  private static String read(String path) throws Exception {
    return Files.readString(Path.of(path));
  }

  @Test
  void firstAttemptAndTopupShareTheOrderService() throws Exception {
    String attempt = read(RESOLVER + "CreateShopAttemptResolveService.java");
    String topup = read(RESOLVER + "CreateShopPendingTopupService.java");

    for (String source : new String[] {attempt, topup}) {
      assertTrue(source.contains("networkOrderService.orderMissing("));
      assertFalse(source.contains("stockResolver.requestFromNetwork("));
      assertFalse(source.contains("getInflightRemaining("));
    }
  }

  @Test
  void orderServiceCountsOwnAndUnownedIncomingStockFirst() throws Exception {
    String service = read(RESOLVER + "CreateShopNetworkOrderService.java");

    int own = service.indexOf("pickup.getInflightRemainingFor(requestId, deliverable::matches)");
    int claim = service.indexOf("pickup.claimFreeInflight(requestId, deliverable::matches, need)");
    int order = service.indexOf("stockResolver.requestFromNetwork(");
    assertTrue(own > 0 && claim > own && order > claim);
    assertTrue(service.contains("ShopStockAccounting.networkOrderAmount(missing, ownInflight)"));
    assertFalse(service.contains(".reserve("));
  }

  @Test
  void orderingReservesOnlyRackStock() throws Exception {
    String attempt = read(RESOLVER + "CreateShopAttemptResolveService.java");
    String topup = read(RESOLVER + "CreateShopPendingTopupService.java");

    assertFalse(topup.contains("pickup.reserve("));
    assertEquals(
        1, Pattern.compile(Pattern.quote("pickup.reserve(")).matcher(attempt).results().count());
    assertTrue(attempt.contains("for (ItemStack stack : rackPlanned) {"));
  }

  @Test
  void ordersAreTrackedWhenQueuedAndForgottenWhenAbandoned() throws Exception {
    String facade = read(MAIN + "create/CreateNetworkFacade.java");
    String queue = read(MAIN + "create/CreateNetworkRequestQueue.java");

    int queued = facade.indexOf("CreateNetworkRequestQueue.queue(");
    int recorded =
        facade.indexOf(
            "recordInflight(consolidateRequestedStacks(normalized), requesterName, requestUuid);");
    assertTrue(queued > 0 && recorded > queued);
    int broadcast = facade.indexOf("boolean broadcastQueuedRequest(");
    assertFalse(facade.substring(broadcast).contains("recordInflight("));
    assertTrue(facade.contains("pickup.cancelInflight(requestUuid, stack, stack.getCount());"));
    assertTrue(
        queue.contains(
            "failed.facade.forgetAbandonedOrder(key.requestUuid(), key.requesterName(), failed.stacks);"));
  }

  @Test
  void arrivalsAreReservedBeforeTheResolverPlans() throws Exception {
    String building = read(MAIN + "minecolonies/building/BuildingCreateShop.java");
    String tracker = read(MAIN + "minecolonies/building/ShopInflightTracker.java");

    int arrivals = building.indexOf("inflightTracker.reconcileArrivals(colony);");
    int planning = building.indexOf("resolver.tickPendingDeliveries(colony.getRequestManager());");
    assertTrue(arrivals > 0 && planning > arrivals);
    assertTrue(tracker.contains("ShopStockAccounting.arrivalReservation("));
    assertTrue(tracker.contains("pickup.getReservedForExcluding(arrival.key(), gaugeRequests)"));
    assertTrue(tracker.contains("pickup.reserve(arrival.owner(), arrival.key(), reserved);"));
  }
}
