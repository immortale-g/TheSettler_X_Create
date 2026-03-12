package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CreateShopResolverDiagnosticsGuardTest {
  @Test
  void parentChildrenLoggingUsesFailOpenRequestLookup() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/requestsystem/resolver/CreateShopResolverDiagnostics.java"));

    assertTrue(source.contains("handler.getRequestOrNull(parentToken)"));
    assertTrue(source.contains("handler.getRequestOrNull(child)"));
    assertTrue(source.contains("parent == null || parent.getChildren() == null"));
  }
}
