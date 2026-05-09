package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import com.minecolonies.api.colony.requestsystem.location.ILocation;
import com.minecolonies.api.colony.requestsystem.manager.IRequestManager;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.request.RequestState;
import com.minecolonies.api.colony.requestsystem.requestable.IDeliverable;
import com.minecolonies.api.colony.requestsystem.requestable.deliveryman.Delivery;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.core.colony.requestsystem.management.IStandardRequestManager;
import com.thesettler_x_create.TheSettlerXCreate;
import com.thesettler_x_create.blockentity.CreateShopBlockEntity;
import com.thesettler_x_create.minecolonies.building.BuildingCreateShop;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Handles delivery completion reconciliation and reservation consumption for Create Shop requests.
 */
final class CreateShopDeliveryCompletionService {
  private final CreateShopRequestStateMutatorService requestStateMutatorService;
  private final CreateShopDeliveryManager deliveryManager;
  private final CreateShopResolverDiagnostics diagnostics;
  private final CreateShopResolverRecheck recheck;
  private final CreateShopOutstandingNeededService outstandingNeededService =
      new CreateShopOutstandingNeededService();

  CreateShopDeliveryCompletionService(
      CreateShopRequestStateMutatorService requestStateMutatorService,
      CreateShopDeliveryManager deliveryManager,
      CreateShopResolverDiagnostics diagnostics,
      CreateShopResolverRecheck recheck) {
    this.requestStateMutatorService = requestStateMutatorService;
    this.deliveryManager = deliveryManager;
    this.diagnostics = diagnostics;
    this.recheck = recheck;
  }

