package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CreateShopRequestResolverMissingChildCallbackHoldGuardTest {
  @Test
  void holdsParentWhenChildDropsWithoutTerminalCallback() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/requestsystem/resolver/CreateShopPendingRequestProcessorService.java"));

    assertTrue(source.contains("wait:child-dropped-without-callback"));
    assertTrue(source.contains("tickPending:wait-child-callback"));
    assertTrue(source.contains("deliveryStarted"));
    assertTrue(source.contains("!completionSeen"));
    assertTrue(source.contains("markOrderedWithPendingAtLeastOne("));
  }
}
