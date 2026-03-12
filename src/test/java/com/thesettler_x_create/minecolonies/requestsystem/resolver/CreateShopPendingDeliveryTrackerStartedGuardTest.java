package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CreateShopPendingDeliveryTrackerStartedGuardTest {
  @Test
  void trackerPersistsDeliveryStartedSignal() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/requestsystem/resolver/CreateShopPendingDeliveryTracker.java"));

    assertTrue(source.contains("state.setDeliveryStarted(true)"));
    assertTrue(source.contains("boolean hasDeliveryStarted(IToken<?> token)"));
    assertTrue(source.contains("!state.isDeliveryStarted()"));
  }
}
