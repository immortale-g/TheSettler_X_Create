package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CreateShopDeliveryReservationHoldGuardTest {
  @Test
  void keepsPickupReservationUntilTerminalDeliveryCleanup() throws Exception {
    String reconciliationSource =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/requestsystem/resolver/CreateShopChildReconciliationService.java"));
    String completionSource =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/requestsystem/resolver/CreateShopDeliveryCompletionService.java"));

    assertFalse(reconciliationSource.contains("reservation release on courier pickup"));
    assertFalse(reconciliationSource.contains("pickup.release(parentRequestId)"));
    assertTrue(reconciliationSource.contains("held by reservation="));
    assertTrue(
        completionSource.contains(
            "pickup.consumeReservedForRequest(parentRequestId, stack, stack.getCount())"));
  }
}
