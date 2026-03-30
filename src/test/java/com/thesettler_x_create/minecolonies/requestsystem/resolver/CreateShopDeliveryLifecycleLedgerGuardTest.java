package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CreateShopDeliveryLifecycleLedgerGuardTest {
  @Test
  void ledgerEmitsDeterministicDiagnosisCodesForStuckNativeFlow() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/requestsystem/resolver/CreateShopDeliveryLifecycleLedgerService.java"));

    assertTrue(source.contains("MC_QUEUE_DEQUEUED_WITHOUT_TERMINAL"));
    assertTrue(source.contains("MC_NO_TERMINAL_CALLBACK"));
    assertTrue(source.contains("MC_HANDLER_LOST_TOKEN"));
    assertTrue(source.contains("delivery-child-ledger source="));
  }
}
