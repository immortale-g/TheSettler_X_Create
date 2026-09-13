package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * An ended request lets go of its orders on the way instead of forgetting them; forgetting made the
 * next request for the item order again while the first order was still coming.
 */
class CreateShopEndedRequestDetachesInflightGuardTest {
  private static final String RESOLVER_DIR =
      "src/main/java/com/thesettler_x_create/minecolonies/requestsystem/resolver/";

  @Test
  void releaseDetachesInsteadOfCancelling() throws Exception {
    String source =
        Files.readString(Path.of(RESOLVER_DIR + "CreateShopReservationReleaseService.java"));

    assertTrue(source.contains("pickup.release(requestId);"));
    assertTrue(source.contains("pickup.detachInflight(requestId);"));
    assertFalse(source.contains("cancelInflight"));
    assertFalse(source.contains("cancelLostPackage("));
  }

  @Test
  void aCompletedDeliveryKeepsTheParentsOrders() throws Exception {
    String source =
        Files.readString(Path.of(RESOLVER_DIR + "CreateShopDeliveryCompletionService.java"));

    assertFalse(source.contains("clearInflightByUuid"));
    assertFalse(source.contains("cancelInflightByUuid"));
  }
}
