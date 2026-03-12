package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CreateShopRequestResolverInflightTopupGuardTest {
  @Test
  void tickPendingBlocksNetworkTopupWhenInflightForTupleIsStillOpen() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/requestsystem/resolver/CreateShopPendingTopupService.java"));

    assertTrue(source.contains("pickup.getInflightRemaining("));
    assertTrue(source.contains("deliverable.getResult()"));
    assertTrue(source.contains("tile.getShopAddress()"));
    assertTrue(source.contains("tickPending:wait-inflight"));
    assertTrue(source.contains("network topup blocked (inflightRemaining="));
  }
}
