package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import com.minecolonies.api.colony.requestsystem.manager.IRequestManager;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.requestable.IDeliverable;
import com.minecolonies.api.colony.requestsystem.requestable.INonExhaustiveDeliverable;
import com.thesettler_x_create.Config;
import com.thesettler_x_create.TheSettlerXCreate;
import com.thesettler_x_create.blockentity.CreateShopBlockEntity;
import com.thesettler_x_create.minecolonies.building.BuildingCreateShop;
import com.thesettler_x_create.minecolonies.tileentity.TileEntityCreateShop;
import java.util.UUID;
import net.minecraft.world.level.Level;

final class CreateShopRequestValidator {
  boolean canResolveRequest(
      CreateShopRequestResolver resolver,
      IRequestManager manager,
      IRequest<? extends IDeliverable> request) {
    if (request.getState()
        == com.minecolonies.api.colony.requestsystem.request.RequestState.CANCELLED) {
      resolver.getCancelledRequests().add(request.getId());
    } else if (resolver.getCancelledRequests().remove(request.getId())) {
      if (Config.DEBUG_LOGGING.getAsBoolean()) {
        TheSettlerXCreate.LOGGER.info(
            "[CreateShop] cleared cancelled flag (state={}) {}",
            request.getState(),
            request.getId());
      }
    }
    if (resolver.getCancelledRequests().contains(request.getId())) {
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
    if (resolver.getCooldown().isRequestOnCooldown(level, request.getId())) {
      if (Config.DEBUG_LOGGING.getAsBoolean()) {
        TheSettlerXCreate.LOGGER.info("[CreateShop] canResolve=false (request already ordered)");
      }
      return false;
    }
    if (resolver.hasDeliveriesCreated(request.getId())) {
      return false;
    }
    if (request.getRequester().getLocation().equals(resolver.getLocation())) {
      if (Config.DEBUG_LOGGING.getAsBoolean()) {
        TheSettlerXCreate.LOGGER.info("[CreateShop] canResolve=false (self-loop)");
      }
      return false;
    }
    IDeliverable deliverable = request.getRequest();

    BuildingCreateShop shop = resolver.getShopForValidator(manager);
    if (shop == null || !shop.isBuilt()) {
      if (Config.DEBUG_LOGGING.getAsBoolean()) {
        TheSettlerXCreate.LOGGER.info("[CreateShop] canResolve=false (shop missing or not built)");
      }
      return false;
    }
    resolver.getChain().sanitizeRequestChain(manager, request);
    if (!resolver.getChain().safeIsRequestChainValid(manager, request)) {
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

    int needed = deliverable.getCount();
    if (deliverable instanceof INonExhaustiveDeliverable nonExhaustive) {
      needed -= nonExhaustive.getLeftOver();
    }
    UUID requestId = resolver.toRequestId(request.getId());
    int reservedForRequest = pickup.getReservedForRequest(requestId);
    int reservedForDeliverable = pickup.getReservedForDeliverable(deliverable);
    int reservedForOthers = Math.max(0, reservedForDeliverable - reservedForRequest);
    needed = Math.max(0, needed - reservedForRequest);
    if (needed <= 0) {
      if (Config.DEBUG_LOGGING.getAsBoolean()) {
        TheSettlerXCreate.LOGGER.info("[CreateShop] canResolve=false (needed<=0)");
      }
      return false;
    }
    CreateShopStockSnapshot snapshot =
        resolver
            .getStockResolver()
            .getAvailability(tile, pickup, deliverable, reservedForOthers, resolver.getPlanning());
    int available = snapshot.getAvailable();
    // Return false so MineColonies falls back to the next resolver (player) when not enough stock.
    if (available <= 0) {
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