  void handleDeliveryComplete(
      CreateShopRequestResolver resolver, IRequestManager manager, IRequest<?> request) {
    if (resolver == null) {
      return;
    }
    IToken<?> childToken = request == null ? null : request.getId();
    IToken<?> parentToken =
        CreateShopDeliveryResolverLocator.resolveParentTokenForDelivery(manager, request);
    if (parentToken == null) {
      return;
    }
    requestStateMutatorService.completeDeliveryWindow(resolver, parentToken, childToken);
    Level level =
        manager == null || manager.getColony() == null ? null : manager.getColony().getWorld();
    resolver.markParentChildCompletedSeen(parentToken, level == null ? 0L : level.getGameTime());
    resolver.observeDeliveryChildCallbackTerminal(
        level, parentToken, childToken, "complete-callback");
    IRequest<?> parentRequest = null;
    IStandardRequestManager standard = CreateShopRequestResolver.unwrapStandardManager(manager);
    if (standard != null) {
      try {
        parentRequest = standard.getRequestHandler().getRequest(parentToken);
      } catch (Exception ignored) {
        // Ignore lookup failures; callbacks remain best-effort.
      }
    }
    if (parentRequest != null) {
      resolver.transitionFlow(
          manager,
          parentRequest,
          CreateShopFlowState.DELIVERY_COMPLETED,
          "delivery-complete",
          CreateShopStackMetrics.describeStack(
              request.getRequest() instanceof Delivery d ? d.getStack() : ItemStack.EMPTY),
          request.getRequest() instanceof Delivery d ? d.getStack().getCount() : 0,
          "com.thesettler_x_create.message.createshop.flow_delivery_completed");
    }
    if (request != null && request.getRequest() instanceof Delivery delivery) {
      try {
        BuildingCreateShop shop = resolver.getShop(manager);
        CreateShopBlockEntity pickup = null;
        if (shop != null) {
          pickup = shop.getPickupBlockEntity();
        }
        level =
            manager == null || manager.getColony() == null ? null : manager.getColony().getWorld();
        ILocation start = delivery.getStart();
        BlockPos startPos = start == null ? null : start.getInDimensionLocation();
        if (pickup == null
            && level != null
            && startPos != null
            && com.minecolonies.api.util.WorldUtil.isBlockLoaded(level, startPos)) {
          BlockEntity startEntity = level.getBlockEntity(startPos);
          if (startEntity instanceof CreateShopBlockEntity shopPickup) {
            pickup = shopPickup;
          }
        }
        if (pickup != null
            && CreateShopDeliveryOriginMatcher.isDeliveryFromLocalShopStart(
                delivery, shop, pickup)) {
          UUID parentRequestId = CreateShopRequestResolver.toRequestId(parentToken);
          ItemStack stack = delivery.getStack().copy();
          int reservedForStackBefore = pickup.getReservedFor(stack);
          if (!stack.isEmpty()) {
            pickup.consumeReservedForRequest(parentRequestId, stack, stack.getCount());
          }
          int reservedForStackAfter = pickup.getReservedFor(stack);
          int consumedReserved = Math.max(0, reservedForStackBefore - reservedForStackAfter);
          if (consumedReserved > 0 && parentRequest != null) {
            resolver.transitionFlow(
                manager,
                parentRequest,
                CreateShopFlowState.RESERVED_FOR_DELIVERY,
                "delivery-complete:reserved-consumed",
                CreateShopStackMetrics.describeStack(stack),
                consumedReserved,
                "com.thesettler_x_create.message.createshop.flow_reserved");
          }
          if (resolver.isDebugLoggingEnabled()) {
            int reservedForRequest = pickup.getReservedForRequest(parentRequestId);
            int reservedForStack = reservedForStackAfter;
            BlockPos pickupPosition = pickup.getBlockPos();
            deliveryManager.logDeliveryDiagnostics(
                "complete",
                manager,
                request.getId(),
                parentRequestId,
                pickupPosition,
                stack,
                delivery.getTarget(),
                reservedForRequest,
                -1,
                reservedForStack);
            TheSettlerXCreate.LOGGER.info(
                "[CreateShop] delivery complete detail token={} parent={} stack={} count={} start={} target={} reservedConsumed={}",
                request.getId(),
                parentToken,
                stack.isEmpty() ? "<empty>" : stack.getItem().toString(),
                stack.getCount(),
                startPos,
                delivery.getTarget().getInDimensionLocation(),
                consumedReserved);
          }
        }
      } catch (Exception ignored) {
        // Ignore delivery detail logging failures.
      }
    }
    boolean parentStillNonTerminal =
        parentRequest != null
            && !CreateShopRequestResolver.isTerminalRequestState(parentRequest.getState());
    int pending = resolver.getPendingTracker().getPendingCount(parentToken);
    boolean parentResolvedByDelivery =
        parentStillNonTerminal
            && completeParentAfterDeliveredChild(
                resolver, standard, manager, parentRequest, parentToken, childToken, pending);
    if (parentResolvedByDelivery) {
      requestStateMutatorService.clearPendingTokenState(resolver, standard, parentToken, true);
    } else if (parentStillNonTerminal) {
      int heldPending = Math.max(1, pending);
      requestStateMutatorService.markOrderedWithPendingAtLeastOne(
          resolver, level, parentToken, heldPending);
      diagnostics.recordPendingSource(parentToken, "delivery-complete:await-parent-terminal");
      resolver.touchFlow(
          parentToken,
          level == null ? 0L : level.getGameTime(),
          "delivery-complete:await-parent-terminal");
    } else if (pending > 0) {
      requestStateMutatorService.markOrderedWithPending(resolver, level, parentToken, pending);
    } else {
      requestStateMutatorService.closeDeliveryWindow(resolver, parentToken, null);
      requestStateMutatorService.clearOrderedAndPending(resolver, parentToken);
    }
    if (resolver.isDebugLoggingEnabled()) {
      IStandardRequestManager debugManager =
          CreateShopRequestResolver.unwrapStandardManager(manager);
      if (debugManager != null) {
        try {
          var handler = debugManager.getRequestHandler();
          IRequest<?> parent = handler.getRequest(parentToken);
          if (parent == null) {
            TheSettlerXCreate.LOGGER.info(
                "[CreateShop] delivery complete parent={} missing", parentToken);
            return;
          }
          String parentState = parent.getState().toString();
          boolean hasChildren = parent.hasChildren();
          TheSettlerXCreate.LOGGER.info(
              "[CreateShop] delivery complete parent={} state={} hasChildren={}",
              parentToken,
              parentState,
              hasChildren);
          diagnostics.logParentChildrenState(debugManager, parentToken, "delivery-complete");
          recheck.scheduleParentChildRecheck(debugManager, parentToken);
        } catch (Exception ignored) {
          // Ignore lookup errors.
        }
      }
    }
  }

