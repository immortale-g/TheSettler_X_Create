package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import com.minecolonies.api.colony.requestsystem.manager.IRequestManager;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.requestable.IDeliverable;
import com.thesettler_x_create.DebugLog;
import com.thesettler_x_create.TheSettlerXCreate;
import com.thesettler_x_create.blockentity.CreateShopBlockEntity;
import com.thesettler_x_create.minecolonies.building.BuildingCreateShop;
import com.thesettler_x_create.minecolonies.tileentity.TileEntityCreateShop;
import com.thesettler_x_create.stock.ShopStockAccounting;
import java.util.UUID;
import net.minecraft.world.level.Level;

/**
 * Decides whether a request is currently eligible to be (re-)resolved: checks the delivery window,
 * cooldown, and outstanding-needed amount via its injected collaborators before the resolver
 * commits to attempting a resolve.
 */
final class CreateShopRequestValidator {
  private final CreateShopOutstandingNeededService outstandingNeededService =
      new CreateShopOutstandingNeededService();
  private final CreateShopResolverChain chain;
  private final CreateShopStockResolver stockResolver;
  private final CreateShopResolverPlanning planning;
  private final CreateShopResolverCooldown cooldown;

  CreateShopRequestValidator(
      CreateShopResolverChain chain,
      CreateShopStockResolver stockResolver,
      CreateShopResolverPlanning planning,
      CreateShopResolverCooldown cooldown) {
    this.chain = chain;
    this.stockResolver = stockResolver;
    this.planning = planning;
    this.cooldown = cooldown;
  }

  boolean canResolveRequest(
      CreateShopRequestResolver resolver,
      IRequestManager manager,
      IRequest<? extends IDeliverable> request) {
    // Once the shop handed out a delivery the request stays with the shop, also while MineColonies
    // reassigns it after a cancelled child.
    boolean holdDeliveryWindow =
        request.hasChildren() || resolver.getPendingTracker().hasDeliveryStarted(request.getId());
    if (request.getState()
        == com.minecolonies.api.colony.requestsystem.request.RequestState.CANCELLED) {
      resolver.markCancelledRequest(request.getId());
    } else if (resolver.clearCancelledRequest(request.getId())) {
      if (DebugLog.enabled()) {
        TheSettlerXCreate.LOGGER.info(
            "[CreateShop] cleared cancelled flag (state={}) {}",
            request.getState(),
            request.getId());
      }
    }
    if (resolver.isCancelledRequest(request.getId())) {
      if (DebugLog.enabled()) {
        TheSettlerXCreate.LOGGER.info(
            "[CreateShop] canResolve=false (request cancelled) " + request.getId());
      }
      return false;
    }
    Level level = manager.getColony().getWorld();
    if (level.isClientSide) {
      DebugLog.info("[CreateShop] canResolve=false (no level or client)");
      return false;
    }
    if (cooldown.isRequestOnCooldown(level, request.getId()) && !holdDeliveryWindow) {
      DebugLog.info("[CreateShop] canResolve=false (request already ordered)");
      return false;
    }
    if (request.getRequester().getLocation().equals(resolver.getLocation())) {
      DebugLog.info("[CreateShop] canResolve=false (self-loop)");
      return false;
    }
    // Secondary guard: ILocation.equals() may create new objects and fail silently.
    // Compare BlockPos directly to catch perma-request self-loops.
    {
      net.minecraft.core.BlockPos requesterPos =
          request.getRequester().getLocation().getInDimensionLocation();
      net.minecraft.core.BlockPos resolverPos = resolver.getLocation().getInDimensionLocation();
      if (requesterPos != null && requesterPos.equals(resolverPos)) {
        TheSettlerXCreate.LOGGER.warn(
            "[CreateShop] canResolve=false (self-loop by BlockPos — ILocation.equals missed it)"
                + " requester={} resolver={}",
            requesterPos,
            resolverPos);
        return false;
      }
    }
    IDeliverable deliverable = request.getRequest();

    BuildingCreateShop shop = resolver.getShop(manager);
    if (shop == null || !shop.isBuilt()) {
      DebugLog.info("[CreateShop] canResolve=false (shop missing or not built)");
      return false;
    }
    if (!shop.isWorkerWorking() && !holdDeliveryWindow) {
      DebugLog.info("[CreateShop] canResolve=false (no shopkeeper working)");
      return false;
    }
    chain.sanitizeRequestChain(manager, request);
    if (!chain.safeIsRequestChainValid(manager, request)) {
      DebugLog.info("[CreateShop] canResolve=false (request chain invalid)");
      return false;
    }

    TileEntityCreateShop tile = shop.getCreateShopTileEntity();
    if (tile == null || tile.getStockNetworkId() == null) {
      DebugLog.info("[CreateShop] canResolve=false (missing stock network id)");
      return false;
    }
    shop.ensurePickupLink();
    CreateShopBlockEntity pickup = shop.getPickupBlockEntity();
    if (pickup == null) {
      DebugLog.info("[CreateShop] canResolve=false (pickup block missing)");
      return false;
    }

    UUID requestId = CreateShopRequestResolver.toRequestId(request.getId());
    int reservedForRequest = pickup.getReservedForRequest(requestId);
    int reservedForDeliverable = pickup.getReservedForDeliverable(deliverable);
    int reservedForOthers =
        ShopStockAccounting.reservedForOthers(reservedForDeliverable, reservedForRequest);
    int needed = outstandingNeededService.compute(request, deliverable, reservedForRequest);
    if (needed <= 0) {
      if (holdDeliveryWindow) {
        DebugLog.info(
            "[CreateShop] canResolve=true (hold delivery window, needed<=0, reserved={})",
            reservedForRequest);
        return true;
      }
      DebugLog.info("[CreateShop] canResolve=false (needed<=0)");
      return false;
    }
    CreateShopStockSnapshot snapshot =
        stockResolver.getAvailability(tile, pickup, deliverable, reservedForOthers, planning);
    int available = snapshot.available();
    // Return false so MineColonies falls back to the next resolver (player) when not enough stock.
    if (available <= 0) {
      if (holdDeliveryWindow) {
        DebugLog.info(
            "[CreateShop] canResolve=true (hold delivery window, available<=0, reserved={}, needed={})",
            reservedForOthers,
            needed);
        return true;
      }
      if (DebugLog.enabled()) {
        TheSettlerXCreate.LOGGER.info(
            "[CreateShop] canResolve=false (available={}, reserved={}, needed={}, min={}) for {}",
            available,
            reservedForOthers,
            needed,
            deliverable.getMinimumCount(),
            deliverable);
      }
      return false;
    }

    int minimum = deliverable.getMinimumCount();
    boolean result = ShopStockAccounting.canCover(available, needed, minimum);
    DebugLog.info(
        "[CreateShop] canResolve={} (available={}, reserved={}, needed={}, min={}) for {}",
        result,
        available,
        reservedForOthers,
        needed,
        minimum,
        deliverable);
    return result;
  }
}
