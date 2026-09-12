package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.requestable.IDeliverable;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.core.colony.requestsystem.management.IStandardRequestManager;
import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.world.level.Level;

/**
 * Rehydrates each candidate request's {@link CreateShopFlowState} before tick-pending mutation
 * starts, so a reload doesn't lose track of where a request was in its flow.
 *
 * <p>Since Phase 3.2 the StateMachine persists FlowState to NBT. On reload, FlowStates are loaded
 * into {@code StateMachine.pendingRestore} and applied lazily in {@code getOrCreate}. This service
 * uses that restored state as the primary source of truth. The heuristic derivation from the
 * MineColonies request graph is retained as a fallback for saves that predate Phase 3.2.
 */
final class CreateShopFlowStateRehydrateService {
  private final CreateShopRequestStateMutatorService requestStateMutatorService;
  private final CreateShopOutstandingNeededService outstandingNeededService;
  private final CreateShopResolverDiagnostics diagnostics;

  CreateShopFlowStateRehydrateService(
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
      // Fully delivered but still open: MineColonies only asks resolveRequest when a child
      // completes, so a request that missed that call (worlds saved while parents were detached, or
      // a child that vanished without callback) would otherwise never close.
      if (resolver
          .getResolverCallbackService()
          .finishIfDelivered(resolver, manager, request, "rehydrate")) {
        continue;
      }

      // Fast path: trigger lazy NBT-restore and check if a FlowState was persisted for this token.
      // getOrCreate consumes the pendingRestore entry if present, giving us the restored state.
      CreateShopFlowRecord flowRecord = resolver.getFlowStateMachine().getOrCreate(token, now);
      CreateShopFlowState restoredState = flowRecord.getState();
      // Pending counts are not forced here: the tick derives them from the request itself
      // (requested minus delivered minus reserved), and an open delivery child keeps the request
      // active on its own. Forcing a pending unit used to keep finished requests alive forever.
      if (restoredState != CreateShopFlowState.NEW && !restoredState.isTerminal()) {
        // State was restored from NBT — no heuristic derivation needed.
        resolver.touchFlow(token, now, "rehydrate:nbt-restored");
        active.add(token);
        continue;
      }

      // Heuristic fallback for saves without FlowStates NBT (pre-Phase-3.2 worlds).
      if (request.hasChildren() || resolver.getPendingTracker().hasDeliveryStarted(token)) {
        resolver.touchFlow(token, now, "rehydrate:children-or-started");
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
