package com.thesettler_x_create.minecolonies.building;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.request.RequestState;
import com.minecolonies.api.colony.requestsystem.requestable.deliveryman.Delivery;
import com.minecolonies.api.colony.requestsystem.resolver.IRequestResolver;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.core.colony.requestsystem.management.IStandardRequestManager;
import com.thesettler_x_create.blockentity.CreateShopBlockEntity;
import com.thesettler_x_create.minecolonies.requestsystem.resolver.CreateShopRequestResolver;
import com.thesettler_x_create.minecolonies.tileentity.TileEntityCreateShop;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Handles incoming rack housekeeping: moving unreserved rack items to the hut inventory and
 * triggering native MineColonies pickup requests.
 *
 * <p>Extracted from {@link BuildingCreateShop} to separate housekeeping lifecycle from building
 * orchestration.
 */
final class ShopHousekeepingOrchestrator {
  private static final long TRANSFER_INTERVAL = 20L * 3L;
  private static final long DEBUG_COOLDOWN = 20L * 5L;

  private final BuildingCreateShop shop;
  private long lastTransferTick = -1L;
  private long lastWorkCheckTick = -1L;
  private long lastDebugTick = -1L;
  private boolean cachedHasIncomingRackWork;

  ShopHousekeepingOrchestrator(BuildingCreateShop shop) {
    this.shop = shop;
  }

  /**
   * Returns whether the shop currently has unreserved rack items waiting to be moved to the hut.
   * Result is cached per game tick to avoid repeated tile lookups.
   */
  boolean hasIncomingRackWork() {
    Level level = shop.getColony() == null ? null : shop.getColony().getWorld();
    if (level == null || level.isClientSide) {
      return false;
    }
    long now = level.getGameTime();
    if (lastWorkCheckTick == now) {
      return cachedHasIncomingRackWork;
    }
    lastWorkCheckTick = now;
    TileEntityCreateShop tile = shop.getCreateShopTileEntity();
    CreateShopBlockEntity pickup = shop.getPickupBlockEntity();
    cachedHasIncomingRackWork = tile != null && tile.hasUnreservedRackItems(pickup);
    return cachedHasIncomingRackWork;
  }

  /**
   * Returns whether there are active local MineColonies delivery children whose start location
   * matches the shop pickup block or a registered container. Used to gate housekeeping.
   */
  boolean hasActiveLocalDeliveryChildren(IColony colony, CreateShopBlockEntity pickup) {
    if (colony == null || pickup == null || pickup.getLevel() == null) {
      return false;
    }
    if (!(colony.getRequestManager() instanceof IStandardRequestManager standard)) {
      return false;
    }
    var assignmentStore = standard.getRequestResolverRequestAssignmentDataStore();
    if (assignmentStore == null) {
      return false;
    }
    Map<IToken<?>, java.util.Collection<IToken<?>>> assignments = assignmentStore.getAssignments();
    if (assignments == null || assignments.isEmpty()) {
      return false;
    }
    var requestHandler = standard.getRequestHandler();
    if (requestHandler == null) {
      return false;
    }
    for (var assignmentEntry : assignments.entrySet()) {
      java.util.Collection<IToken<?>> assigned = assignmentEntry.getValue();
      if (assigned == null || assigned.isEmpty()) {
        continue;
      }
      for (IToken<?> token : java.util.List.copyOf(assigned)) {
        if (token == null) {
          continue;
        }
        try {
          IRequest<?> request = requestHandler.getRequest(token);
          if (request == null || !(request.getRequest() instanceof Delivery delivery)) {
            continue;
          }
          RequestState state = request.getState();
          boolean activeState =
              state == RequestState.CREATED
                  || state == RequestState.ASSIGNED
                  || state == RequestState.IN_PROGRESS;
          if (!activeState) {
            continue;
          }
          if (!isLocalDeliveryStart(delivery, pickup)) {
            continue;
          }
          if (!isLocalCreateShopDeliveryParent(standard, request)) {
            continue;
          }
          return true;
        } catch (Exception ex) {
          if (BuildingCreateShop.isDebugRequests()) {
            com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
                "[CreateShop] housekeeping delivery-child check failed token={} error={}",
                token,
                ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
          }
          return true;
        }
      }
    }
    return false;
  }

