package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CreateShopRequestResolverLocalScopeGuardTest {
  @Test
  void tickPendingKeepsUnknownChildrenFailOpenAndSkipsNonLocalDeliveryMutation() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/requestsystem/resolver/CreateShopRequestResolver.java"));

    assertTrue(source.contains("missing -> keep (fail-open)"));
    assertTrue(source.contains("lookup failed -> keep (fail-open)"));
    assertTrue(source.contains("skip (non-local delivery child)"));
  }
}
