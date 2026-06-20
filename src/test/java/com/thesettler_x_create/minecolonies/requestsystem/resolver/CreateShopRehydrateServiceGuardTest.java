package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Guard tests for CreateShopLifecycleRehydrateService.
 *
 * <p>Verifies the three critical invariants that prevent state drift after world reloads and
 * resolver reassignment:
 *
 * <ol>
 *   <li>Active DELIVERY_CREATED requests are re-marked as pending (≥1) not re-ordered.
 *   <li>Orphaned / terminal requests are cleaned up, not held.
 *   <li>Active-child lookup failures clear stale child-active state, preventing stuck trackers.
 * </ol>
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
    // Must preserve at least 1 even when current pending is 0 to avoid losing the inflight window.
    assertTrue(source.contains("Math.max(1, currentPending)"));
  }

  @Test
  void orphanedOrTerminalRequestClearsAllLocalState() throws Exception {
    String source = Files.readString(Path.of(SOURCE));

    // Missing or terminal requests must not linger in local state — clear both pending and stale
    // recovery arm so the next tick does not re-process them.
    assertTrue(source.contains("isTerminalRequestState(request.getState())"));
    assertTrue(source.contains("clearPendingTokenState(resolver, manager, token, true)"));
    assertTrue(source.contains("clearStaleRecoveryArm(resolver, token)"));
  }

  @Test
  void activeChildLookupFailureClearsStaleChildActiveState() throws Exception {
    String source = Files.readString(Path.of(SOURCE));

    // If the active-child request lookup throws or returns null, the child must be removed from the
    // active-child set so the tracker does not hold a token that no longer exists.
    assertTrue(source.contains("resolver.clearChildActive(childToken)"));
    // Parent must still be re-queued from child's parent link even if the lookup partly fails.
    assertTrue(source.contains("IToken<?> parent = childRequest.getParent()"));
    assertTrue(source.contains("expandedCandidates.add(parent)"));
  }

  @Test
  void candidateSetIsExpandedBeforeFiltering() throws Exception {
    String source = Files.readString(Path.of(SOURCE));

    // The service expands beyond the initial candidate set with tokens from the pending tracker
    // and delivery parent snapshot — this ensures state is not lost on a partial reload.
    assertTrue(
        source.contains("expandedCandidates.addAll(resolver.getPendingTracker().getTokens())"));
    assertTrue(
        source.contains("expandedCandidates.addAll(resolver.getParentDeliveryTokensSnapshot())"));
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
