package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CreateShopResolverNoReflectionGuardTest {
  @Test
  void resolverDoesNotUsePrivateFieldReflectionForManagerUnwrap() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/requestsystem/resolver/CreateShopRequestResolver.java"));

    assertFalse(source.contains("wrappedManager"));
    assertFalse(source.contains("getDeclaredField("));
    assertFalse(source.contains("setAccessible("));
  }
}
