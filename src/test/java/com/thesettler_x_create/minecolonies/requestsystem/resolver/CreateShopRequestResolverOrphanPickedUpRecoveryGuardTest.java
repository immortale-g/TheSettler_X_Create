package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CreateShopRequestResolverOrphanPickedUpRecoveryGuardTest {
  @Test
  void orphanPickedUpChildRecoveryResolvesParentInsteadOfStalling() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/requestsystem/resolver/CreateShopPendingRequestProcessorService.java"));

    assertTrue(source.contains("findPickedUpOrphanChildForParent"));
    assertTrue(source.contains("recover:orphan-pickedup-child"));
    assertTrue(source.contains("orphan-pickedup-recovery"));
    assertTrue(source.contains("updateRequestState(request.getId(), RequestState.RESOLVED)"));
  }
}
