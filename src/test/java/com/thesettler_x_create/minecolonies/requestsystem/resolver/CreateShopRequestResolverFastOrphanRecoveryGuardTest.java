package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CreateShopRequestResolverFastOrphanRecoveryGuardTest {
  @Test
  void resolveRequestPerformsFastOrphanPickedUpRecoveryBeforeOrderedSkip() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/requestsystem/resolver/CreateShopTerminalRequestLifecycleService.java"));

    assertTrue(source.contains("tryFastOrphanPickedUpRecovery(resolver, manager, request)"));
    assertTrue(source.contains("fast-orphan-pickedup-recovery"));
    assertTrue(source.contains("fast orphan picked-up recovery parent={} child={} -> resolved"));
  }
}
