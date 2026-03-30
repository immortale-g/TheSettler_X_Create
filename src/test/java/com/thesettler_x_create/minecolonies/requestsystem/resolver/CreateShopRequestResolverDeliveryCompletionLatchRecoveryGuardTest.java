package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CreateShopRequestResolverDeliveryCompletionLatchRecoveryGuardTest {
  @Test
  void clearsDeliveryCreatedLatchAfterCompletionSeenToAllowNextTopupCycle() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/requestsystem/resolver/CreateShopPendingRequestProcessorService.java"));

    assertTrue(source.contains("recover:delivery-created-after-completion"));
    assertTrue(source.contains("tickPending:recover-delivery-after-completion"));
    assertTrue(source.contains("if (completionSeen)"));
    assertTrue(source.contains("resolver.clearDeliveriesCreated(request.getId());"));
  }
}
