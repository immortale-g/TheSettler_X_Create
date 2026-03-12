package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CreateShopRequestResolverLookupFailureGraceDropGuardTest {
  @Test
  void lookupFailuresUseGraceDropInsteadOfInfiniteFailOpen() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/requestsystem/resolver/CreateShopChildReconciliationService.java"));

    assertTrue(source.contains("lookup failed -> dropped after grace"));
    assertTrue(source.contains("resolver.shouldDropMissingChild(level, childToken)"));
    assertTrue(source.contains("request.removeChild(childToken);"));
  }
}
