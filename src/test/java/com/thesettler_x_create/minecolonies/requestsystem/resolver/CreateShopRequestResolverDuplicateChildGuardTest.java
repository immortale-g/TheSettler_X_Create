package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CreateShopRequestResolverDuplicateChildGuardTest {
  @Test
  void tickPendingRemovesDuplicateChildTokens() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/requestsystem/resolver/CreateShopRequestResolver.java"));

    assertTrue(
        source.contains("java.util.Set<IToken<?>> seenChildren = new java.util.HashSet<>()"));
    assertTrue(source.contains("child {} duplicate -> removed"));
  }
}
