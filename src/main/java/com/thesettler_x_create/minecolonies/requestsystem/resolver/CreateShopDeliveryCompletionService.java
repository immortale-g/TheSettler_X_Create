package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import com.minecolonies.api.colony.requestsystem.location.ILocation;
import com.minecolonies.api.colony.requestsystem.manager.IRequestManager;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.requestable.deliveryman.Delivery;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.core.colony.requestsystem.management.IStandardRequestManager;
import com.thesettler_x_create.DebugLog;
import com.thesettler_x_create.TheSettlerXCreate;
import com.thesettler_x_create.blockentity.CreateShopBlockEntity;
import com.thesettler_x_create.minecolonies.building.BuildingCreateShop;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Handles delivery completion reconciliation for Create Shop requests. */
final class CreateShopDeliveryCompletionService {
  private final CreateShopDeliveryManager deliveryManager;
  private final CreateShopResolverDiagnostics diagnostics;
  private final CreateShopResolverRecheck recheck;

  CreateShopDeliveryCompletionService(
      CreateShopDeliveryManager deliveryManager,
      CreateShopResolverDiagnostics diagnostics,
      CreateShopResolverRecheck recheck) {
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
    Level level =
        manager == null || manager.getColony() == null ? null : manager.getColony().getWorld();
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
        ILocation start = delivery.getStart();
        BlockPos startPos = start == null ? null : start.getInDimensionLocation();
        if (pickup != null
            && CreateShopDeliveryOriginMatcher.isDeliveryFromLocalShopStart(
                delivery, shop, pickup)) {
          UUID parentRequestId = CreateShopRequestResolver.toRequestId(parentToken);
          // The parent's orders still on their way stay tracked: one completed delivery says
          // nothing about the rest, and forgetting them made the parent order again. They are
          // detached when the parent itself ends.
          ItemStack stack = delivery.getStack().copy();
          // A delivery starting at the hut had its reservation consumed when the courier took the
          // items out (CreateShopPickupObservationService); consuming it again here would eat a
          // sibling delivery's share that is still in the rack. A delivery created before 0.4.0
          // starts at a rack, is gathered past the hut and never reported, so it keeps the old
          // behavior and consumes on completion.
          if (!stack.isEmpty()
              && !CreateShopDeliveryOriginMatcher.isDeliveryFromShopHut(delivery, shop)) {
            pickup.consumeReservedForRequest(parentRequestId, stack, stack.getCount());
          }
          if (DebugLog.enabled()) {
            int reservedForRequest = pickup.getReservedForRequest(parentRequestId);
            int reservedForStack = pickup.getReservedFor(stack);
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
                "[CreateShop] delivery complete detail token={} parent={} stack={} count={} start={} target={}",
                request.getId(),
                parentToken,
                stack.isEmpty() ? "<empty>" : stack.getItem().toString(),
                stack.getCount(),
                startPos,
                delivery.getTarget().getInDimensionLocation());
          }
        }
      } catch (Exception ignored) {
        // Ignore delivery detail logging failures.
      }
    }
    // Parent resolution is left to MineColonies: this callback runs inside
    // RequestHandler#onRequestCompleted before it detaches the child and, once the parent has no
    // children left, calls our resolveRequest. Keep the child linked so that path runs.
    if (DebugLog.enabled()) {
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
}
