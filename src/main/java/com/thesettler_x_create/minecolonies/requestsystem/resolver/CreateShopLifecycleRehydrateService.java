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
 * <p>This avoids relying on stale local pending/cooldown maps after reloads or resolver ownership drift.
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
    if (resolver == null || manager == null || level == null || candidates == null || candidates.isEmpty()) {
      return java.util.Collections.emptySet();
    }
    Set<IToken<?>> expandedCandidates = new LinkedHashSet<>(candidates);
    expandedCandidates.addAll(resolver.getPendingTracker().getTokens());
    expandedCandidates.addAll(resolver.getParentDeliveryTokensSnapshot());
    for (IToken<?> childToken : resolver.getActiveChildTokensSnapshot()) {
      IRequest<?> childRequest = null;
      try {
        childRequest = manager.getRequestHandler().getRequest(childToken);
      } catch (Exception ignored) {
        resolver.clearChildActive(childToken);
      }
      if (childRequest == null) {
        resolver.clearChildActive(childToken);
        continue;
      }
      IToken<?> parent = childRequest.getParent();
      if (parent != null) {
        expandedCandidates.add(parent);
      }
    }
    Set<IToken<?>> active = new LinkedHashSet<>();
    for (IToken<?> token : Set.copyOf(expandedCandidates)) {
      if (token == null) {
        continue;
      }
      IRequest<?> request;
      try {
        request = manager.getRequestHandler().getRequest(token);
      } catch (Exception ignored) {
        resolver.clearPendingTokenState(token, true);
        resolver.clearTrackedChildrenForParent(manager, token);
        resolver.clearStaleRecoveryArm(token);
        continue;
      }
      if (request == null || CreateShopRequestResolver.isTerminalRequestState(request.getState())) {
        resolver.clearPendingTokenState(token, true);
        resolver.clearTrackedChildrenForParent(manager, token);
        resolver.clearStaleRecoveryArm(token);
        continue;
      }
      if (!(request.getRequest() instanceof IDeliverable deliverable)) {
        active.add(token);
        continue;
      }
      if (request.hasChildren() || resolver.hasDeliveriesCreated(token)) {
        int currentPending = Math.max(0, resolver.getPendingTracker().getPendingCount(token));
        requestStateMutatorService.markOrderedWithPendingAtLeastOne(
            resolver, level, token, Math.max(1, currentPending));
        diagnostics.recordPendingSource(token, "rehydrate:inflight-or-children");
        resolver.touchFlow(token, level.getGameTime(), "rehydrate:inflight-or-children");
        active.add(token);
        continue;
      }

      int derivedPending = outstandingNeededService.compute(request, deliverable, 0);
      if (derivedPending > 0) {
        int currentPending = Math.max(0, resolver.getPendingTracker().getPendingCount(token));
        int merged = Math.max(currentPending, derivedPending);
        requestStateMutatorService.markOrderedWithPending(resolver, level, token, merged);
        diagnostics.recordPendingSource(token, "rehydrate:derived-request");
        resolver.touchFlow(token, level.getGameTime(), "rehydrate:derived-request");
        active.add(token);
      } else {
        resolver.clearPendingTokenState(token, false);
        resolver.clearTrackedChildrenForParent(manager, token);
        resolver.clearStaleRecoveryArm(token);
      }
    }
    return active;
  }
}
