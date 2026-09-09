package com.thesettler_x_create.minecolonies.command;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Seam-audit finding s3-1: the test-harness/diagnostic commands ({@code run_live_test}, {@code
 * auto_test_harness*}, {@code diag_*}, {@code test_output_packaging}) create fake requests and fake
 * inflight data in a live colony, but used to be gated only by operator permission - the same gate
 * as the legitimate production maintenance commands ({@code prepare_uninstall}, {@code
 * reset_live_state}). Fixed by adding a separate, default-off {@code
 * Config.ENABLE_DEV_TEST_COMMANDS} gate on the dev/test-only commands, leaving production
 * maintenance commands untouched.
 */
class CreateShopMaintenanceCommandsDevGateGuardTest {

  @Test
  void devOnlyCommandsRequireDevTestGate() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/command/CreateShopMaintenanceCommands.java"));

    assertTrue(source.contains("requiresDevTestCommands"));
    assertTrue(source.contains("Config.ENABLE_DEV_TEST_COMMANDS.get()"));

    assertGatedBeforeExecutes(source, "run_live_test");
    assertGatedBeforeExecutes(source, "auto_test_harness");
    assertGatedBeforeExecutes(source, "auto_test_harness_full_all");
    assertGatedBeforeExecutes(source, "diag_output_block");
    assertGatedBeforeExecutes(source, "test_output_packaging");
    assertGatedBeforeExecutes(source, "diag_perma_requests");
  }

  @Test
  void productionMaintenanceCommandsStayOperatorOnly() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/command/CreateShopMaintenanceCommands.java"));

    // prepare_uninstall and reset_live_state must not require the dev-test gate - they are the
    // legitimate production maintenance path and must keep working on a normal server.
    assertNoDevGateBetween(source, "prepare_uninstall", "run_live_test");
    assertNoDevGateBetween(source, "\"reset_live_state\"", "auto_test_harness\")");
  }

  private static void assertGatedBeforeExecutes(String source, String literal) {
    int literalIndex = source.indexOf("Commands.literal(\"" + literal + "\")");
    assertTrue(literalIndex > 0, "expected literal " + literal);
    int executesIndex = source.indexOf(".executes(", literalIndex);
    assertTrue(executesIndex > literalIndex, "expected .executes( after literal " + literal);
    String between = source.substring(literalIndex, executesIndex);
    assertTrue(
        between.contains("requiresDevTestCommands"),
        "expected dev-test gate between literal and executes for " + literal);
  }

  private static void assertNoDevGateBetween(String source, String fromMarker, String toMarker) {
    int from = source.indexOf(fromMarker);
    int to = source.indexOf(toMarker, from);
    assertTrue(from > 0 && to > from, "expected markers " + fromMarker + " / " + toMarker);
    String between = source.substring(from, to);
    assertFalse(between.contains("requiresDevTestCommands"));
  }
}
