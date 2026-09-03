package com.thesettler_x_create.blockentity;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Guard tests for ColonyGaugeBehaviour's NBT round-trip and state machine. Can't be a live
 * behavioral test - the class extends Create's FilteringBehaviour and needs a real kinetic
 * block-entity/world context to construct, which this project's test suite deliberately never
 * bootstraps (see the *GuardTest convention used throughout src/test).
 */
class ColonyGaugeBehaviourGuardTest {

  private static String source() throws Exception {
    return Files.readString(
        Path.of("src/main/java/com/thesettler_x_create/blockentity/ColonyGaugeBehaviour.java"));
  }

  @Test
  void writeAndReadAgreeOnEveryPersistedTag() throws Exception {
    String source = source();
    int writeStart = source.indexOf("public void write(");
    int writeEnd = source.indexOf("public void read(");
    String writeBody = source.substring(writeStart, writeEnd);
    String readBody = source.substring(writeEnd);

    // Every tag written must have a matching read, or a save/load cycle silently drops that
    // field - e.g. exactly the kind of bug this feature's zero test coverage let through
    // elsewhere in the mod this session (the GaugeDimension dead-field cleanup).
    String[] tags = {
      "ColonyId",
      "ShopPos",
      "Timer",
      "PromisedUntil",
      "Satisfied",
      "PromisedSatisfied",
      "PromisedAmount",
      "FrogportAddress",
      "ManualAddress",
      "PromiseClearingInterval"
    };
    for (String tag : tags) {
      assertTrue(writeBody.contains("\"" + tag + "\""), "write() missing tag " + tag);
      assertTrue(readBody.contains("\"" + tag + "\""), "read() missing tag " + tag);
    }
  }

  @Test
  void writeSkipsInactivePanelsAndReadDeactivatesOnMissingTag() throws Exception {
    String source = source();

    // An inactive panel writes nothing at all (if (!active) return;) - and read() must treat a
    // missing/empty compound as "this panel is not active" rather than leaving stale state from
    // a previous load, otherwise a panel removed on one save could reappear as active after
    // reload if some earlier NBT happened to still be lying around under the same slot key.
    assertTrue(source.contains("if (!active) return;"));
    int readStart = source.indexOf("public void read(");
    String readBody = source.substring(readStart);
    int emptyCheckIdx = readBody.indexOf("if (tag.isEmpty()) {");
    assertTrue(emptyCheckIdx >= 0);
    String emptyBranch = readBody.substring(emptyCheckIdx, emptyCheckIdx + 80);
    assertTrue(emptyBranch.contains("active = false;"));
  }

  @Test
  void disableFullyUnlinksButResetFilterKeepsTheShopLink() throws Exception {
    String source = source();
    int disableStart = source.indexOf("public void disable() {");
    int disableEnd = source.indexOf("public void resetFilter()");
    String disableBody = source.substring(disableStart, disableEnd);

    int resetFilterStart = disableEnd;
    int resetFilterEnd = source.indexOf("public void setManualAddress(");
    String resetFilterBody = source.substring(resetFilterStart, resetFilterEnd);

    // disable() removes the panel from its shop entirely.
    assertTrue(disableBody.contains("colonyId = -1;"));
    assertTrue(disableBody.contains("shopPos = null;"));
    // resetFilter() only clears the requested item/promise, the panel must stay linked.
    assertFalse(resetFilterBody.contains("colonyId = -1;"));
    assertFalse(resetFilterBody.contains("shopPos = null;"));
  }

  @Test
  void satisfiedStockClearsAnyLingeringPromise() throws Exception {
    String source = source();
    int start = source.indexOf("private void tickStorageMonitor() {");
    int end = source.indexOf("private int getLevelInStorage()");
    String body = source.substring(start, end);

    // Once real stock satisfies the target, any outstanding "promised" request state must be
    // cleared - otherwise a stale promise could keep suppressing the powered/UI state even
    // though the gauge is now genuinely satisfied by storage alone.
    int shouldSatisfyIdx = body.indexOf("if (shouldSatisfy) {");
    assertTrue(shouldSatisfyIdx >= 0);
    String satisfyBranch = body.substring(shouldSatisfyIdx);
    assertTrue(satisfyBranch.contains("promisedSatisfied = false;"));
    assertTrue(satisfyBranch.contains("promisedAmount = 0;"));
    assertTrue(satisfyBranch.contains("promisedUntil = 0L;"));
  }

  @Test
  void deliveryReceivedRecomputesSatisfiedFromStorageRatherThanAssumingSuccess() throws Exception {
    String source = source();
    int start = source.indexOf("public void onDeliveryReceived() {");
    int end = source.indexOf("private BuildingCreateShop findBuilding()");
    String body = source.substring(start, end);

    // A delivery arriving must clear the promise and then re-derive `satisfied` from the
    // Packager's actual current stock (tickStorageMonitor) - not set satisfied=true directly,
    // since the delivered amount could be short of the full target.
    assertTrue(body.contains("promisedSatisfied = false;"));
    assertTrue(body.contains("promisedAmount = 0;"));
    assertTrue(body.contains("promisedUntil = 0L;"));
    assertTrue(body.contains("tickStorageMonitor();"));
    assertFalse(body.contains("satisfied = true;"));
  }

  @Test
  void tryRequestGuardsAgainstEveryUnsafePrecondition() throws Exception {
    String source = source();
    int start = source.indexOf("void tryRequest() {");
    int end = source.indexOf("public void onDeliveryReceived()");
    String body = source.substring(start, end);

    // Every one of these must gate the actual request call (building.requestForGauge) - dropping
    // any of them risks firing colony requests for an unlinked/unaddressed/already-satisfied
    // panel, or crashing on a null building.
    assertTrue(body.contains("if (!isLinked()) {"));
    assertTrue(body.contains("if (getFilter().isEmpty()) {"));
    assertTrue(body.contains("if (targetAddress == null || targetAddress.isBlank()) {"));
    assertTrue(body.contains("if (promisedSatisfied || satisfied) {"));
    assertTrue(body.contains("if (remaining <= 0) {"));
    assertTrue(body.contains("if (building == null) {"));
  }
}
