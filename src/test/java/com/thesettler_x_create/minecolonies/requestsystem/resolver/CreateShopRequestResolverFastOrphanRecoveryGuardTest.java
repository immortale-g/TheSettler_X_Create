package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CreateShopRequestResolverFastOrphanRecoveryGuardTest {
  @Test
  void orphanSweepFinalizesTheChildAndLetsTheSharedCompletionCheckDecide() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/requestsystem/resolver/CreateShopResolverCallbackService.java"));
    assertTrue(
        source.contains("tryFastOrphanPickedUpRecovery(resolver, manager, deliverableRequest)"));
    assertTrue(source.contains("fast-orphan-pickedup-recovery"));
    assertTrue(
        source.contains(
            "finishIfDelivered(resolver, manager, request, \"fast-orphan-pickedup-recovery\")"));
    assertTrue(
        source.contains(
            "pickup.getReservedForRequest(CreateShopRequestResolver.toRequestId(request.getId()))"));
    assertTrue(
        source.contains("fast orphan picked-up recovery skipped parent={} reservationHeld={}"));
    // resolveRequest itself no longer runs the orphan recovery.
    assertFalse(source.contains("tryFastOrphanPickedUpRecovery(resolver, manager, request)"));
  }
}
