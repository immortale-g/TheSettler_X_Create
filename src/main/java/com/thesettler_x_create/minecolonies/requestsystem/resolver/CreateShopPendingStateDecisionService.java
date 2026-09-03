package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.requestable.IDeliverable;
import com.thesettler_x_create.Config;
import com.thesettler_x_create.TheSettlerXCreate;
import net.minecraft.world.level.Level;

/** Decides whether pending processing can continue and normalizes pending quantity state. */
final class CreateShopPendingStateDecisionService {
  private final CreateShopRequestStateMutatorService requestStateMutatorService;
  private final CreateShopWorkerAvailabilityGate workerAvailabilityGate;
  private final CreateShopOutstandingNeededService outstandingNeededService;
  private final CreateShopResolverDiagnostics diagnostics;

  CreateShopPendingStateDecisionService(
      CreateShopRequestStateMutatorService requestStateMutatorService,
      CreateShopWorkerAvailabilityGate workerAvailabilityGate,
      CreateShopOutstandingNeededService outstandingNeededService,
      CreateShopResolverDiagnostics diagnostics) {
    this.requestStateMutatorService = requestStateMutatorService;
    this.workerAvailabilityGate = workerAvailabilityGate;
    this.outstandingNeededService = outstandingNeededService;
    this.diagnostics = diagnostics;
  }

  PendingDecision decide(
      CreateShopRequestResolver resolver,
      IRequest<?> request,
      Level level,
      IDeliverable deliverable,
      boolean onCooldown,
      int reservedForRequest,
      boolean workerWorking,
      String requestIdLog) {
    int trackedPending = Math.max(0, resolver.getPendingTracker().getPendingCount(request.getId()));
    int derivedPending = outstandingNeededService.compute(request, deliverable, reservedForRequest);
    int pendingCount = Math.max(0, Math.max(reservedForRequest, derivedPending));
    boolean inflightWindow =
        request.hasChildren()
            || resolver.hasDeliveriesCreated(request.getId())
            || resolver.getPendingTracker().hasDeliveryStarted(request.getId());
    if (inflightWindow && pendingCount > 0) {
      pendingCount = Math.max(1, Math.max(trackedPending, pendingCount));
    }
    if (pendingCount != trackedPending) {
      requestStateMutatorService.markOrderedWithPending(
          resolver, null, request.getId(), pendingCount);
      diagnostics.recordPendingSource(request.getId(), "tickPending:derived-reconcile");
      if (Config.DEBUG_LOGGING.getAsBoolean()) {
        TheSettlerXCreate.LOGGER.info(
            "[CreateShop] tickPending: {} reconcile pending tracked={} derived={} reserved={} -> {}",
            requestIdLog,
            trackedPending,
            derivedPending,
            reservedForRequest,
            pendingCount);
      }
    }
    if (pendingCount <= 0 && !onCooldown) {
      diagnostics.logPendingReasonChange(
          request.getId(),
          "skip:no-pending reserved="
              + reservedForRequest
              + " pending="
              + resolver.getPendingTracker().getPendingCount(request.getId()));
      if (Config.DEBUG_LOGGING.getAsBoolean()) {
        TheSettlerXCreate.LOGGER.info(
            "[CreateShop] tickPending: {} skip (no pending)", requestIdLog);
      }
      return PendingDecision.skipped();
    }
    if (pendingCount <= 0) {
      boolean parentTerminal = CreateShopRequestResolver.isTerminalRequestState(request.getState());
      boolean deliveryWindowOpen =
          request.hasChildren()
              || resolver.hasDeliveriesCreated(request.getId())
              || resolver.getPendingTracker().hasDeliveryStarted(request.getId());
      if (onCooldown && parentTerminal && !deliveryWindowOpen) {
        requestStateMutatorService.clearOrderedAndPending(resolver, request.getId());
        diagnostics.logPendingReasonChange(request.getId(), "recover:stale-cooldown-no-pending");
        if (Config.DEBUG_LOGGING.getAsBoolean()) {
          TheSettlerXCreate.LOGGER.info(
              "[CreateShop] tickPending: {} cleared stale cooldown (no pending/no children)",
              requestIdLog);
        }
        return PendingDecision.skipped();
      }
      diagnostics.logPendingReasonChange(
          request.getId(),
          "skip:pending-count reserved=" + reservedForRequest + " pending=" + pendingCount);
      if (Config.DEBUG_LOGGING.getAsBoolean()) {
        TheSettlerXCreate.LOGGER.info(
            "[CreateShop] tickPending: {} skip (reservedForRequest={}, pendingCount={})",
            requestIdLog,
            reservedForRequest,
            pendingCount);
      }
      return PendingDecision.skipped();
    }
    if (!workerAvailabilityGate.shouldResumePending(workerWorking, pendingCount)) {
      resolver.touchFlow(request.getId(), level.getGameTime(), "tickPending:worker-unavailable");
      if (workerAvailabilityGate.shouldKeepPendingState(workerWorking, pendingCount)) {
        requestStateMutatorService.markOrderedWithPending(
            resolver, level, request.getId(), pendingCount);
        diagnostics.recordPendingSource(request.getId(), "tickPending:worker-unavailable");
      }
      diagnostics.logPendingReasonChange(request.getId(), "wait:worker-not-working");
      if (Config.DEBUG_LOGGING.getAsBoolean()) {
        TheSettlerXCreate.LOGGER.info(
            "[CreateShop] tickPending: {} waiting (worker unavailable, pendingCount={})",
            requestIdLog,
            pendingCount);
      }
      return PendingDecision.skipped();
    }
    return PendingDecision.proceed(pendingCount);
  }

  record PendingDecision(boolean shouldSkip, int pendingCount) {
    static PendingDecision skipped() {
      return new PendingDecision(true, 0);
    }

    static PendingDecision proceed(int pendingCount) {
      return new PendingDecision(false, pendingCount);
    }
  }
}
