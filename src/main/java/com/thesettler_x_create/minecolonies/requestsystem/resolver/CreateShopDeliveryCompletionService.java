package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import com.minecolonies.api.colony.requestsystem.location.ILocation;
import com.minecolonies.api.colony.requestsystem.manager.IRequestManager;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
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

/** Handles delivery completion reconciliation and reservation consumption for Create Shop requests. */
final class CreateShopDeliveryCompletionService {
  private final CreateShopRequestStateMutatorService requestStateMutatorService;
  private final CreateShopDeliveryManager deliveryManager;
  private final CreateShopResolverDiagnostics diagnostics;
  private final CreateShopResolverRecheck recheck;

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

  void handleDeliveryComplete(CreateShopRequestResolver resolver, IRequestManager manager, IRequest<?> request) {
    if (resolver == null) {
      return;
    }
    if (request != null && request.getId() != null) {
      resolver.getDeliveryChildActiveSinceForOps().remove(request.getId());
    }
    IToken<?> parentToken =
        CreateShopDeliveryResolverLocator.resolveParentTokenForDelivery(manager, request);
    if (parentToken == null) {
      return;
    }
    resolver.getParentDeliveryActiveSinceForOps().remove(parentToken);
    resolver.clearStaleRecoveryArmForOps(parentToken);
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
        Level level =
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
            && CreateShopDeliveryOriginMatcher.isDeliveryFromLocalShopStart(delivery, shop, pickup)) {
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
          if (resolver.isDebugLoggingEnabledForOps()) {
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
    resolver.clearDeliveriesCreated(parentToken);
    int pending = resolver.getPendingTracker().getPendingCount(parentToken);
    if (pending > 0) {
      if (manager != null && manager.getColony() != null) {
        requestStateMutatorService.markOrderedWithPending(
            resolver, manager.getColony().getWorld(), parentToken, pending);
      }
    } else {
      requestStateMutatorService.clearOrderedAndPending(resolver, parentToken);
    }
    if (resolver.isDebugLoggingEnabledForOps()) {
      IStandardRequestManager debugManager = CreateShopRequestResolver.unwrapStandardManager(manager);
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
}
