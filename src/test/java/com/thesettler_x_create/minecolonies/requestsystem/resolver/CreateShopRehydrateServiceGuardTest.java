package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Guard tests for CreateShopLifecycleRehydrateService.
 *
 * <p>After Phase 3.5 the active-child-expansion loop and stale-recovery-arm calls have been
 * removed. The service now only expands via pendingTracker and relies on NBT-restored FlowState as
 * primary source of truth. Heuristic derivation remains as fallback for pre-3.2 saves.
 */
class CreateShopRehydrateServiceGuardTest {
  private static final String SOURCE =
      "src/main/java/com/thesettler_x_create/minecolonies/requestsystem/resolver/CreateShopLifecycleRehydrateService.java";

  @Test
  void reloadWithInflightOrChildrenMarksAtLeastOnePendingNotNewOrder() throws Exception {
    String source = Files.readString(Path.of(SOURCE));

    // Active delivery window (children / delivery created / delivery started) must mark pending
    // using markOrderedWithPendingAtLeastOne — never create a new order directly.
    assertTrue(source.contains("request.hasChildren()"));
    assertTrue(source.contains("resolver.hasDeliveriesCreated(token)"));
    assertTrue(source.contains("resolver.getPendingTracker().hasDeliveryStarted(token)"));
    assertTrue(source.contains("markOrderedWithPendingAtLeastOne("));
    assertTrue(source.contains("rehydrate:inflight-or-children-or-started"));
    assertTrue(source.contains("Math.max(1, currentPending)"));
  }

  @Test
  void orphanedOrTerminalRequestClearsLocalPendingState() throws Exception {
    String source = Files.readString(Path.of(SOURCE));

    // Missing or terminal requests must not linger — clear pending state on the next tick.
    // clearStaleRecoveryArm has been removed in Phase 3.5 (stale maps removed).
    assertTrue(source.contains("isTerminalRequestState(request.getState())"));
    assertTrue(source.contains("clearPendingTokenState(resolver, manager, token, true)"));
  }

  @Test
  void candidateSetIsExpandedFromPendingTracker() throws Exception {
    String source = Files.readString(Path.of(SOURCE));

    // Since Phase 3.5 the active-child-token expansion loop is removed (stale maps gone).
    // Candidate expansion now relies only on the pending tracker.
    assertTrue(
        source.contains("expandedCandidates.addAll(resolver.getPendingTracker().getTokens())"));
  }

  @Test
  void derivedPendingBelowOneExcludesRequestFromActiveSet() throws Exception {
    String source = Files.readString(Path.of(SOURCE));

    // If derived pending reaches 0 and there is no inflight window, the request is cleaned up
    // rather than added to the active set — this prevents phantom pending state.
    assertTrue(source.contains("int derivedPending = outstandingNeededService.compute("));
    assertTrue(source.contains("rehydrate:derived-request"));
    assertTrue(source.contains("clearPendingTokenState(resolver, manager, token, false)"));
  }
}
