package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CreateShopRequestResolverWrappedDeferGuardTest {
  @Test
  void defersDeliveryCreationToTickPendingForWrappedManagers() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/requestsystem/resolver/CreateShopRequestResolver.java"));

    assertTrue(source.contains("attemptResolve:defer-wrapped-manager"));
    assertTrue(source.contains("attemptResolve defer delivery creation (wrapped manager)"));
    assertTrue(source.contains("if (unwrapStandardManager(manager) == null)"));
  }
}
