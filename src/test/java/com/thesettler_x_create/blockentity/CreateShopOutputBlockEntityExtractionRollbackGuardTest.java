package com.thesettler_x_create.blockentity;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Seam-audit finding s1-6: a non-simulated {@code extractFromRacks} pull that came up short of the
 * requested amount (racks depleted between the preview and the real call, e.g. by something else
 * pulling concurrently) used to be treated as a full success anyway - the caller has no "partial"
 * concept, so it packaged whatever partial amount it got and marked the whole gauge task complete,
 * silently under-delivering and losing track of the shortfall forever. Fixed to make a real pull
 * all-or-nothing: every slot already pulled from during the call is rolled back before returning
 * empty when the full amount can't be reached, leaving the racks exactly as found and the gauge
 * task pending for a later retry.
 */
class CreateShopOutputBlockEntityExtractionRollbackGuardTest {
  private static final Path SOURCE =
      Path.of("src/main/java/com/thesettler_x_create/blockentity/CreateShopOutputBlockEntity.java");

  @Test
  void nonSimulatedShortfallRollsBackAndReturnsEmpty() throws Exception {
    String source = Files.readString(SOURCE);

    int method = source.indexOf("private ItemStack extractFromRacks(");
    assertTrue(method > 0);
    String body = source.substring(method, Math.min(source.length(), method + 2200));

    assertTrue(body.contains("if (!simulate && remaining > 0) {"));
    assertTrue(body.contains("rollBack(pulls);"));
    assertTrue(body.contains("return ItemStack.EMPTY;"));

    // Pulls must only be tracked (and therefore only rolled back) for real, non-simulated calls -
    // simulate mode never mutates the racks so it has nothing to undo.
    assertTrue(body.contains("simulate ? null : new java.util.ArrayList<>()"));
  }

  @Test
  void rollbackReinsertsIntoTheExactSlotEachPullCameFrom() throws Exception {
    String source = Files.readString(SOURCE);

    int method = source.indexOf("private void rollBack(");
    assertTrue(method > 0);
    String body = source.substring(method, Math.min(source.length(), method + 500));

    assertTrue(body.contains("pull.handler().insertItem(pull.slot(), pull.amount(), false)"));
  }
}
