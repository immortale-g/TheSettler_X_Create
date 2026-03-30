package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import com.minecolonies.api.colony.requestsystem.manager.IRequestManager;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.requestable.IDeliverable;
import com.thesettler_x_create.Config;
import com.thesettler_x_create.TheSettlerXCreate;
import com.thesettler_x_create.blockentity.CreateShopBlockEntity;
import com.thesettler_x_create.minecolonies.building.BuildingCreateShop;
import com.thesettler_x_create.minecolonies.tileentity.TileEntityCreateShop;
import java.util.UUID;
import net.minecraft.world.level.Level;

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
    boolean deliveryWindowOpen =
        request.hasChildren()
            || resolver.hasDeliveriesCreated(request.getId())
            || resolver.getPendingTracker().hasDeliveryStarted(request.getId());
    boolean completionSeen = resolver.hasParentChildCompletedSeen(request.getId());
    boolean holdDeliveryWindow = deliveryWindowOpen && !completionSeen;
    if (request.getState()
        == com.minecolonies.api.colony.requestsystem.request.RequestState.CANCELLED) {
      resolver.markCancelledRequest(request.getId());
    } else if (resolver.clearCancelledRequest(request.getId())) {
      if (Config.DEBUG_LOGGING.getAsBoolean()) {
        TheSettlerXCreate.LOGGER.info(
            "[CreateShop] cleared cancelled flag (state={}) {}",
            request.getState(),
            request.getId());
      }
    }
    if (resolver.isCancelledRequest(request.getId())) {
      if (Config.DEBUG_LOGGING.getAsBoolean()) {
        TheSettlerXCreate.LOGGER.info(
            "[CreateShop] canResolve=false (request cancelled) " + request.getId());
      }
      return false;
    }
    Level level = manager.getColony().getWorld();
    if (level.isClientSide) {
      if (Config.DEBUG_LOGGING.getAsBoolean()) {
        TheSettlerXCreate.LOGGER.info("[CreateShop] canResolve=false (no level or client)");
      }
      return false;
    }
    if (cooldown.isRequestOnCooldown(level, request.getId()) && !holdDeliveryWindow) {
      if (Config.DEBUG_LOGGING.getAsBoolean()) {
        TheSettlerXCreate.LOGGER.info("[CreateShop] canResolve=false (request already ordered)");
      }
      return false;
    }
    if (resolver.hasDeliveriesCreated(request.getId()) && !holdDeliveryWindow) {
      return false;
    }
    if (request.getRequester().getLocation().equals(resolver.getLocation())) {
      if (Config.DEBUG_LOGGING.getAsBoolean()) {
        TheSettlerXCreate.LOGGER.info("[CreateShop] canResolve=false (self-loop)");
      }
      return false;
    }
    IDeliverable deliverable = request.getRequest();

    BuildingCreateShop shop = resolver.getShop(manager);
    if (shop == null || !shop.isBuilt()) {
      if (Config.DEBUG_LOGGING.getAsBoolean()) {
        TheSettlerXCreate.LOGGER.info("[CreateShop] canResolve=false (shop missing or not built)");
      }
      return false;
    }
    chain.sanitizeRequestChain(manager, request);
    if (!chain.safeIsRequestChainValid(manager, request)) {
      if (Config.DEBUG_LOGGING.getAsBoolean()) {
        TheSettlerXCreate.LOGGER.info("[CreateShop] canResolve=false (request chain invalid)");
      }
      return false;
    }

    TileEntityCreateShop tile = shop.getCreateShopTileEntity();
    if (tile == null || tile.getStockNetworkId() == null) {
      if (Config.DEBUG_LOGGING.getAsBoolean()) {
        TheSettlerXCreate.LOGGER.info("[CreateShop] canResolve=false (missing stock network id)");
      }
      return false;
    }
    shop.ensurePickupLink();
    CreateShopBlockEntity pickup = shop.getPickupBlockEntity();
    if (pickup == null) {
      if (Config.DEBUG_LOGGING.getAsBoolean()) {
        TheSettlerXCreate.LOGGER.info("[CreateShop] canResolve=false (pickup block missing)");
      }
      return false;
    }

    UUID requestId = resolver.toRequestId(request.getId());
    int reservedForRequest = pickup.getReservedForRequest(requestId);
    int reservedForDeliverable = pickup.getReservedForDeliverable(deliverable);
    int reservedForOthers = Math.max(0, reservedForDeliverable - reservedForRequest);
    int needed = outstandingNeededService.compute(request, deliverable, reservedForRequest);
    if (needed <= 0) {
      if (holdDeliveryWindow) {
        if (Config.DEBUG_LOGGING.getAsBoolean()) {
          TheSettlerXCreate.LOGGER.info(
              "[CreateShop] canResolve=true (hold delivery window, needed<=0, reserved={}, children={}, deliveryCreated={}, deliveryStarted={})",
              reservedForRequest,
              request.hasChildren(),
              resolver.hasDeliveriesCreated(request.getId()),
              resolver.getPendingTracker().hasDeliveryStarted(request.getId()));
        }
        return true;
      }
      if (Config.DEBUG_LOGGING.getAsBoolean()) {
        TheSettlerXCreate.LOGGER.info("[CreateShop] canResolve=false (needed<=0)");
      }
      return false;
    }
    CreateShopStockSnapshot snapshot =
        stockResolver.getAvailability(tile, pickup, deliverable, reservedForOthers, planning);
    int available = snapshot.getAvailable();
    // Return false so MineColonies falls back to the next resolver (player) when not enough stock.
    if (available <= 0) {
      if (holdDeliveryWindow) {
        if (Config.DEBUG_LOGGING.getAsBoolean()) {
          TheSettlerXCreate.LOGGER.info(
              "[CreateShop] canResolve=true (hold delivery window, available<=0, reserved={}, needed={})",
              reservedForOthers,
              needed);
        }
        return true;
      }
      if (Config.DEBUG_LOGGING.getAsBoolean()) {
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
    boolean result = available >= minimum || available >= needed;
    if (Config.DEBUG_LOGGING.getAsBoolean()) {
      TheSettlerXCreate.LOGGER.info(
          "[CreateShop] canResolve={} (available={}, reserved={}, needed={}, min={}) for {}",
          result,
          available,
          reservedForOthers,
          needed,
          minimum,
          deliverable);
    }
    return result;
  }
}
