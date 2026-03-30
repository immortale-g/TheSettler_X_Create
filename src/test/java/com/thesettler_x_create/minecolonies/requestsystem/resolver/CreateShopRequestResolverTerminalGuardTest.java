package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CreateShopRequestResolverTerminalGuardTest {
  @Test
  void tickPendingSkipsTerminalParentRequests() throws Exception {
    String resolverSource =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/requestsystem/resolver/CreateShopRequestResolver.java"));
    String processorSource =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/requestsystem/resolver/CreateShopPendingRequestProcessorService.java"));

    assertTrue(
        processorSource.contains(
            "if (CreateShopRequestResolver.isTerminalRequestState(request.getState()))"));
    assertTrue(processorSource.contains("skip:terminal-state"));
    assertTrue(
        resolverSource.contains("static boolean isTerminalRequestState(RequestState state)"));
  }
}
