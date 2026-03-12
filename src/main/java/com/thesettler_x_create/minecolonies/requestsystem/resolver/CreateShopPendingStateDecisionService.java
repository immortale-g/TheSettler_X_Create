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

  CreateShopPendingStateDecisionService(
      CreateShopRequestStateMutatorService requestStateMutatorService,
      CreateShopWorkerAvailabilityGate workerAvailabilityGate,
      CreateShopOutstandingNeededService outstandingNeededService) {
    this.requestStateMutatorService = requestStateMutatorService;
    this.workerAvailabilityGate = workerAvailabilityGate;
    this.outstandingNeededService = outstandingNeededService;
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
    int pendingCount = Math.max(0, reservedForRequest);
    if (pendingCount <= 0) {
      pendingCount = Math.max(0, resolver.getPendingTracker().getPendingCount(request.getId()));
    }
    if (pendingCount <= 0) {
      int derivedPending = outstandingNeededService.compute(request, deliverable, reservedForRequest);
      if (derivedPending > 0) {
        requestStateMutatorService.markOrderedWithPending(
            resolver, null, request.getId(), derivedPending);
        pendingCount = derivedPending;
        resolver.getDiagnosticsForOps().recordPendingSource(request.getId(), "tickPending:derived-needed");
        if (Config.DEBUG_LOGGING.getAsBoolean()) {
          TheSettlerXCreate.LOGGER.info(
              "[CreateShop] tickPending: {} derived pending from request={} (reservedForRequest={})",
              requestIdLog,
              derivedPending,
              reservedForRequest);
        }
      }
    }
    if (pendingCount <= 0 && !onCooldown) {
      resolver
          .getDiagnosticsForOps()
          .logPendingReasonChange(
              request.getId(),
              "skip:no-pending reserved="
                  + reservedForRequest
                  + " pending="
                  + resolver.getPendingTracker().getPendingCount(request.getId()));
      if (Config.DEBUG_LOGGING.getAsBoolean()) {
        TheSettlerXCreate.LOGGER.info("[CreateShop] tickPending: {} skip (no pending)", requestIdLog);
      }
      return PendingDecision.skipped();
    }
    if (pendingCount <= 0) {
      if (onCooldown && !resolver.hasDeliveriesCreated(request.getId()) && !request.hasChildren()) {
        requestStateMutatorService.clearOrderedAndPending(resolver, request.getId());
        resolver
            .getDiagnosticsForOps()
            .logPendingReasonChange(request.getId(), "recover:stale-cooldown-no-pending");
        if (Config.DEBUG_LOGGING.getAsBoolean()) {
          TheSettlerXCreate.LOGGER.info(
              "[CreateShop] tickPending: {} cleared stale cooldown (no pending/no children)",
              requestIdLog);
        }
        return PendingDecision.skipped();
      }
      resolver
          .getDiagnosticsForOps()
          .logPendingReasonChange(
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
      resolver.touchFlowForOps(
          request.getId(), level.getGameTime(), "tickPending:worker-unavailable");
      if (workerAvailabilityGate.shouldKeepPendingState(workerWorking, pendingCount)) {
        requestStateMutatorService.markOrderedWithPending(
            resolver, level, request.getId(), pendingCount);
        resolver.getDiagnosticsForOps().recordPendingSource(request.getId(), "tickPending:worker-unavailable");
      }
      resolver.getDiagnosticsForOps().logPendingReasonChange(request.getId(), "wait:worker-not-working");
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
