package com.thesettler_x_create.minecolonies.command;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Seam-audit finding s3-6: {@code findLiveTestTargetRequester} used to fall back to "any other
 * requester building" (tier 2) when no Warehouse or PostBox was found, meaning {@code
 * run_live_test}/the auto-harness could fire a fake request at an arbitrary worker building (e.g. a
 * crafter) and disturb its real request queue. Fixed by rejecting anything that isn't a Warehouse
 * or PostBox outright instead of falling back to it.
 */
class CreateShopDiagnosticCommandsTargetPickerGuardTest {

  @Test
  void targetPriorityTierRejectsEverythingButWarehouseAndPostBox() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/command/CreateShopDiagnosticCommands.java"));

    int method = source.indexOf("private static int targetPriorityTier(");
    assertTrue(method > 0);
    String body = source.substring(method, Math.min(source.length(), method + 500));

    assertFalse(body.contains("return 2;"), "expected no more fallback tier for other buildings");
    assertTrue(body.contains("return -1;"));
  }

  @Test
  void findTargetSkipsRejectedTiers() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/command/CreateShopDiagnosticCommands.java"));

    int method = source.indexOf("private static IRequester findLiveTestTargetRequester(");
    assertTrue(method > 0);
    String body = source.substring(method, Math.min(source.length(), method + 1600));

    assertTrue(body.contains("int tier = targetPriorityTier(candidate);"));
    int tierCheck = body.indexOf("if (tier < 0) {");
    assertTrue(tierCheck > 0, "expected a reject check right after computing the tier");
    assertTrue(body.indexOf("continue;", tierCheck) > tierCheck);
  }
}
