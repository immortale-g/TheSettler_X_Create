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

  void handleDeliveryCancelled(CreateShopRequestResolver resolver, IRequestManager manager, IRequest<?> request) {
    if (resolver == null || manager == null || request == null) {
      return;
    }
    if (!(request.getRequest() instanceof Delivery delivery)) {
      return;
    }
    if (request.getId() != null) {
      resolver.getDeliveryChildActiveSince().remove(request.getId());
    }
    IToken<?> parentToken =
        CreateShopDeliveryResolverLocator.resolveParentTokenForDelivery(manager, request);
    if (parentToken == null) {
      return;
    }
    resolver.getParentDeliveryActiveSince().remove(parentToken);
    resolver.clearStaleRecoveryArm(parentToken);
    UUID parentRequestId = CreateShopRequestResolver.toRequestId(parentToken);
    ItemStack stack = delivery.getStack().copy();

    Level level = manager.getColony() == null ? null : manager.getColony().getWorld();
    if (level == null) {
      requestStateMutatorService.markOrderedWithPendingAtLeastOne(
          resolver, null, parentToken, stack.getCount());
      diagnostics.recordPendingSource(parentToken, "delivery-cancel");
      resolver.clearDeliveriesCreated(parentToken);
      return;
    }

    BuildingCreateShop shop = resolver.getShop(manager);
    CreateShopBlockEntity pickup = shop == null ? null : shop.getPickupBlockEntity();
    if (pickup == null) {
      ILocation start = delivery.getStart();
      BlockPos startPos = start == null ? null : start.getInDimensionLocation();
      if (startPos != null) {
        BlockEntity entity = level.getBlockEntity(startPos);
        if (entity instanceof CreateShopBlockEntity shopPickup) {
          pickup = shopPickup;
        }
      }
    }
    if (pickup == null) {
      int fallbackPending = Math.max(1, stack.getCount());
      requestStateMutatorService.markOrderedWithPendingAtLeastOne(
          resolver, level, parentToken, fallbackPending);
      diagnostics.recordPendingSource(parentToken, "delivery-cancel-missing-pickup");
      resolver.clearDeliveriesCreated(parentToken);
      if (resolver.isDebugLoggingEnabled()) {
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
    pickup.release(parentRequestId);
    requestStateMutatorService.markOrderedWithPendingAtLeastOne(
        resolver, level, parentToken, pendingCount);
    diagnostics.recordPendingSource(parentToken, "delivery-cancel-reserve");
    resolver.clearDeliveriesCreated(parentToken);

    if (resolver.isDebugLoggingEnabled()) {
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
}