  /**
   * Returns true when the current state allows the shopkeeper AI to perform a housekeeping carry.
   * Checks active delivery children, resolver inventory window, and worker availability.
   */
  boolean isHousekeepingAllowed(IColony colony, @Nullable CreateShopBlockEntity pickup) {
    if (hasActiveLocalDeliveryChildren(colony, pickup)) {
      return false;
    }
    CreateShopRequestResolver resolver = shop.getOrCreateShopResolver();
    if (resolver != null && resolver.hasProtectedInventoryWindow()) {
      return false;
    }
    return shop.hasHousekeepingAvailableWorker();
  }

  /**
   * Periodic tick: updates rack-work cache and triggers native pickup requests when hut inventory
   * has items. Item movement itself is now handled by the shopkeeper AI via physical carry states.
   */
  void tick(IColony colony) {
    if (colony == null || colony.getWorld() == null || colony.getWorld().isClientSide) {
      return;
    }
    long now = colony.getWorld().getGameTime();
    TileEntityCreateShop tile = shop.getCreateShopTileEntity();
    CreateShopBlockEntity pickup = shop.getPickupBlockEntity();
    cachedHasIncomingRackWork = tile != null && tile.hasUnreservedRackItems(pickup);
    long elapsed = lastTransferTick < 0L ? TRANSFER_INTERVAL : now - lastTransferTick;
    if (elapsed < TRANSFER_INTERVAL) {
      return;
    }
    if (tile == null || pickup == null) {
      return;
    }
    if (!isHousekeepingAllowed(colony, pickup)) {
      if (BuildingCreateShop.isDebugRequests() && shouldLogDebug(now)) {
        com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
            "[CreateShop] housekeeping pickup blocked reason={} pendingUnreserved={}",
            shop.describeHousekeepingBlockReason(),
            cachedHasIncomingRackWork);
      }
      return;
    }
    boolean hutHasItems = tile.hasHutInventoryItems();
    if (hutHasItems) {
      lastTransferTick = now;
      int pickupPriority = shop.getPickUpPriority();
      if (pickupPriority > 0) {
        boolean pickupRequested = shop.createNativeHutPickupRequest(pickupPriority);
        if (BuildingCreateShop.isDebugRequests()) {
          com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
              "[CreateShop] housekeeping pickup request priority={} created={} hutHasItems={}",
              pickupPriority,
              pickupRequested,
              hutHasItems);
        }
      }
    }
  }

  private boolean shouldLogDebug(long now) {
    if (lastDebugTick >= 0L && now - lastDebugTick < DEBUG_COOLDOWN) {
      return false;
    }
    lastDebugTick = now;
    return true;
  }

  private boolean isLocalCreateShopDeliveryParent(
      IStandardRequestManager standard, IRequest<?> child) {
    if (standard == null || child == null || !child.hasParent()) {
      return false;
    }
    try {
      IRequest<?> parent = standard.getRequestHandler().getRequest(child.getParent());
      if (parent == null) {
        return false;
      }
      IRequestResolver<?> owner = standard.getResolverHandler().getResolverForRequest(parent);
      if (!(owner instanceof CreateShopRequestResolver resolver)) {
        return false;
      }
      return resolver.getLocation() != null && resolver.getLocation().equals(shop.getLocation());
    } catch (Exception ignored) {
      return false;
    }
  }

  private boolean isLocalDeliveryStart(Delivery delivery, CreateShopBlockEntity pickup) {
    if (delivery == null || pickup == null || pickup.getLevel() == null) {
      return false;
    }
    var start = delivery.getStart();
    if (start == null || start.getDimension() == null) {
      return false;
    }
    if (!pickup.getLevel().dimension().equals(start.getDimension())) {
      return false;
    }
    BlockPos startPos = start.getInDimensionLocation();
    if (startPos == null) {
      return false;
    }
    if (pickup.getBlockPos().equals(startPos)) {
      return true;
    }
    return shop.hasContainerPosition(startPos);
  }
}
