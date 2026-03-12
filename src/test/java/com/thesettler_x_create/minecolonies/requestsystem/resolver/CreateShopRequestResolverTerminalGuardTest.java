package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CreateShopRequestResolverTerminalGuardTest {
  @Test
  void tickPendingSkipsTerminalParentRequests() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/requestsystem/resolver/CreateShopRequestResolver.java"));

    assertTrue(source.contains("if (isTerminalRequestState(request.getState()))"));
    assertTrue(source.contains("skip:terminal-state"));
    assertTrue(
        source.contains("private static boolean isTerminalRequestState(RequestState state)"));
  }
}
