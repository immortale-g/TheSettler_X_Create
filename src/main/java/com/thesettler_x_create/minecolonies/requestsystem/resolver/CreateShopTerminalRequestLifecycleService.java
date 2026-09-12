package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import com.minecolonies.api.colony.requestsystem.manager.IRequestManager;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.request.RequestState;
import com.minecolonies.api.colony.requestsystem.requestable.IDeliverable;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.core.colony.requestsystem.management.IStandardRequestManager;
import com.thesettler_x_create.Config;
import com.thesettler_x_create.TheSettlerXCreate;
import com.thesettler_x_create.blockentity.CreateShopBlockEntity;
import com.thesettler_x_create.minecolonies.building.BuildingCreateShop;
import java.util.Collection;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

/** Handles terminal request lifecycle cleanup and resolver completion/cancel transitions. */
final class CreateShopTerminalRequestLifecycleService {
  private final CreateShopRequestStateMutatorService requestStateMutatorService;
  private final CreateShopOutstandingNeededService outstandingNeededService;
  private final CreateShopResolverDiagnostics diagnostics;

  CreateShopTerminalRequestLifecycleService(
      CreateShopRequestStateMutatorService requestStateMutatorService,
      CreateShopOutstandingNeededService outstandingNeededService,
      CreateShopResolverDiagnostics diagnostics) {
    this.requestStateMutatorService = requestStateMutatorService;
    this.outstandingNeededService = outstandingNeededService;
    this.diagnostics = diagnostics;
  }

  /**
   * MineColonies calls this whenever the request has no open children: right after {@code
   * attemptResolve} returned without a delivery (the Create order is still on its way) and again
   * each time the last delivery child completed. Like a crafter, the shop only finishes the request
   * once everything was delivered; otherwise the request stays IN_PROGRESS and the tick orders and
   * delivers the rest.
   */
  void resolveRequest(
      CreateShopRequestResolver resolver,
      @NotNull IRequestManager manager,
      @NotNull IRequest<? extends IDeliverable> request) {
    boolean finished = finishIfDelivered(resolver, manager, request, "resolveRequest");
    if (Config.DEBUG_LOGGING.getAsBoolean()) {
      TheSettlerXCreate.LOGGER.info(
          "[CreateShop] resolveRequest parent={} finished={} state={}",
          request.getId(),
          finished,
          request.getState());
    }
    if (manager instanceof IStandardRequestManager standardManager) {
      diagnostics.logRequestStateChange(standardManager, request.getId(), "resolveRequest");
    }
  }

  /**
   * The single place that closes a Create Shop parent request. It only resolves when no delivery
   * child is open and the delivered amount covers the request. Reservations are deliberately not
   * counted: stock sitting in the shop rack is not delivered yet.
   */
  boolean finishIfDelivered(
      CreateShopRequestResolver resolver,
      IRequestManager manager,
      IRequest<?> request,
      String source) {
    if (resolver == null
        || manager == null
        || request == null
        || !(request.getRequest() instanceof IDeliverable deliverable)
        || CreateShopRequestResolver.isTerminalRequestState(request.getState())
        || request.hasChildren()) {
      return false;
    }
    if (outstandingNeededService.compute(request, deliverable, 0) > 0) {
      return false;
    }
    try {
      manager.updateRequestState(request.getId(), RequestState.RESOLVED);
    } catch (Exception ex) {
      if (isDebugLoggingEnabledSafe()) {
        TheSettlerXCreate.LOGGER.info(
            "[CreateShop] finish parent={} source={} failed: {}",
            request.getId(),
            source,
            ex.getMessage() == null ? "<null>" : ex.getMessage());
      }
      return false;
    }
    resolver.transitionFlow(
        manager,
        request,
        CreateShopFlowState.REQUEST_COMPLETED,
        source + ":parent-resolved",
        "",
        0,
        "com.thesettler_x_create.message.createshop.flow_request_completed");
    resolver.releaseReservation(manager, request);
    requestStateMutatorService.clearPendingTokenState(resolver, request.getId(), true);
    if (isDebugLoggingEnabledSafe()) {
      TheSettlerXCreate.LOGGER.info(
          "[CreateShop] finish parent={} source={} -> resolved", request.getId(), source);
    }
    return true;
  }

  void onAssignedRequestBeingCancelled(
      CreateShopRequestResolver resolver,
      @NotNull IRequestManager manager,
      @NotNull IRequest<? extends IDeliverable> request) {
    resolver.transitionFlow(
        manager,
        request,
        CreateShopFlowState.CANCELLED,
        "assigned-cancelled",
        "",
        0,
        "com.thesettler_x_create.message.createshop.flow_cancelled");
    cleanupTerminalRequest(resolver, manager, request, true);
  }

