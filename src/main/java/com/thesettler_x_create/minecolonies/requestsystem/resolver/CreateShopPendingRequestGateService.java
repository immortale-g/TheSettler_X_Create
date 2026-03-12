package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import com.minecolonies.api.colony.requestsystem.manager.IRequestManager;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.requestable.IDeliverable;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.core.colony.requestsystem.management.IStandardRequestManager;
import com.thesettler_x_create.Config;
import com.thesettler_x_create.TheSettlerXCreate;

/** Evaluates ownership/cancellation gates before pending processing mutates request state. */
final class CreateShopPendingRequestGateService {
  private final CreateShopResolverOwnership ownership;
  private final CreateShopResolverDiagnostics diagnostics;

  CreateShopPendingRequestGateService(
      CreateShopResolverOwnership ownership, CreateShopResolverDiagnostics diagnostics) {
    this.ownership = ownership;
    this.diagnostics = diagnostics;
  }

  boolean shouldSkipForPendingProcessing(
      CreateShopRequestResolver resolver,
      IRequestManager manager,
      IStandardRequestManager standardManager,
      IRequest<?> request,
      IToken<?> token) {
    if (!ownership.isRequestOwnedByLocalResolver(standardManager, request)) {
      resolver.clearPendingTokenState(token, true);
      return true;
    }
    if (resolver.isCancelledRequest(request.getId())) {
      if (request.getState()
          != com.minecolonies.api.colony.requestsystem.request.RequestState.CANCELLED) {
        resolver.clearCancelledRequest(request.getId());
      } else {
        resolver.clearPendingTokenState(request.getId(), true);
        diagnostics.logPendingReasonChange(request.getId(), "skip:cancelled");
        resolver.transitionFlow(
            manager,
            request,
            CreateShopFlowState.CANCELLED,
            "tickPending:cancelled",
            "",
            0,
            "com.thesettler_x_create.message.createshop.flow_cancelled");
        if (Config.DEBUG_LOGGING.getAsBoolean()) {
          TheSettlerXCreate.LOGGER.info(
              "[CreateShop] tickPending: {} skip (cancelled)", request.getId());
        }
        return true;
      }
    }
    if (request.getState() == com.minecolonies.api.colony.requestsystem.request.RequestState.CANCELLED) {
      resolver.clearPendingTokenState(request.getId(), true);
      resolver.transitionFlow(
          manager,
          request,
          CreateShopFlowState.CANCELLED,
          "tickPending:state-cancelled",
          "",
          0,
          "com.thesettler_x_create.message.createshop.flow_cancelled");
      if (Config.DEBUG_LOGGING.getAsBoolean()) {
        TheSettlerXCreate.LOGGER.info(
            "[CreateShop] tickPending: {} skip (state cancelled)", request.getId());
      }
      return true;
    }
    if (!(request.getRequest() instanceof IDeliverable)) {
      diagnostics.logPendingReasonChange(token, "skip:not-deliverable");
      return true;
    }
    return false;
  }
}
