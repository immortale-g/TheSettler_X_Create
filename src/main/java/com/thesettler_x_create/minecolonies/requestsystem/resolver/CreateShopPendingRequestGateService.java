package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import com.minecolonies.api.colony.requestsystem.manager.IRequestManager;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.requestable.IDeliverable;
import com.minecolonies.api.colony.requestsystem.resolver.IRequestResolver;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.core.colony.requestsystem.management.IStandardRequestManager;
import com.thesettler_x_create.Config;
import com.thesettler_x_create.TheSettlerXCreate;
import java.util.List;

/** Evaluates ownership/cancellation gates before pending processing mutates request state. */
final class CreateShopPendingRequestGateService {
  private final CreateShopResolverOwnership ownership;
  private final CreateShopResolverDiagnostics diagnostics;
  private final CreateShopRequestStateMutatorService requestStateMutatorService;

  CreateShopPendingRequestGateService(
      CreateShopResolverOwnership ownership,
      CreateShopResolverDiagnostics diagnostics,
      CreateShopRequestStateMutatorService requestStateMutatorService) {
    this.ownership = ownership;
    this.diagnostics = diagnostics;
    this.requestStateMutatorService = requestStateMutatorService;
  }

  boolean shouldSkipForPendingProcessing(
      CreateShopRequestResolver resolver,
      IRequestManager manager,
      IStandardRequestManager standardManager,
      IRequest<?> request,
      IToken<?> token) {
    if (!ownership.isRequestOwnedByLocalResolver(standardManager, request)) {
      boolean terminal = CreateShopRequestResolver.isTerminalRequestState(request.getState());
      boolean activeDeliveryWindow =
          request.hasChildren()
              || resolver.hasDeliveriesCreated(request.getId())
              || resolver.getPendingTracker().hasDeliveryStarted(request.getId());
      if (!terminal && activeDeliveryWindow) {
        String ownershipSnapshot = buildOwnershipSnapshot(standardManager, request);
        boolean reassigned = tryReassignFromRetryingOwner(standardManager, request);
        diagnostics.logPendingReasonChange(
            request.getId(), "skip:ownership-handoff-active-delivery");
        if (Config.DEBUG_LOGGING.getAsBoolean()) {
          TheSettlerXCreate.LOGGER.info(
              "[CreateShop] tickPending: "
                  + request.getId()
                  + " skip (ownership handoff with active delivery window, reassign="
                  + (reassigned ? "ok" : "none")
                  + ", snapshot="
                  + ownershipSnapshot
                  + ")");
        }
        return true;
      }
      requestStateMutatorService.clearPendingTokenState(resolver, standardManager, token, true);
      return true;
    }
    if (resolver.isCancelledRequest(request.getId())) {
      if (request.getState()
          != com.minecolonies.api.colony.requestsystem.request.RequestState.CANCELLED) {
        resolver.clearCancelledRequest(request.getId());
      } else {
        requestStateMutatorService.clearPendingTokenState(
            resolver, standardManager, request.getId(), true);
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
    if (request.getState()
        == com.minecolonies.api.colony.requestsystem.request.RequestState.CANCELLED) {
      requestStateMutatorService.clearPendingTokenState(
          resolver, standardManager, request.getId(), true);
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

  private boolean tryReassignFromRetryingOwner(
      IStandardRequestManager manager, IRequest<?> request) {
    if (manager == null || request == null) {
      return false;
    }
    IRequestResolver<?> owner;
    try {
      owner = manager.getResolverHandler().getResolverForRequest(request);
    } catch (Exception ignored) {
      return false;
    }
    if (owner == null
        || !"StandardRetryingRequestResolver".equals(owner.getClass().getSimpleName())) {
      return false;
    }
    IToken<?> ownerToken;
    try {
      ownerToken =
          manager
              .getRequestResolverRequestAssignmentDataStore()
              .getAssignmentForValue(request.getId());
    } catch (Exception ignored) {
      return false;
    }
    if (ownerToken == null) {
      return false;
    }
    try {
      manager.reassignRequest(request.getId(), List.of(ownerToken));
      return true;
    } catch (Exception ignored) {
      return false;
    }
  }

  private String buildOwnershipSnapshot(IStandardRequestManager manager, IRequest<?> request) {
    if (manager == null || request == null) {
      return "<none>";
    }
    String ownerClass = "<unknown>";
    String ownerToken = "<none>";
    String assignedToken = "<none>";
    String childTokens = "[]";
    try {
      IRequestResolver<?> owner = manager.getResolverHandler().getResolverForRequest(request);
      if (owner != null) {
        ownerClass = owner.getClass().getSimpleName();
      }
    } catch (Exception ignored) {
      // Best effort diagnostics only.
    }
    try {
      ownerToken =
          String.valueOf(
              manager
                  .getRequestResolverRequestAssignmentDataStore()
                  .getAssignmentForValue(request.getId()));
    } catch (Exception ignored) {
      // Best effort diagnostics only.
    }
    try {
      assignedToken =
          String.valueOf(
              manager
                  .getRequestResolverRequestAssignmentDataStore()
                  .getAssignmentForValue(request.getId()));
    } catch (Exception ignored) {
      // Best effort diagnostics only.
    }
    try {
      childTokens = String.valueOf(request.getChildren());
    } catch (Exception ignored) {
      // Best effort diagnostics only.
    }
    return "ownerClass="
        + ownerClass
        + ",ownerToken="
        + ownerToken
        + ",assignedToken="
        + assignedToken
        + ",children="
        + childTokens;
  }
}
