package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import com.minecolonies.api.colony.requestsystem.manager.IRequestManager;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.request.RequestState;
import com.minecolonies.api.colony.requestsystem.requestable.IDeliverable;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.core.colony.requestsystem.management.IStandardRequestManager;
import com.thesettler_x_create.Config;
import com.thesettler_x_create.TheSettlerXCreate;
import java.util.Collection;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

/** Handles terminal request lifecycle cleanup and resolver completion/cancel transitions. */
final class CreateShopTerminalRequestLifecycleService {
  private final CreateShopRequestStateMutatorService requestStateMutatorService;
  private final CreateShopResolverCooldown cooldown;
  private final CreateShopResolverDiagnostics diagnostics;

  CreateShopTerminalRequestLifecycleService(
      CreateShopRequestStateMutatorService requestStateMutatorService,
      CreateShopResolverCooldown cooldown,
      CreateShopResolverDiagnostics diagnostics) {
    this.requestStateMutatorService = requestStateMutatorService;
    this.cooldown = cooldown;
    this.diagnostics = diagnostics;
  }

  void resolveRequest(
      CreateShopRequestResolver resolver,
      @NotNull IRequestManager manager,
      @NotNull IRequest<? extends IDeliverable> request) {
    if (tryFastOrphanPickedUpRecovery(resolver, manager, request)) {
      return;
    }
    Level level = manager.getColony().getWorld();
    boolean ordered = cooldown.isOrdered(request.getId());
    boolean onCooldown = cooldown.isRequestOnCooldown(level, request.getId());
    if (ordered || onCooldown) {
      if (Config.DEBUG_LOGGING.getAsBoolean()) {
        TheSettlerXCreate.LOGGER.info(
            "[CreateShop] resolveRequest skip parent={} ordered={} cooldown={}",
            request.getId(),
            ordered,
            onCooldown);
      }
      if (manager instanceof IStandardRequestManager standardManager) {
        diagnostics.logRequestStateChange(standardManager, request.getId(), "resolveRequest-skip");
      }
      return;
    }
    resolver.resolveViaWarehouse(manager, request);
    if (Config.DEBUG_LOGGING.getAsBoolean()) {
      TheSettlerXCreate.LOGGER.info(
          "[CreateShop] resolveRequest parent={} state={}", request.getId(), request.getState());
    }
    if (manager instanceof IStandardRequestManager standardManager) {
      diagnostics.logRequestStateChange(standardManager, request.getId(), "resolveRequest");
    }
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
    boolean deliveryStarted = resolver.getPendingTracker().hasDeliveryStarted(request.getId());
    boolean completionSeen = resolver.hasParentChildCompletedSeen(request.getId());
    boolean completionGateOpen = !deliveryStarted || completionSeen;
    if (!terminal || graphActiveChild || !completionGateOpen) {
      if (isDebugLoggingEnabledSafe()) {
        TheSettlerXCreate.LOGGER.info(
            "[CreateShop] terminal cleanup skipped token={} state={} terminal={} graphActiveChild={} completionGateOpen={} deliveryStarted={} completionSeen={}",
            request.getId(),
            request.getState(),
            terminal,
            graphActiveChild,
            completionGateOpen,
            deliveryStarted,
            completionSeen);
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
    for (var parentToken : resolver.getParentDeliveryTokensSnapshot()) {
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
    boolean deliveryStarted = resolver.getPendingTracker().hasDeliveryStarted(request.getId());
    boolean completionSeen = resolver.hasParentChildCompletedSeen(request.getId());
    if (!deliveryStarted || completionSeen) {
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
    Level level = manager.getColony() == null ? null : manager.getColony().getWorld();
    long nowTick = level == null ? 0L : level.getGameTime();
    resolver.markParentChildCompletedSeen(request.getId(), nowTick);
    resolver.observeDeliveryChildCallbackTerminal(
        level, request.getId(), orphanChild, "fast-orphan-pickedup-recovery");
    requestStateMutatorService.finalizeOrphanDeliveryChild(
        resolver, standardManager, orphanChild, "fast-orphan-pickedup-recovery");
    requestStateMutatorService.completeDeliveryWindow(resolver, request.getId(), orphanChild);
    requestStateMutatorService.clearOrderedAndPending(resolver, request.getId());
    resolver.clearDeliveriesCreated(request.getId());
    resolver.releaseReservation(manager, request);
    try {
      if (standardManager != null) {
        standardManager.updateRequestState(request.getId(), RequestState.RESOLVED);
      }
    } catch (Exception ignored) {
      // Best effort; local cleanup already executed.
    }
    requestStateMutatorService.clearPendingTokenState(
        resolver, standardManager, request.getId(), true);
    if (isDebugLoggingEnabledSafe()) {
      TheSettlerXCreate.LOGGER.info(
          "[CreateShop] fast orphan picked-up recovery parent={} child={} -> resolved",
          request.getId(),
          orphanChild);
    }
    return true;
  }
}
