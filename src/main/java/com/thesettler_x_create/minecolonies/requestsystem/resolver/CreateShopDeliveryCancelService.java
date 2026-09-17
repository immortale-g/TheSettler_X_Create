package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import com.minecolonies.api.colony.requestsystem.manager.IRequestManager;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.requestable.deliveryman.Delivery;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.core.colony.requestsystem.management.IStandardRequestManager;
import com.thesettler_x_create.DebugLog;
import com.thesettler_x_create.TheSettlerXCreate;
import com.thesettler_x_create.blockentity.CreateShopBlockEntity;
import com.thesettler_x_create.minecolonies.building.BuildingCreateShop;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Handles delivery-cancel callbacks and parent requeue reconciliation for Create Shop requests. */
final class CreateShopDeliveryCancelService {
  private final CreateShopRequestStateMutatorService requestStateMutatorService;
  private final CreateShopResolverDiagnostics diagnostics;
  private final CreateShopResolverRecheck recheck;
  private final CreateShopDeliveryManager deliveryManager;

  CreateShopDeliveryCancelService(
      CreateShopRequestStateMutatorService requestStateMutatorService,
      CreateShopResolverDiagnostics diagnostics,
      CreateShopResolverRecheck recheck,
      CreateShopDeliveryManager deliveryManager) {
    this.requestStateMutatorService = requestStateMutatorService;
    this.diagnostics = diagnostics;
    this.recheck = recheck;
    this.deliveryManager = deliveryManager;
  }

  void handleDeliveryCancelled(
      CreateShopRequestResolver resolver, IRequestManager manager, IRequest<?> request) {
    if (resolver == null || manager == null || request == null) {
      return;
    }
    if (!(request.getRequest() instanceof Delivery delivery)) {
      return;
    }
    IToken<?> childToken = request.getId();
    IToken<?> parentToken =
        CreateShopDeliveryResolverLocator.resolveParentTokenForDelivery(manager, request);
    if (parentToken == null) {
      return;
    }
    UUID parentRequestId = CreateShopRequestResolver.toRequestId(parentToken);
    ItemStack stack = delivery.getStack().copy();

    Level level = manager.getColony() == null ? null : manager.getColony().getWorld();
    resolver.observeDeliveryChildCallbackTerminal(
        level, parentToken, childToken, "cancel-callback");
    if (level == null) {
      requestStateMutatorService.markOrderedWithPendingAtLeastOne(
          resolver, null, parentToken, stack.getCount());
      diagnostics.recordPendingSource(parentToken, "delivery-cancel");
      return;
    }

    BuildingCreateShop shop = resolver.getShop(manager);
    CreateShopBlockEntity pickup = shop == null ? null : shop.getPickupBlockEntity();
    if (pickup == null) {
      int fallbackPending = Math.max(1, stack.getCount());
      requestStateMutatorService.markOrderedWithPendingAtLeastOne(
          resolver, level, parentToken, fallbackPending);
      diagnostics.recordPendingSource(parentToken, "delivery-cancel-missing-pickup");
      if (DebugLog.enabled()) {
        TheSettlerXCreate.LOGGER.info(
            "[CreateShop] delivery cancelled {} -> parent={} pendingCount={} (pickup missing, fallback requeue)",
            request.getId(),
            parentToken,
            fallbackPending);
      }
      IStandardRequestManager standard = CreateShopRequestResolver.unwrapStandardManager(manager);
      if (standard != null) {
        recheck.scheduleParentChildRecheck(standard, parentToken);
      }
      return;
    }
    if (!CreateShopDeliveryOriginMatcher.isDeliveryFromLocalShopStart(delivery, shop, pickup)) {
      return;
    }

    int reservedForRequest = pickup.getReservedForRequest(parentRequestId);
    int pendingCount = Math.max(1, Math.max(reservedForRequest, stack.getCount()));
    releaseCancelledShare(manager, parentToken, childToken, parentRequestId, pickup, stack);
    requestStateMutatorService.markOrderedWithPendingAtLeastOne(
        resolver, level, parentToken, pendingCount);
    diagnostics.recordPendingSource(parentToken, "delivery-cancel-reserve");

    if (DebugLog.enabled()) {
      int reservedForStack = pickup.getReservedFor(stack);
      BlockPos pickupPosition = pickup.getBlockPos();
      deliveryManager.logDeliveryDiagnostics(
          "cancel",
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
          "[CreateShop] delivery cancelled {} -> parent={} pendingCount={} reserved={} pickup={}",
          request.getId(),
          parentToken,
          pendingCount,
          reservedForRequest,
          pickup.getBlockPos());
      IStandardRequestManager standard = CreateShopRequestResolver.unwrapStandardManager(manager);
      if (standard != null) {
        diagnostics.logParentChildrenState(standard, parentToken, "delivery-cancel");
        recheck.scheduleParentChildRecheck(standard, parentToken);
      }
    }
  }

  /**
   * Gives back what the cancelled delivery had reserved. Since 0.4.0 a request can have several
   * deliveries open at once, and each of them has its own share of the request's reservation. As
   * long as one of the siblings is still open its share has to stay reserved: its items are sitting
   * in the hut waiting to be picked up, and unreserving them lets a competing request claim them
   * out from under the courier on his way. Only the last delivery of a request gives the whole
   * reservation back.
   */
  private void releaseCancelledShare(
      IRequestManager manager,
      IToken<?> parentToken,
      IToken<?> childToken,
      UUID parentRequestId,
      CreateShopBlockEntity pickup,
      ItemStack stack) {
    if (!hasOtherOpenDeliveryChild(manager, parentToken, childToken)) {
      pickup.release(parentRequestId);
      return;
    }
    int released = pickup.releaseReservedForRequest(parentRequestId, stack, stack.getCount());
    if (DebugLog.enabled()) {
      TheSettlerXCreate.LOGGER.info(
          "[CreateShop] delivery cancelled {} -> parent={} releasedShare={} of {} (siblings still open)",
          childToken,
          parentToken,
          released,
          stack.getCount());
    }
  }

  /**
   * Whether {@code parentToken} still has a delivery child other than {@code childToken} that
   * MineColonies has not finished. Read-only on the native request graph; when the graph cannot be
   * read the answer is "no", which keeps the old whole-request release.
   */
  private static boolean hasOtherOpenDeliveryChild(
      IRequestManager manager, IToken<?> parentToken, IToken<?> childToken) {
    IStandardRequestManager standard = CreateShopRequestResolver.unwrapStandardManager(manager);
    if (standard == null || standard.getRequestHandler() == null) {
      return false;
    }
    IRequest<?> parent;
    try {
      parent = standard.getRequestHandler().getRequest(parentToken);
    } catch (Exception ignored) {
      return false;
    }
    if (parent == null || !parent.hasChildren() || parent.getChildren() == null) {
      return false;
    }
    for (IToken<?> sibling : List.copyOf(parent.getChildren())) {
      if (sibling == null || sibling.equals(childToken)) {
        continue;
      }
      try {
        IRequest<?> child = standard.getRequestHandler().getRequest(sibling);
        if (child == null
            || !(child.getRequest() instanceof Delivery)
            || CreateShopRequestResolver.isTerminalRequestState(child.getState())) {
          continue;
        }
        return true;
      } catch (Exception ignored) {
        // A token can outlive its request for a moment; it is simply not a sibling then.
      }
    }
    return false;
  }
}
