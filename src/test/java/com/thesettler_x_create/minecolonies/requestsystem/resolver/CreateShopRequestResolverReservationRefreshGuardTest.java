package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CreateShopRequestResolverReservationRefreshGuardTest {
  @Test
  void tickPendingRefreshesReservationsFromRackAfterRequestStateRefresh() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/requestsystem/resolver/CreateShopRequestResolver.java"));
    assertTrue(source.contains("syncReservationsFromRack("));
    assertTrue(source.contains("tickPending:reservation-refresh"));
    assertTrue(source.contains("pickup.reserve(requestId, stack.copy(), stack.getCount())"));
  }
}
