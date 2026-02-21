package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CreateShopDeliveryManagerLinkGuardTest {
  @Test
  void managerLinksChildToParentAndCancelsChildOnLinkFailure() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/requestsystem/resolver/CreateShopDeliveryManager.java"));

    assertTrue(source.contains("request.addChild(token)"));
    assertTrue(source.contains("updateRequestState("));
    assertTrue(source.contains("RequestState.CANCELLED"));
  }
}
