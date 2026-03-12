package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.requestable.deliveryman.Delivery;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.core.colony.requestsystem.management.IStandardRequestManager;
import com.thesettler_x_create.Config;
import com.thesettler_x_create.TheSettlerXCreate;
import com.thesettler_x_create.blockentity.CreateShopBlockEntity;
import com.thesettler_x_create.minecolonies.building.BuildingCreateShop;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Performs guarded local-delivery child recovery and parent requeue reconciliation. */
final class CreateShopDeliveryChildRecoveryService {
  private final CreateShopRequestStateMutatorService requestStateMutatorService;
  private final CreateShopResolverOwnership ownership;

  CreateShopDeliveryChildRecoveryService(
      CreateShopRequestStateMutatorService requestStateMutatorService,
      CreateShopResolverOwnership ownership) {
    this.requestStateMutatorService = requestStateMutatorService;
    this.ownership = ownership;
  }

  boolean recover(
      CreateShopRequestResolver resolver,
      IStandardRequestManager manager,
      Level level,
      IRequest<?> parentRequest,
      IToken<?> childToken,
      IRequest<?> childRequest,
      BuildingCreateShop shop,
      CreateShopBlockEntity pickup,
      String pendingSource,
      String logTemplate) {
    if (resolver == null
        || manager == null
        || level == null
        || parentRequest == null
        || childToken == null) {
      return false;
    }
    if (!ownership.isRequestOwnedByLocalResolver(manager, parentRequest)) {
      resolver.clearStaleRecoveryArm(parentRequest.getId());
      return false;
    }
    if (!CreateShopDeliveryOriginMatcher.isLocalShopDeliveryChild(childRequest, shop, pickup)) {
      return false;
    }
    int childCount = 1;
    String childItem = "<unknown>";
    if (childRequest != null && childRequest.getRequest() instanceof Delivery delivery) {
      ItemStack stack = delivery.getStack();
      if (stack != null && !stack.isEmpty()) {
        childCount = Math.max(1, stack.getCount());
        childItem = stack.getItem().toString();
      }
    }
    boolean stateUpdated = false;
    try {
      manager.updateRequestState(
          childToken, com.minecolonies.api.colony.requestsystem.request.RequestState.CANCELLED);
      stateUpdated = true;
    } catch (Exception ignored) {
      // Best effort; parent child-link cleanup below still runs.
    }
    try {
      parentRequest.removeChild(childToken);
    } catch (Exception ignored) {
      // Best effort.
    }
    resolver.clearDeliveriesCreated(parentRequest.getId());
    int currentPending = resolver.getPendingTracker().getPendingCount(parentRequest.getId());
    requestStateMutatorService.markOrderedWithPendingAtLeastOne(
        resolver, level, parentRequest.getId(), Math.max(currentPending, childCount));
    resolver.getDiagnosticsForOps().recordPendingSource(parentRequest.getId(), pendingSource);
    resolver.getParentDeliveryActiveSinceForOps().put(parentRequest.getId(), level.getGameTime());
    resolver.clearStaleRecoveryArm(parentRequest.getId());
    resolver.getDeliveryChildActiveSinceForOps().put(childToken, level.getGameTime());
    resolver.getRecheck().scheduleParentChildRecheck(manager, parentRequest.getId());
    if (Config.DEBUG_LOGGING.getAsBoolean()) {
      TheSettlerXCreate.LOGGER.info(
          logTemplate, parentRequest.getId(), childToken, stateUpdated, childItem, childCount);
    }
    return true;
  }
}