  void onAssignedRequestCancelled(
      CreateShopRequestResolver resolver,
      @NotNull IRequestManager manager,
      @NotNull IRequest<? extends IDeliverable> request) {
    resolver.transitionFlow(
        manager,
        request,
        CreateShopFlowState.CANCELLED,
        "assigned-cancelled-post",
        "",
        0,
        "com.thesettler_x_create.message.createshop.flow_cancelled");
    cleanupTerminalRequest(resolver, manager, request, true);
  }

  void onRequestedRequestComplete(
      CreateShopRequestResolver resolver,
      @NotNull IRequestManager manager,
      @NotNull IRequest<?> request) {
    resolver.transitionFlow(
        manager,
        request,
        CreateShopFlowState.REQUEST_COMPLETED,
        "request-completed",
        "",
        0,
        "com.thesettler_x_create.message.createshop.flow_request_completed");
    cleanupTerminalRequest(
        resolver, manager, request, request.getRequest() instanceof IDeliverable);
  }

  void onRequestedRequestCancelled(
      CreateShopRequestResolver resolver,
      @NotNull IRequestManager manager,
      @NotNull IRequest<?> request) {
    resolver.transitionFlow(
        manager,
        request,
        CreateShopFlowState.CANCELLED,
        "request-cancelled",
        "",
        0,
        "com.thesettler_x_create.message.createshop.flow_cancelled");
    cleanupTerminalRequest(
        resolver, manager, request, request.getRequest() instanceof IDeliverable);
  }

  void cleanupTerminalRequest(
      CreateShopRequestResolver resolver,
      IRequestManager manager,
      IRequest<?> request,
      boolean releaseReservation) {
    if (request == null) {
      return;
    }
    boolean terminal = CreateShopRequestResolver.isTerminalRequestState(request.getState());
    IStandardRequestManager standardManager =
        CreateShopRequestResolver.unwrapStandardManager(manager);
    boolean graphActiveChild = hasActiveNonTerminalChildInGraph(standardManager, request.getId());
    if (!terminal || graphActiveChild) {
      if (isDebugLoggingEnabledSafe()) {
        TheSettlerXCreate.LOGGER.info(
            "[CreateShop] terminal cleanup skipped token={} state={} terminal={} graphActiveChild={}",
            request.getId(),
            request.getState(),
            terminal,
            graphActiveChild);
      }
      return;
    }
    requestStateMutatorService.clearPendingTokenState(
        resolver, standardManager, request.getId(), false);
    if (releaseReservation) {
      resolver.releaseReservation(manager, request);
    }
  }

  void sweepFastOrphanPickedUpRecoveries(
      CreateShopRequestResolver resolver,
      IRequestManager manager,
      IStandardRequestManager standardManager) {
    if (resolver == null || manager == null || standardManager == null) {
      return;
    }
    var handler = standardManager.getRequestHandler();
    if (handler == null) {
      return;
    }
    Level level = manager.getColony() == null ? null : manager.getColony().getWorld();
    for (var parentToken : java.util.List.copyOf(resolver.getPendingTracker().getTokens())) {
      if (parentToken == null) {
        continue;
      }
      IRequest<?> rawRequest;
      try {
        rawRequest = handler.getRequest(parentToken);
      } catch (Exception ignored) {
        rawRequest = null;
      }
      if (rawRequest == null) {
        continue;
      }
      IRequest<?> request = rawRequest;
      Object payload = request.getRequest();
      if (!(payload instanceof IDeliverable)) {
        continue;
      }
      observeActiveChildrenDuringSweep(resolver, standardManager, level, request);
      @SuppressWarnings("unchecked")
      IRequest<? extends IDeliverable> deliverableRequest =
          (IRequest<? extends IDeliverable>) request;
      tryFastOrphanPickedUpRecovery(resolver, manager, deliverableRequest);
    }
  }

