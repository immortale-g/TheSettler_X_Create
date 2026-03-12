package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import com.minecolonies.api.colony.requestsystem.manager.IRequestManager;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.requestable.IDeliverable;
import com.minecolonies.core.colony.requestsystem.management.IStandardRequestManager;
import com.thesettler_x_create.Config;
import com.thesettler_x_create.TheSettlerXCreate;
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
    cleanupTerminalRequest(resolver, manager, request, request.getRequest() instanceof IDeliverable);
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
    cleanupTerminalRequest(resolver, manager, request, request.getRequest() instanceof IDeliverable);
  }

  void cleanupTerminalRequest(
      CreateShopRequestResolver resolver,
      IRequestManager manager,
      IRequest<?> request,
      boolean releaseReservation) {
    if (request == null) {
      return;
    }
    requestStateMutatorService.clearOrderedAndPending(resolver, request.getId());
    resolver.clearDeliveriesCreated(request.getId());
    resolver.clearTrackedChildrenForParent(
        CreateShopRequestResolver.unwrapStandardManager(manager), request.getId());
    if (releaseReservation) {
      resolver.releaseReservation(manager, request);
    }
  }
}

