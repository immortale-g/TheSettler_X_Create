package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Two orphan recoveries trust {@code pickupConfirmedAtTick}. It must mean the items left the shop,
 * not merely that a courier holds the task.
 */
class CreateShopPickupConfirmedGuardTest {
  private static final String RESOLVER_DIR =
      "src/main/java/com/thesettler_x_create/minecolonies/requestsystem/resolver/";

  @Test
  void observedPickupConfirmsTheDelivery() throws Exception {
    String observation =
        Files.readString(Path.of(RESOLVER_DIR + "CreateShopPickupObservationService.java"));
    String ledger =
        Files.readString(Path.of(RESOLVER_DIR + "CreateShopDeliveryChildLedgerService.java"));

    assertTrue(observation.contains("resolver.observeDeliveryChildPickup("));
    assertTrue(
        observation.contains(
            "level, parentByDelivery.get(allocation.delivery()), allocation.delivery());"));
    assertTrue(ledger.contains("entry.diagnosisCode = \"COURIER_PICKUP_OBSERVED\";"));
  }

  @Test
  void courierTaskAloneDoesNotConfirmAPickup() throws Exception {
    String ledger =
        Files.readString(Path.of(RESOLVER_DIR + "CreateShopDeliveryChildLedgerService.java"));
    int taskMatch = ledger.indexOf("if (snapshot.courierTaskMatchCount() > 0");
    int confirm = ledger.indexOf("entry.diagnosisCode = \"COURIER_PICKUP_CONFIRMED\";");

    assertTrue(taskMatch >= 0 && confirm > taskMatch);
    assertTrue(
        ledger
            .substring(taskMatch, confirm)
            .contains("&& isParentReservationUsedUp(resolver, manager, parentToken)"));
  }
}
