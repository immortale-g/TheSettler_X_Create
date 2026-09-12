package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CreateShopReservationKeepAliveGuardTest {
  @Test
  void tickPendingKeepsReservationsOfOpenRequestsAlive() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/requestsystem/resolver/CreateShopTickPendingService.java"));
    int collect = source.indexOf("pendingTokenCollectorService.collectPendingTokens(");
    int refresh =
        source.indexOf("refreshActiveReservations(resolver, standardManager, pendingTokens)");
    int earlyReturn = source.indexOf("if (pendingTokens.isEmpty())");
    assertTrue(collect >= 0 && refresh > collect, "keep-alive must use the collected tokens");
    assertTrue(refresh < earlyReturn, "keep-alive must run before the empty-token early return");
    assertTrue(source.contains("isTerminalRequestState(request.getState())"));
    assertTrue(source.contains("shop.getGaugeReservationRequestIds()"));
    assertTrue(source.contains("pickup.refreshReservations(activeRequestIds)"));
  }

  @Test
  void blockEntityRebasesLoadedReservationsAndUsesThePolicy() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/blockentity/CreateShopBlockEntity.java"));
    assertTrue(source.contains("rebaseLoadedReservationExpiry = !reservations.isEmpty();"));
    assertTrue(source.contains("ReservationExpiryPolicy.loadedExpiry("));
    assertTrue(source.contains("ReservationExpiryPolicy.keepAliveExpiry("));
    assertFalse(source.contains("RESERVATION_TTL"));
  }
}
