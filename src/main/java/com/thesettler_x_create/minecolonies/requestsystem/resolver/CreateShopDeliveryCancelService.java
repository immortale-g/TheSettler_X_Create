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
  void handleDeliveryCancelled(CreateShopRequestResolver resolver, IRequestManager manager, IRequest<?> request) {
    if (resolver == null || manager == null || request == null) {
      return;
    }
    if (!(request.getRequest() instanceof Delivery delivery)) {
      return;
    }
    if (request.getId() != null) {
      resolver.getDeliveryChildActiveSinceForOps().remove(request.getId());
    }
    IToken<?> parentToken =
        CreateShopDeliveryResolverLocator.resolveParentTokenForDelivery(manager, request);
    if (parentToken == null) {
      return;
    }
    resolver.getParentDeliveryActiveSinceForOps().remove(parentToken);
    resolver.clearStaleRecoveryArmForOps(parentToken);
    UUID parentRequestId = CreateShopRequestResolver.toRequestId(parentToken);
    ItemStack stack = delivery.getStack().copy();

    Level level = manager.getColony() == null ? null : manager.getColony().getWorld();
    if (level == null) {
      resolver.getPendingTracker().setPendingCount(parentToken, Math.max(1, stack.getCount()));
      resolver.getDiagnosticsForOps().recordPendingSource(parentToken, "delivery-cancel");
      resolver.clearDeliveriesCreated(parentToken);
      return;
    }

    BuildingCreateShop shop = resolver.getShopForValidator(manager);
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
      resolver.getPendingTracker().setPendingCount(parentToken, fallbackPending);
      resolver.getDiagnosticsForOps().recordPendingSource(parentToken, "delivery-cancel-missing-pickup");
      resolver.getCooldown().markRequestOrdered(level, parentToken);
      resolver.clearDeliveriesCreated(parentToken);
      if (resolver.isDebugLoggingEnabledForOps()) {
        TheSettlerXCreate.LOGGER.info(
            "[CreateShop] delivery cancelled {} -> parent={} pendingCount={} (pickup missing, fallback requeue)",
            request.getId(),
            parentToken,
            fallbackPending);
      }
      IStandardRequestManager standard = CreateShopRequestResolver.unwrapStandardManager(manager);
      if (standard != null) {
        resolver.getRecheckForOps().scheduleParentChildRecheck(standard, parentToken);
      }
      return;
    }
    if (!CreateShopDeliveryOriginMatcher.isDeliveryFromLocalShopStart(delivery, shop, pickup)) {
      return;
    }

    int reservedForRequest = pickup.getReservedForRequest(parentRequestId);
    int pendingCount = Math.max(1, Math.max(reservedForRequest, stack.getCount()));
    pickup.release(parentRequestId);
    resolver.getPendingTracker().setPendingCount(parentToken, pendingCount);
    resolver.getDiagnosticsForOps().recordPendingSource(parentToken, "delivery-cancel-reserve");
    resolver.getCooldown().markRequestOrdered(level, parentToken);
    resolver.clearDeliveriesCreated(parentToken);

    if (resolver.isDebugLoggingEnabledForOps()) {
      int reservedForStack = pickup.getReservedFor(stack);
      BlockPos pickupPosition = pickup.getBlockPos();
      resolver
          .getDeliveryManagerForOps()
          .logDeliveryDiagnostics(
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
        resolver.getDiagnosticsForOps().logParentChildrenState(standard, parentToken, "delivery-cancel");
        resolver.getRecheckForOps().scheduleParentChildRecheck(standard, parentToken);
      }
    }
  }
}
