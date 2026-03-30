package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CreateShopPendingRequestGateOwnershipHandoffGuardTest {
  @Test
  void keepsLocalStateDuringNonTerminalOwnershipHandoffWithActiveDeliveryWindow() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/requestsystem/resolver/CreateShopPendingRequestGateService.java"));

    assertTrue(source.contains("skip:ownership-handoff-active-delivery"));
    assertTrue(source.contains("request.hasChildren()"));
    assertTrue(source.contains("resolver.hasDeliveriesCreated(request.getId())"));
    assertTrue(source.contains("resolver.getPendingTracker().hasDeliveryStarted(request.getId())"));
    assertTrue(source.contains("!terminal && activeDeliveryWindow"));
    assertTrue(source.contains("tryReassignFromRetryingOwner(standardManager, request)"));
    assertTrue(source.contains("reassign=\""));
    assertTrue(source.contains("manager.reassignRequest(request.getId(), List.of(ownerToken));"));
  }
}
