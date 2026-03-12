package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import com.minecolonies.api.colony.requestsystem.manager.IRequestManager;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.requestable.IDeliverable;
import org.jetbrains.annotations.NotNull;

/** Handles terminal request lifecycle cleanup and resolver completion/cancel transitions. */
final class CreateShopTerminalRequestLifecycleService {
  void resolveRequest(
      CreateShopRequestResolver resolver,
      @NotNull IRequestManager manager,
      @NotNull IRequest<? extends IDeliverable> request) {
    if (resolver.shouldSkipResolveForOps(manager, request)) {
      return;
    }
    resolver.resolveViaWarehouseForOps(manager, request);
    resolver.logResolveCompletionForOps(manager, request);
  }

  void onAssignedRequestBeingCancelled(
      CreateShopRequestResolver resolver,
      @NotNull IRequestManager manager,
      @NotNull IRequest<? extends IDeliverable> request) {
    resolver.transitionFlowForOps(
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
    resolver.transitionFlowForOps(
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
    resolver.transitionFlowForOps(
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
    resolver.transitionFlowForOps(
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
    resolver.getPendingTracker().remove(request.getId());
    resolver.getCooldown().clearRequestCooldown(request.getId());
    resolver.clearDeliveriesCreated(request.getId());
    resolver.clearTrackedChildrenForParentForOps(
        CreateShopRequestResolver.unwrapStandardManager(manager), request.getId());
    if (releaseReservation) {
      resolver.releaseReservation(manager, request);
    }
  }
}
