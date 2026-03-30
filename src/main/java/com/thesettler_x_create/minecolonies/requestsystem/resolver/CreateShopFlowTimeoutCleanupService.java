package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.requestable.IDeliverable;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.core.colony.requestsystem.management.IStandardRequestManager;
import net.minecraft.world.level.Level;

/** Handles timeout-driven resolver cleanup for stale non-terminal flow records. */
final class CreateShopFlowTimeoutCleanupService {
  private final CreateShopRequestStateMutatorService requestStateMutatorService;

  CreateShopFlowTimeoutCleanupService(
      CreateShopRequestStateMutatorService requestStateMutatorService) {
    this.requestStateMutatorService = requestStateMutatorService;
  }

  void processTimedOutFlows(
      CreateShopRequestResolver resolver, IStandardRequestManager manager, Level level) {
    if (resolver == null || manager == null || level == null) {
      return;
    }
    long timeout = resolver.getInflightTimeoutTicksSafe();
    for (CreateShopFlowRecord record :
        resolver.getFlowStateMachine().collectTimedOut(level.getGameTime(), timeout)) {
      IToken<?> token = record.getRequestToken();
      boolean runtimeDeliveryWindowOpen =
          resolver.getParentDeliveryTokensSnapshot().contains(token)
              || resolver.hasDeliveriesCreated(token)
              || resolver.getPendingTracker().hasDeliveryStarted(token);
      IRequest<?> request = null;
      try {
        request = manager.getRequestHandler().getRequest(token);
      } catch (Exception ignored) {
        // Missing requests are cleaned up below.
      }
      if (request == null && runtimeDeliveryWindowOpen) {
        // Request graph lookups can be transiently stale; do not clear active parent lifecycle.
        resolver.touchFlow(token, level.getGameTime(), "timeout-cleanup:skip-runtime-active");
        continue;
      }
      if (request != null) {
        boolean terminal = CreateShopRequestResolver.isTerminalRequestState(request.getState());
        boolean deliveryWindowOpen = request.hasChildren() || runtimeDeliveryWindowOpen;
        if (!terminal && deliveryWindowOpen) {
          // Active deliveries can outlive local flow timestamps; do not clear parent lifecycle
          // here.
          resolver.touchFlow(token, level.getGameTime(), "timeout-cleanup:skip-active-delivery");
          continue;
        }
        resolver.transitionFlow(
            manager,
            request,
            CreateShopFlowState.FAILED,
            "timeout-cleanup",
            record.getStackLabel(),
            record.getAmount(),
            "com.thesettler_x_create.message.createshop.flow_timeout");
        if (request.getRequest() instanceof IDeliverable) {
          resolver.releaseReservation(manager, request);
        }
      }
      requestStateMutatorService.clearPendingTokenState(resolver, manager, token, true);
    }
  }
}
