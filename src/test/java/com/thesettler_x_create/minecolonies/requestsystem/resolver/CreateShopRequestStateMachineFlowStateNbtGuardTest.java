package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Guards the NBT FlowState save/load roundtrip in CreateShopRequestStateMachine.
 *
 * <p>Phase 3.2 introduced FlowState persistence so world reloads restore the exact pre-reload state
 * without re-ordering. A double-nesting bug caused loadFlowStates to receive the already-extracted
 * FlowStates sub-tag and then look for "FlowStates" inside it again — resulting in pendingRestore
 * always staying empty. This guard prevents that regression.
 */
class CreateShopRequestStateMachineFlowStateNbtGuardTest {

  private static final String SOURCE =
      "src/main/java/com/thesettler_x_create/minecolonies/requestsystem/resolver/CreateShopRequestStateMachine.java";

  /**
   * loadFlowStates receives the already-extracted UUID→State map and must iterate it directly via
   * getAllKeys(). It must NOT attempt another getCompound() lookup inside — that was the bug.
   */
  @Test
  void loadFlowStatesIteratesTagDirectlyWithoutDoubleExtraction() throws Exception {
    String source = Files.readString(Path.of(SOURCE));

    int loadStart = source.indexOf("void loadFlowStates(");
    int loadEnd = source.indexOf("private boolean isAllowedTransition(", loadStart);
    assertTrue(loadStart >= 0, "loadFlowStates method not found");
    assertTrue(loadEnd > loadStart, "isAllowedTransition sentinel not found after loadFlowStates");

    String loadSection = source.substring(loadStart, loadEnd);

    // The caller (BuildingCreateShop) already extracts the sub-tag before calling this method.
    // A second getCompound() inside loadFlowStates would cause silent data loss on reload.
    assertFalse(
        loadSection.contains(".getCompound("),
        "loadFlowStates must not call getCompound() — the caller already extracts the sub-tag");

    // The method must iterate the given tag directly.
    assertTrue(
        loadSection.contains("getAllKeys()"),
        "loadFlowStates must iterate the tag via getAllKeys()");

    // Terminal states must be filtered during load — they are never restored.
    assertTrue(
        loadSection.contains("isTerminal()"),
        "loadFlowStates must filter terminal states via isTerminal()");
  }

  /**
   * saveFlowStates must write a nested "FlowStates" CompoundTag into the provided outer tag. This
   * is the format that BuildingCreateShop expects when it calls compound.contains("FlowStates").
   */
  @Test
  void saveFlowStatesWritesNestedSubTag() throws Exception {
    String source = Files.readString(Path.of(SOURCE));

    int saveStart = source.indexOf("void saveFlowStates(");
    int saveEnd = source.indexOf("void loadFlowStates(", saveStart);
    assertTrue(saveStart >= 0, "saveFlowStates method not found");
    assertTrue(saveEnd > saveStart, "loadFlowStates sentinel not found after saveFlowStates");

    String saveSection = source.substring(saveStart, saveEnd);

    // Outer tag receives the sub-tag under the "FlowStates" key.
    assertTrue(
        saveSection.contains("tag.put(TAG_FLOW_STATES, flowStates)"),
        "saveFlowStates must write the FlowStates sub-tag into the outer tag");

    // Ephemeral states (NEW, ELIGIBILITY_CHECK) must not be persisted — they are re-derived.
    assertTrue(
        saveSection.contains("CreateShopFlowState.NEW"),
        "saveFlowStates must skip NEW state");
    assertTrue(
        saveSection.contains("CreateShopFlowState.ELIGIBILITY_CHECK"),
        "saveFlowStates must skip ELIGIBILITY_CHECK state");

    // Terminal states must also be excluded.
    assertTrue(
        saveSection.contains("isTerminal()"),
        "saveFlowStates must filter terminal states via isTerminal()");
  }

  /**
   * pendingRestore is the in-memory buffer populated by loadFlowStates and consumed lazily in
   * getOrCreate. Without it the fast-path cannot activate and double-order protection after reload
   * is ineffective.
   */
  @Test
  void pendingRestoreFieldAndFastPathArePresent() throws Exception {
    String source = Files.readString(Path.of(SOURCE));

    assertTrue(
        source.contains("pendingRestore"),
        "pendingRestore map must exist as the NBT-restore buffer");

    // Fast-path label applied when a token is restored from NBT state.
    assertTrue(
        source.contains("\"nbt-restore\""),
        "getOrCreate must apply \"nbt-restore\" label when restoring from pendingRestore");
  }
}