  private void observeActiveChildrenDuringSweep(
      CreateShopRequestResolver resolver,
      IStandardRequestManager standardManager,
      Level level,
      IRequest<?> parentRequest) {
    if (resolver == null
        || standardManager == null
        || level == null
        || parentRequest == null
        || !parentRequest.hasChildren()) {
      return;
    }
    Collection<IToken<?>> children = parentRequest.getChildren();
    if (children == null || children.isEmpty()) {
      return;
    }
    var handler = standardManager.getRequestHandler();
    if (handler == null) {
      return;
    }
    for (IToken<?> childToken : java.util.List.copyOf(children)) {
      if (childToken == null) {
        continue;
      }
      IRequest<?> child;
      try {
        child = handler.getRequest(childToken);
      } catch (Exception ignored) {
        child = null;
      }
      if (child == null || CreateShopRequestResolver.isTerminalRequestState(child.getState())) {
        continue;
      }
      IToken<?> assignedResolver = findAssignedResolver(standardManager, childToken);
      resolver.observeDeliveryChildLifecycle(
          standardManager,
          level,
          parentRequest.getId(),
          childToken,
          child,
          assignedResolver,
          "sweep");
    }
  }

  private IToken<?> findAssignedResolver(
      IStandardRequestManager standardManager, IToken<?> requestToken) {
    if (standardManager == null || requestToken == null) {
      return null;
    }
    try {
      var store = standardManager.getRequestResolverRequestAssignmentDataStore();
      if (store == null || store.getAssignments() == null) {
        return null;
      }
      for (var entry : store.getAssignments().entrySet()) {
        if (entry.getValue() == null || !entry.getValue().contains(requestToken)) {
          continue;
        }
        return entry.getKey();
      }
    } catch (Exception ignored) {
      return null;
    }
    return null;
  }

  private static boolean isDebugLoggingEnabledSafe() {
    try {
      return Config.DEBUG_LOGGING.getAsBoolean();
    } catch (IllegalStateException ignored) {
      return false;
    }
  }

  private static boolean hasActiveNonTerminalChildInGraph(
      IStandardRequestManager manager,
      com.minecolonies.api.colony.requestsystem.token.IToken<?> parentToken) {
    if (manager == null || parentToken == null || manager.getRequestHandler() == null) {
      return false;
    }
    IRequest<?> parent;
    try {
      parent = manager.getRequestHandler().getRequest(parentToken);
    } catch (Exception ignored) {
      parent = null;
    }
    if (parent == null || !parent.hasChildren()) {
      return false;
    }
    for (var childToken : parent.getChildren()) {
      if (childToken == null) {
        continue;
      }
      try {
        IRequest<?> child = manager.getRequestHandler().getRequest(childToken);
        if (child != null && !CreateShopRequestResolver.isTerminalRequestState(child.getState())) {
          return true;
        }
      } catch (Exception ignored) {
        // Best-effort graph check only.
      }
    }
    return false;
  }

  private boolean tryFastOrphanPickedUpRecovery(
      CreateShopRequestResolver resolver,
      IRequestManager manager,
      IRequest<? extends IDeliverable> request) {
    if (resolver == null || manager == null || request == null) {
      return false;
    }
    if (request.hasChildren()) {
      return false;
    }
    IStandardRequestManager standardManager =
        CreateShopRequestResolver.unwrapStandardManager(manager);
    if (hasActiveNonTerminalChildInGraph(standardManager, request.getId())) {
      return false;
    }
    var orphanChild = resolver.findPickedUpOrphanChildForParent(request.getId());
    if (orphanChild == null) {
      return false;
    }
    BuildingCreateShop shop = resolver.getShop(manager);
    CreateShopBlockEntity pickup = shop == null ? null : shop.getPickupBlockEntity();
    if (pickup != null) {
      int reservedForRequest =
          pickup.getReservedForRequest(CreateShopRequestResolver.toRequestId(request.getId()));
      if (reservedForRequest > 0) {
        if (isDebugLoggingEnabledSafe()) {
          TheSettlerXCreate.LOGGER.info(
              "[CreateShop] fast orphan picked-up recovery skipped parent={} reservationHeld={}",
              request.getId(),
              reservedForRequest);
        }
        return false;
      }
    }
    Level level = manager.getColony() == null ? null : manager.getColony().getWorld();
    resolver.observeDeliveryChildCallbackTerminal(
        level, request.getId(), orphanChild, "fast-orphan-pickedup-recovery");
    requestStateMutatorService.finalizeOrphanDeliveryChild(
        resolver, standardManager, orphanChild, "fast-orphan-pickedup-recovery");
    // The orphan never reached MineColonies' completion callback, so MineColonies will not ask us
    // to resolve the parent. Run the same completion check it would have triggered.
    boolean finished =
        finishIfDelivered(resolver, manager, request, "fast-orphan-pickedup-recovery");
    if (isDebugLoggingEnabledSafe()) {
      TheSettlerXCreate.LOGGER.info(
          "[CreateShop] fast orphan picked-up recovery parent={} child={} finished={}",
          request.getId(),
          orphanChild,
          finished);
    }
    return true;
  }
}
