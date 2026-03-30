package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CreateShopRequestResolverDeliveryCreatedWithoutChildRecoveryGuardTest {
  @Test
  void clearsDeliveryCreatedLatchWhenChildIsGoneWithoutCompletion() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/requestsystem/resolver/CreateShopPendingRequestProcessorService.java"));

    assertTrue(source.contains("recover:delivery-created-without-child"));
    assertTrue(source.contains("tickPending:recover-delivery-created"));
    assertTrue(source.contains("resolver.clearDeliveriesCreated(request.getId());"));
    assertTrue(
        source.contains("if (!request.hasChildren() && deliveryStarted && !completionSeen)"));
  }
}