  private boolean completeParentAfterDeliveredChild(
      CreateShopRequestResolver resolver,
      IStandardRequestManager standard,
      IRequestManager manager,
      IRequest<?> parentRequest,
      IToken<?> parentToken,
      IToken<?> childToken,
      int trackedPending) {
    if (resolver == null || standard == null || parentRequest == null || parentToken == null) {
      return false;
    }
    detachCompletedChild(standard, parentRequest, childToken);
    if (hasActiveNonTerminalChild(standard, parentRequest)) {
      return false;
    }
    int outstanding =
        computeOutstandingAfterDelivery(resolver, manager, parentRequest, parentToken);
    if (Math.max(0, Math.max(trackedPending, outstanding)) > 0) {
      return false;
    }
    try {
      standard.updateRequestState(parentToken, RequestState.RESOLVED);
      resolver.transitionFlow(
          manager,
          parentRequest,
          CreateShopFlowState.REQUEST_COMPLETED,
          "delivery-complete:parent-resolved",
          "",
          0,
          "com.thesettler_x_create.message.createshop.flow_request_completed");
      resolver.releaseReservation(manager, parentRequest);
      if (resolver.isDebugLoggingEnabled()) {
        TheSettlerXCreate.LOGGER.info(
            "[CreateShop] delivery complete resolved parent={} child={}", parentToken, childToken);
      }
      return true;
    } catch (Exception ignored) {
      return false;
    }
  }

  private int computeOutstandingAfterDelivery(
      CreateShopRequestResolver resolver,
      IRequestManager manager,
      IRequest<?> parentRequest,
      IToken<?> parentToken) {
    if (parentRequest == null
        || !(parentRequest.getRequest() instanceof IDeliverable deliverable)) {
      return 0;
    }
    int reservedForRequest = 0;
    try {
      BuildingCreateShop shop = resolver.getShop(manager);
      CreateShopBlockEntity pickup = shop == null ? null : shop.getPickupBlockEntity();
      if (pickup != null) {
        reservedForRequest =
            pickup.getReservedForRequest(CreateShopRequestResolver.toRequestId(parentToken));
      }
    } catch (Exception ignored) {
      reservedForRequest = 0;
    }
    return outstandingNeededService.compute(parentRequest, deliverable, reservedForRequest);
  }

  private void detachCompletedChild(
      IStandardRequestManager standard, IRequest<?> parentRequest, IToken<?> childToken) {
    if (standard == null || parentRequest == null || childToken == null) {
      return;
    }
    try {
      IRequest<?> child = standard.getRequestHandler().getRequestOrNull(childToken);
      if (child != null && CreateShopRequestResolver.isTerminalRequestState(child.getState())) {
        parentRequest.removeChild(childToken);
        child.setParent(null);
      }
    } catch (Exception ignored) {
      // Best effort: parent resolution below still checks for active children.
    }
  }

  private boolean hasActiveNonTerminalChild(
      IStandardRequestManager standard, IRequest<?> parentRequest) {
    if (standard == null || parentRequest == null || !parentRequest.hasChildren()) {
      return false;
    }
    for (IToken<?> childToken : java.util.List.copyOf(parentRequest.getChildren())) {
      if (childToken == null) {
        continue;
      }
      try {
        IRequest<?> child = standard.getRequestHandler().getRequestOrNull(childToken);
        if (child == null) {
          return true;
        }
        if (CreateShopRequestResolver.isTerminalRequestState(child.getState())) {
          parentRequest.removeChild(childToken);
          child.setParent(null);
          continue;
        }
        return true;
      } catch (Exception ignored) {
        return true;
      }
    }
    return false;
  }
}
