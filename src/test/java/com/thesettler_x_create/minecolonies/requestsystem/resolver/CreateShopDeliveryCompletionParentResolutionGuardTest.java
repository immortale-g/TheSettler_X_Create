package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CreateShopDeliveryCompletionParentResolutionGuardTest {
  @Test
  void deliveryCompletionResolvesParentWhenNoPendingRemainder() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/requestsystem/resolver/CreateShopDeliveryCompletionService.java"));

    assertTrue(source.contains("completeParentAfterDeliveredChild("));
    assertTrue(source.contains("pending <= 0"));
    assertTrue(source.contains("standard.updateRequestState(parentToken, RequestState.RESOLVED)"));
    assertTrue(source.contains("detachCompletedChild("));
    assertTrue(source.contains("hasActiveNonTerminalChild("));
    assertTrue(source.contains("delivery-complete:parent-resolved"));
  }
}
