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
  private static final int MAX_CATCHUP_STACKS = 16;
  private static final int TRANSFER_STACKS = 1;

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

  /** Runs one housekeeping tick: moves unreserved rack stacks to hut and triggers pickup. */
  void tick(IColony colony) {
    if (colony == null || colony.getWorld() == null || colony.getWorld().isClientSide) {
      return;
    }
    long now = colony.getWorld().getGameTime();
    long elapsed = lastTransferTick < 0L ? TRANSFER_INTERVAL : now - lastTransferTick;
    if (elapsed < TRANSFER_INTERVAL) {
      if (BuildingCreateShop.isDebugRequests() && shouldLogDebug(now)) {
        com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
            "[CreateShop] housekeeping wait cooldown remaining={}t", TRANSFER_INTERVAL - elapsed);
      }
      return;
    }
    TileEntityCreateShop tile = shop.getCreateShopTileEntity();
    CreateShopBlockEntity pickup = shop.getPickupBlockEntity();
    if (tile == null || pickup == null) {
      cachedHasIncomingRackWork = tile != null && tile.hasUnreservedRackItems(pickup);
      if (BuildingCreateShop.isDebugRequests() && shouldLogDebug(now)) {
        com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
            "[CreateShop] housekeeping skip tilePresent={} pickupPresent={} pendingUnreserved={}",
            tile != null,
            pickup != null,
            cachedHasIncomingRackWork);
      }
      return;
    }
    if (hasActiveLocalDeliveryChildren(colony, pickup)) {
      cachedHasIncomingRackWork = tile.hasUnreservedRackItems(pickup);
      if (BuildingCreateShop.isDebugRequests() && shouldLogDebug(now)) {
        com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
            "[CreateShop] housekeeping blocked reason=active-delivery-child pendingUnreserved={}",
            cachedHasIncomingRackWork);
      }
      return;
    }
    CreateShopRequestResolver resolver = shop.getOrCreateShopResolver();
    if (resolver != null && resolver.hasProtectedInventoryWindow()) {
      cachedHasIncomingRackWork = tile.hasUnreservedRackItems(pickup);
      if (BuildingCreateShop.isDebugRequests() && shouldLogDebug(now)) {
        com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
            "[CreateShop] housekeeping blocked reason=resolver-active-work pendingUnreserved={}",
            cachedHasIncomingRackWork);
      }
      return;
    }
    if (!shop.hasHousekeepingAvailableWorker()) {
      cachedHasIncomingRackWork = tile.hasUnreservedRackItems(pickup);
      if (BuildingCreateShop.isDebugRequests() && shouldLogDebug(now)) {
        com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
            "[CreateShop] housekeeping blocked reason={} pendingUnreserved={}",
            shop.describeHousekeepingBlockReason(),
            cachedHasIncomingRackWork);
      }
      return;
    }
    int dueStacks = Math.max(TRANSFER_STACKS, (int) Math.max(1L, elapsed / TRANSFER_INTERVAL));
    int transferBudget = Math.min(MAX_CATCHUP_STACKS, dueStacks);
    int moved = tile.moveUnreservedRackStacksToHut(pickup, transferBudget);
    boolean hutHasItems = tile.hasHutInventoryItems();
    cachedHasIncomingRackWork = tile.hasUnreservedRackItems(pickup);
    if (moved > 0 || cachedHasIncomingRackWork || hutHasItems) {
      lastTransferTick = now;
    }
    if (moved > 0 || hutHasItems) {
      int pickupPriority = shop.getPickUpPriority();
      if (pickupPriority > 0) {
        boolean pickupRequested = shop.createNativeHutPickupRequest(pickupPriority);
        if (BuildingCreateShop.isDebugRequests()) {
          com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
              "[CreateShop] housekeeping pickup request priority={} created={} moved={} hutHasItems={}",
              pickupPriority,
              pickupRequested,
              moved,
              hutHasItems);
        }
      } else if (BuildingCreateShop.isDebugRequests()) {
        com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
            "[CreateShop] housekeeping pickup request skipped (priority disabled) moved={} hutHasItems={}",
            moved,
            hutHasItems);
      }
    }
    if (moved > 0 && BuildingCreateShop.isDebugRequests()) {
      com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
          "[CreateShop] housekeeping moved unreserved rack stacks to hut count={} budget={} elapsed={}t",
          moved,
          transferBudget,
          elapsed);
    } else if (BuildingCreateShop.isDebugRequests() && shouldLogDebug(now)) {
      com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
          "[CreateShop] housekeeping ran but moved=0 pendingUnreserved={} budget={} elapsed={}t",
          cachedHasIncomingRackWork,
          transferBudget,
          elapsed);
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
