package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.requestable.IDeliverable;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.core.colony.requestsystem.management.IStandardRequestManager;
import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.world.level.Level;

/**
 * Rehydrates lifecycle state from MineColonies request graph before tick-pending mutation starts.
 *
 * <p>Since Phase 3.2 the StateMachine persists FlowState to NBT. On reload, FlowStates are loaded
 * into {@code StateMachine.pendingRestore} and applied lazily in {@code getOrCreate}. This service
 * uses that restored state as the primary source of truth. The heuristic derivation from the
 * MineColonies request graph is retained as a fallback for saves that predate Phase 3.2.
 */
final class CreateShopLifecycleRehydrateService {
  private final CreateShopRequestStateMutatorService requestStateMutatorService;
  private final CreateShopOutstandingNeededService outstandingNeededService;
  private final CreateShopResolverDiagnostics diagnostics;

  CreateShopLifecycleRehydrateService(
      CreateShopRequestStateMutatorService requestStateMutatorService,
      CreateShopOutstandingNeededService outstandingNeededService,
      CreateShopResolverDiagnostics diagnostics) {
    this.requestStateMutatorService = requestStateMutatorService;
    this.outstandingNeededService = outstandingNeededService;
    this.diagnostics = diagnostics;
  }

  Set<IToken<?>> rehydrateAndFilter(
      CreateShopRequestResolver resolver,
      IStandardRequestManager manager,
      Level level,
      Set<IToken<?>> candidates) {
    if (resolver == null
        || manager == null
        || level == null
        || candidates == null
        || candidates.isEmpty()) {
      return java.util.Collections.emptySet();
    }
    Set<IToken<?>> expandedCandidates = new LinkedHashSet<>(candidates);
    expandedCandidates.addAll(resolver.getPendingTracker().getTokens());
    Set<IToken<?>> active = new LinkedHashSet<>();
    long now = level.getGameTime();
    for (IToken<?> token : Set.copyOf(expandedCandidates)) {
      if (token == null) {
        continue;
      }
      IRequest<?> request;
      try {
        request = manager.getRequestHandler().getRequest(token);
      } catch (Exception ignored) {
        requestStateMutatorService.clearPendingTokenState(resolver, manager, token, true);
        continue;
      }
      if (request == null || CreateShopRequestResolver.isTerminalRequestState(request.getState())) {
        requestStateMutatorService.clearPendingTokenState(resolver, manager, token, true);
        continue;
      }
      if (!(request.getRequest() instanceof IDeliverable deliverable)) {
        active.add(token);
        continue;
      }

      // Fast path: trigger lazy NBT-restore and check if a FlowState was persisted for this token.
      // getOrCreate consumes the pendingRestore entry if present, giving us the restored state.
      CreateShopFlowRecord flowRecord = resolver.getFlowStateMachine().getOrCreate(token, now);
      CreateShopFlowState restoredState = flowRecord.getState();
      if (restoredState != CreateShopFlowState.NEW && !restoredState.isTerminal()) {
        // State was restored from NBT — no heuristic derivation needed.
        int currentPending = Math.max(0, resolver.getPendingTracker().getPendingCount(token));
        requestStateMutatorService.markOrderedWithPendingAtLeastOne(
            resolver, level, token, Math.max(1, currentPending));
        diagnostics.recordPendingSource(token, "rehydrate:nbt-restored");
        resolver.touchFlow(token, now, "rehydrate:nbt-restored");
        active.add(token);
        continue;
      }

      // Heuristic fallback for saves without FlowStates NBT (pre-Phase-3.2 worlds).
      if (request.hasChildren()
          || resolver.hasDeliveriesCreated(token)
          || resolver.getPendingTracker().hasDeliveryStarted(token)) {
        int currentPending = Math.max(0, resolver.getPendingTracker().getPendingCount(token));
        requestStateMutatorService.markOrderedWithPendingAtLeastOne(
            resolver, level, token, Math.max(1, currentPending));
        diagnostics.recordPendingSource(token, "rehydrate:inflight-or-children-or-started");
        resolver.touchFlow(token, now, "rehydrate:inflight-or-children-or-started");
        active.add(token);
        continue;
      }

      int derivedPending = outstandingNeededService.compute(request, deliverable, 0);
      if (derivedPending > 0) {
        int currentPending = Math.max(0, resolver.getPendingTracker().getPendingCount(token));
        int merged = Math.max(currentPending, derivedPending);
        requestStateMutatorService.markOrderedWithPending(resolver, level, token, merged);
        diagnostics.recordPendingSource(token, "rehydrate:derived-request");
        resolver.touchFlow(token, now, "rehydrate:derived-request");
        active.add(token);
      } else {
        requestStateMutatorService.clearPendingTokenState(resolver, manager, token, false);
      }
    }
    return active;
  }
}
