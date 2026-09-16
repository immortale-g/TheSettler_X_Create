package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * MineColonies' retrying resolver is recognised by its API interface, not by comparing class name
 * strings: a rename or a subclass would silently turn a string match into a no-op.
 */
class CreateShopRetryingResolverTypeGuardTest {
  private static final String DIR =
      "src/main/java/com/thesettler_x_create/minecolonies/requestsystem/resolver/";

  @Test
  void retryingResolverIsMatchedByType() throws Exception {
    for (String file :
        new String[] {
          "CreateShopPendingRequestGateService.java", "CreateShopRetryingReassignService.java"
        }) {
      String source = Files.readString(Path.of(DIR + file));
      assertTrue(source.contains("instanceof IRetryingRequestResolver"), file);
      assertFalse(source.contains("\"StandardRetryingRequestResolver\""), file);
    }
  }
}
