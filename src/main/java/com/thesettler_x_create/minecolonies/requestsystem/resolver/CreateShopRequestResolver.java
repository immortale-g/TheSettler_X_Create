package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import com.google.common.collect.Lists;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.workerbuildings.IWareHouse;
import com.minecolonies.api.colony.requestsystem.location.ILocation;
import com.minecolonies.api.colony.requestsystem.manager.IRequestManager;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.requestable.IDeliverable;
import com.minecolonies.api.colony.requestsystem.requestable.INonExhaustiveDeliverable;
import com.minecolonies.api.colony.requestsystem.requestable.Tool;
import com.minecolonies.api.colony.requestsystem.requestable.deliveryman.AbstractDeliverymanRequestable;
import com.minecolonies.api.colony.requestsystem.requestable.deliveryman.Delivery;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.api.equipment.registry.EquipmentTypeEntry;
import com.minecolonies.api.tileentities.AbstractTileEntityRack;
import com.minecolonies.api.util.BlockPosUtil;
import com.minecolonies.api.util.MessageUtils;
import com.minecolonies.api.util.WorldUtil;
import com.minecolonies.api.util.constant.TypeConstants;
import com.minecolonies.core.colony.buildings.modules.BuildingModules;
import com.minecolonies.core.colony.buildings.modules.CourierAssignmentModule;
import com.minecolonies.core.colony.buildings.modules.WarehouseRequestQueueModule;
import com.minecolonies.core.colony.requestsystem.management.IStandardRequestManager;
import com.minecolonies.core.colony.requestsystem.resolvers.core.AbstractWarehouseRequestResolver;
import com.thesettler_x_create.Config;
import com.thesettler_x_create.TheSettlerXCreate;
import com.thesettler_x_create.blockentity.CreateShopBlockEntity;
import com.thesettler_x_create.create.CreateNetworkFacade;
import com.thesettler_x_create.create.ICreateNetworkFacade;
import com.thesettler_x_create.minecolonies.building.BuildingCreateShop;
import com.thesettler_x_create.minecolonies.requestsystem.requesters.SafeRequester;
import com.thesettler_x_create.minecolonies.tileentity.TileEntityCreateShop;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.NotNull;

/**
 * Request resolver that fulfills deliverable requests from Create Shop stock and warehouse racks.
 */
public class CreateShopRequestResolver extends AbstractWarehouseRequestResolver {
  // Keep below warehouse resolvers so MineColonies prefers warehouse stock before Create Shop.
  private static final int PRIORITY = 140;
  private static final int MAX_CHAIN_SANITIZE_NODES = 512;
  private long lastPerfLogTime = 0L;
  private long lastTickPendingNanos = 0L;

  private static final java.util.Map<IToken<?>, Long> orderedRequests =
      new java.util.concurrent.ConcurrentHashMap<>();
  private static final java.util.Set<IToken<?>> deliveriesCreated =
      java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());
  private static final java.util.Set<IToken<?>> cancelledRequests =
      java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());
  private static final java.util.Map<IToken<?>, IToken<?>> deliveryParents =
      new java.util.concurrent.ConcurrentHashMap<>();
  private static final java.util.Map<IToken<?>, Integer> pendingRequestCounts =
      new java.util.concurrent.ConcurrentHashMap<>();
  private static final java.util.Map<IToken<?>, String> pendingSources =
      new java.util.concurrent.ConcurrentHashMap<>();
  private static final java.util.Map<IToken<?>, Long> pendingNotices =
      new java.util.concurrent.ConcurrentHashMap<>();
  private long lastDeliveryAssignmentDebugTime = 0L;
  private long lastTickPendingDebugTime = 0L;
  private final java.util.Set<String> deliveryLinkLogged =
      java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());
  private final java.util.Set<String> deliveryCreateLogged =
      java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());
  private final java.util.Map<IToken<?>, String> parentChildrenSnapshots =
      new java.util.concurrent.ConcurrentHashMap<>();
  private final java.util.Map<IToken<?>, Long> parentChildrenRecheck =
      new java.util.concurrent.ConcurrentHashMap<>();
  private final java.util.Map<IToken<?>, String> requestStateSnapshots =
      new java.util.concurrent.ConcurrentHashMap<>();
  private final java.util.Map<IToken<?>, String> pendingReasonSnapshots =
      new java.util.concurrent.ConcurrentHashMap<>();
  private final java.util.Set<String> chainCycleLogged =
      java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());

  public CreateShopRequestResolver(ILocation location, IToken<?> token) {
    super(location, token);
  }

  @Override
  public int getPriority() {
    return PRIORITY;
  }

  @Override
  public boolean isValid() {
    return true;
  }

  @Override
  public @NotNull MutableComponent getRequesterDisplayName(
      @NotNull IRequestManager manager, @NotNull IRequest<?> request) {
    return Component.translatable("com.thesettler_x_create.coremod.buildings.createshop");
  }

  @Override
  public boolean canResolveRequest(
      @NotNull IRequestManager manager, @NotNull IRequest<? extends IDeliverable> request) {
    if (request.getState()
        == com.minecolonies.api.colony.requestsystem.request.RequestState.CANCELLED) {
      cancelledRequests.add(request.getId());
    } else if (cancelledRequests.remove(request.getId())) {
      if (Config.DEBUG_LOGGING.getAsBoolean()) {
        TheSettlerXCreate.LOGGER.info(
            "[CreateShop] cleared cancelled flag (state={}) {}",
            request.getState(),
            request.getId());
      }
    }
    if (cancelledRequests.contains(request.getId())) {
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
    if (isRequestOnCooldown(level, request.getId())) {
      if (Config.DEBUG_LOGGING.getAsBoolean()) {
        TheSettlerXCreate.LOGGER.info("[CreateShop] canResolve=false (request already ordered)");
      }
      return false;
    }
    if (hasDeliveriesCreated(request.getId())) {
      return false;
    }
    if (request.getRequester().getLocation().equals(getLocation())) {
      if (Config.DEBUG_LOGGING.getAsBoolean()) {
        TheSettlerXCreate.LOGGER.info("[CreateShop] canResolve=false (self-loop)");
      }
      return false;
    }
    IDeliverable deliverable = request.getRequest();

    BuildingCreateShop shop = getShop(manager);
    if (shop == null || !shop.isBuilt()) {
      if (Config.DEBUG_LOGGING.getAsBoolean()) {
        TheSettlerXCreate.LOGGER.info("[CreateShop] canResolve=false (shop missing or not built)");
      }
      return false;
    }
    sanitizeRequestChain(manager, request);
    if (!safeIsRequestChainValid(manager, request)) {
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
    UUID requestId = toRequestId(request.getId());
    int reservedForRequest = pickup.getReservedForRequest(requestId);
    int reservedForDeliverable = pickup.getReservedForDeliverable(deliverable);
    needed = Math.max(0, needed - reservedForRequest);
    if (needed <= 0) {
      if (Config.DEBUG_LOGGING.getAsBoolean()) {
        TheSettlerXCreate.LOGGER.info("[CreateShop] canResolve=false (needed<=0)");
      }
      return false;
    }
    ICreateNetworkFacade network = new CreateNetworkFacade(tile);
    int networkAvailable = network.getAvailable(deliverable);
    int rackAvailable = getAvailableFromRacks(tile, deliverable);
    int pickupAvailable = getAvailableFromPickup(pickup, deliverable);
    int rackUsable = Math.max(0, rackAvailable - reservedForDeliverable);
    int available = Math.max(0, networkAvailable + rackUsable + pickupAvailable);
    // Return false so MineColonies falls back to the next resolver (player) when not enough stock.
    if (available <= 0) {
      if (Config.DEBUG_LOGGING.getAsBoolean()) {
        TheSettlerXCreate.LOGGER.info(
            "[CreateShop] canResolve=false (available={}, reserved={}, needed={}, min={}) for {}",
            available,
            reservedForDeliverable,
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
          reservedForDeliverable,
          needed,
          minimum,
          deliverable);
    }
    return result;
  }

  @Override
  public List<IToken<?>> attemptResolveRequest(
      @NotNull IRequestManager manager, @NotNull IRequest<? extends IDeliverable> request) {
    if (request.getState()
        == com.minecolonies.api.colony.requestsystem.request.RequestState.CANCELLED) {
      cancelledRequests.add(request.getId());
    } else {
      cancelledRequests.remove(request.getId());
    }
    if (cancelledRequests.contains(request.getId())) {
      if (Config.DEBUG_LOGGING.getAsBoolean()) {
        TheSettlerXCreate.LOGGER.info(
            "[CreateShop] attemptResolve skipped (request cancelled) " + request.getId());
      }
      return Lists.newArrayList();
    }
    Level level = manager.getColony().getWorld();
    if (level.isClientSide) {
      if (Config.DEBUG_LOGGING.getAsBoolean()) {
        TheSettlerXCreate.LOGGER.info("[CreateShop] attemptResolve skipped (no level or client)");
      }
      return Lists.newArrayList();
    }
    if (isRequestOnCooldown(level, request.getId())) {
      if (Config.DEBUG_LOGGING.getAsBoolean()) {
        TheSettlerXCreate.LOGGER.info(
            "[CreateShop] attemptResolve skipped (request already ordered)");
      }
      return Lists.newArrayList();
    }
    if (hasDeliveriesCreated(request.getId())) {
      return Lists.newArrayList();
    }
    IDeliverable deliverable = request.getRequest();
    sanitizeRequestChain(manager, request);

    BuildingCreateShop shop = getShop(manager);
    if (shop == null) {
      if (Config.DEBUG_LOGGING.getAsBoolean()) {
        TheSettlerXCreate.LOGGER.info("[CreateShop] attemptResolve skipped (shop missing)");
      }
      return Lists.newArrayList();
    }
    TileEntityCreateShop tile = shop.getCreateShopTileEntity();
    if (tile == null || tile.getStockNetworkId() == null) {
      if (Config.DEBUG_LOGGING.getAsBoolean()) {
        TheSettlerXCreate.LOGGER.info(
            "[CreateShop] attemptResolve skipped (missing stock network id)");
      }
      return Lists.newArrayList();
    }
    shop.ensurePickupLink();
    CreateShopBlockEntity pickup = shop.getPickupBlockEntity();
    if (pickup == null) {
      if (Config.DEBUG_LOGGING.getAsBoolean()) {
        TheSettlerXCreate.LOGGER.info("[CreateShop] attemptResolve skipped (pickup block missing)");
      }
      return Lists.newArrayList();
    }
    if (pickup.getLevel() == null) {
      if (Config.DEBUG_LOGGING.getAsBoolean()) {
        TheSettlerXCreate.LOGGER.info("[CreateShop] attemptResolve skipped (pickup level missing)");
      }
      return Lists.newArrayList();
    }

    int needed = deliverable.getCount();
    if (deliverable instanceof INonExhaustiveDeliverable nonExhaustive) {
      needed -= nonExhaustive.getLeftOver();
    }
    UUID requestId = toRequestId(request.getId());
    int reservedForRequest = pickup.getReservedForRequest(requestId);
    int reservedForDeliverable = pickup.getReservedForDeliverable(deliverable);
    needed = Math.max(0, needed - reservedForRequest);
    if (needed <= 0) {
      if (Config.DEBUG_LOGGING.getAsBoolean()) {
        TheSettlerXCreate.LOGGER.info("[CreateShop] attemptResolve skipped (needed<=0)");
      }
      return Lists.newArrayList();
    }
    if (!shop.isWorkerWorking()) {
      if (Config.DEBUG_LOGGING.getAsBoolean()) {
        TheSettlerXCreate.LOGGER.info(
            "[CreateShop] attemptResolve deferred (worker not working) request=" + request.getId());
      }
      markRequestOrdered(level, request.getId());
      pendingRequestCounts.put(request.getId(), needed);
      recordPendingSource(request.getId(), "attemptResolve:worker-not-working");
      return Lists.newArrayList();
    }

    ICreateNetworkFacade network = new CreateNetworkFacade(tile);
    int networkAvailable = network.getAvailable(deliverable);
    int rackAvailable = getAvailableFromRacks(tile, deliverable);
    int rackUsable = Math.max(0, rackAvailable - reservedForDeliverable);
    int available = Math.max(0, networkAvailable + rackUsable);
    int provide = Math.min(available, needed);
    if (provide <= 0) {
      if (Config.DEBUG_LOGGING.getAsBoolean()) {
        TheSettlerXCreate.LOGGER.info(
            "[CreateShop] attemptResolve aborted (available={}, reserved={}, needed={}) for {}",
            available,
            reservedForDeliverable,
            needed,
            deliverable);
      }
      // Track pending requests so we can detect items arriving in racks.
      markRequestOrdered(level, request.getId());
      pendingRequestCounts.put(request.getId(), needed);
      recordPendingSource(request.getId(), "attemptResolve:insufficient");
      return Lists.newArrayList();
    }

    List<com.minecolonies.api.util.Tuple<ItemStack, BlockPos>> planned =
        planFromRacksWithPositions(tile, deliverable, Math.min(provide, rackUsable));
    List<ItemStack> ordered = extractStacks(planned);
    int plannedCount = ordered.stream().mapToInt(ItemStack::getCount).sum();
    int remaining = Math.max(0, provide - plannedCount);
    if (remaining > 0) {
      ordered.addAll(network.requestItems(deliverable, remaining));
    }
    if (Config.DEBUG_LOGGING.getAsBoolean()) {
      TheSettlerXCreate.LOGGER.info(
          "[CreateShop] attemptResolve provide={} (available={}, reserved={}, needed={}) -> ordered {} stack(s)",
          provide,
          available,
          reservedForDeliverable,
          needed,
          ordered.size());
    }
    if (!ordered.isEmpty()) {
      // If we can satisfy from racks, create deliveries immediately.
      if (rackUsable > 0) {
        List<IToken<?>> created = createDeliveriesFromStacks(manager, request, planned, pickup);
        markDeliveriesCreated(request.getId());
        if (Config.DEBUG_LOGGING.getAsBoolean()) {
          TheSettlerXCreate.LOGGER.info(
              "[CreateShop] attemptResolve created deliveries parent={} manager={} tokens={}",
              request.getId(),
              manager.getClass().getName(),
              created);
        }
        return created;
      }
      // Otherwise, order from network and wait for arrival.
      markRequestOrdered(level, request.getId());
      pendingRequestCounts.put(request.getId(), needed);
      recordPendingSource(request.getId(), "attemptResolve:network-ordered");
      sendShopChat(manager, "com.thesettler_x_create.message.createshop.request_taken", ordered);
      sendShopChat(manager, "com.thesettler_x_create.message.createshop.request_sent", ordered);
    }

    // Reserve ordered items so they count as in-progress and avoid re-ordering.
    if (!ordered.isEmpty()) {
      for (ItemStack stack : ordered) {
        if (stack.isEmpty()) {
          continue;
        }
        pickup.reserve(requestId, stack.copy(), stack.getCount());
      }
    }

    // If we ordered from the network, we wait for arrival and do not create deliveries yet.
    if (rackUsable <= 0) {
      return Lists.newArrayList();
    }

    // Deliveries already created above for rack items.
    return Lists.newArrayList();
  }

  @Override
  public void resolveRequest(
      @NotNull IRequestManager manager, @NotNull IRequest<? extends IDeliverable> request) {
    // Do not complete requests that are still waiting on ordered goods.
    Level level = manager.getColony().getWorld();
    boolean ordered = orderedRequests.containsKey(request.getId());
    boolean cooldown = isRequestOnCooldown(level, request.getId());
    if (ordered || cooldown) {
      if (Config.DEBUG_LOGGING.getAsBoolean()) {
        TheSettlerXCreate.LOGGER.info(
            "[CreateShop] resolveRequest skip parent={} ordered={} cooldown={}",
            request.getId(),
            ordered,
            cooldown);
      }
      if (manager instanceof IStandardRequestManager standardManager) {
        logRequestStateChange(standardManager, request.getId(), "resolveRequest-skip");
      }
      return;
    }
    // Use Warehouse resolver behavior to mark parent resolved once children complete.
    super.resolveRequest(manager, request);
    if (Config.DEBUG_LOGGING.getAsBoolean()) {
      TheSettlerXCreate.LOGGER.info(
          "[CreateShop] resolveRequest parent={} state={}", request.getId(), request.getState());
    }
    if (manager instanceof IStandardRequestManager standardManager) {
      logRequestStateChange(standardManager, request.getId(), "resolveRequest");
    }
  }

  @Override
  public java.util.List<IRequest<?>> getFollowupRequestForCompletion(
      @NotNull IRequestManager manager, @NotNull IRequest<? extends IDeliverable> request) {
    // Avoid AbstractWarehouseRequestResolver followup logic (casts to TileEntityWareHouse).
    java.util.List<IRequest<?>> followups = java.util.Collections.emptyList();
    if (Config.DEBUG_LOGGING.getAsBoolean()) {
      int size = 0;
      TheSettlerXCreate.LOGGER.info(
          "[CreateShop] followup completion request={} state={} children={} followups={}",
          request.getId(),
          request.getState(),
          request.hasChildren(),
          size);
    }
    if (manager instanceof IStandardRequestManager standardManager) {
      logRequestStateChange(standardManager, request.getId(), "followup");
    }
    return followups;
  }

  public void tickPendingDeliveries(IRequestManager manager) {
    if (!(manager instanceof IStandardRequestManager standardManager)) {
      return;
    }
    long perfStart = System.nanoTime();
    Level level = manager.getColony().getWorld();
    if (level == null) {
      return;
    }
    if (level.isClientSide) {
      return;
    }
    processParentChildRechecks(standardManager, level);
    var assignmentStore = standardManager.getRequestResolverRequestAssignmentDataStore();
    var requestHandler = standardManager.getRequestHandler();
    Map<IToken<?>, java.util.Collection<IToken<?>>> assignments = assignmentStore.getAssignments();
    var assigned = assignments.get(getId());
    java.util.Set<IToken<?>> pendingTokens = new java.util.HashSet<>();
    if (assigned != null) {
      pendingTokens.addAll(assigned);
    }
    pendingTokens.addAll(orderedRequests.keySet());
    pendingTokens.addAll(pendingRequestCounts.keySet());
    if (pendingTokens.isEmpty()) {
      if (Config.DEBUG_LOGGING.getAsBoolean() && shouldLogTickPending(level)) {
        if (!orderedRequests.isEmpty() || !pendingRequestCounts.isEmpty()) {
          TheSettlerXCreate.LOGGER.info(
              "[CreateShop] tickPending: empty snapshot but maps ordered={} pendingCounts={}",
              orderedRequests.size(),
              pendingRequestCounts.size());
        }
      }
      if (Config.DEBUG_LOGGING.getAsBoolean() && shouldLogTickPending(level)) {
        TheSettlerXCreate.LOGGER.info(
            "[CreateShop] tickPending: no assigned or ordered requests for resolver {}", getId());
      }
      return;
    }
    if (Config.DEBUG_LOGGING.getAsBoolean() && shouldLogTickPending(level)) {
      int assignedCount = assigned == null ? 0 : assigned.size();
      int orderedCount = orderedRequests.size();
      TheSettlerXCreate.LOGGER.info(
          "[CreateShop] tickPending: assigned={}, ordered={}, total={}",
          assignedCount,
          orderedCount,
          pendingTokens.size());
      int logged = 0;
      for (IToken<?> token : java.util.List.copyOf(pendingTokens)) {
        if (logged >= 5) {
          break;
        }
        try {
          IRequest<?> req = requestHandler.getRequest(token);
          String type = req == null ? "<null>" : req.getRequest().getClass().getName();
          String state = req == null ? "<null>" : req.getState().toString();
          TheSettlerXCreate.LOGGER.info(
              "[CreateShop] tickPending: candidate {} type={} state={}", token, type, state);
          logged++;
        } catch (IllegalArgumentException ignored) {
          // Missing request.
        }
      }
    }
    BuildingCreateShop shop = getShop(manager);
    if (shop == null) {
      return;
    }
    if (!shop.isWorkerWorking()) {
      if (Config.DEBUG_LOGGING.getAsBoolean() && shouldLogTickPending(level)) {
        TheSettlerXCreate.LOGGER.info("[CreateShop] tickPending skipped (worker not working)");
      }
      return;
    }
    TileEntityCreateShop tile = shop.getCreateShopTileEntity();
    if (tile == null) {
      return;
    }
    CreateShopBlockEntity pickup = shop.getPickupBlockEntity();
    if (pickup == null) {
      return;
    }

    for (IToken<?> token : java.util.List.copyOf(pendingTokens)) {
      IRequest<?> request;
      try {
        request = requestHandler.getRequest(token);
      } catch (IllegalArgumentException ex) {
        orderedRequests.remove(token);
        pendingRequestCounts.remove(token);
        pendingSources.remove(token);
        continue;
      }
      if (cancelledRequests.contains(request.getId())) {
        if (request.getState()
            != com.minecolonies.api.colony.requestsystem.request.RequestState.CANCELLED) {
          cancelledRequests.remove(request.getId());
        } else {
          orderedRequests.remove(request.getId());
          pendingRequestCounts.remove(request.getId());
          pendingSources.remove(request.getId());
          clearRequestCooldown(request.getId());
          clearDeliveriesCreated(request.getId());
          logPendingReasonChange(request.getId(), "skip:cancelled");
          if (Config.DEBUG_LOGGING.getAsBoolean()) {
            TheSettlerXCreate.LOGGER.info(
                "[CreateShop] tickPending: " + request.getId() + " skip (cancelled)");
          }
          continue;
        }
      }
      if (request.getState()
          == com.minecolonies.api.colony.requestsystem.request.RequestState.CANCELLED) {
        if (Config.DEBUG_LOGGING.getAsBoolean()) {
          TheSettlerXCreate.LOGGER.info(
              "[CreateShop] tickPending: {} skip (state cancelled)", request.getId());
        }
        continue;
      }
      logRequestStateChange(standardManager, token, "tickPending");
      if (!(request.getRequest() instanceof IDeliverable deliverable)) {
        logPendingReasonChange(token, "skip:not-deliverable");
        continue;
      }
      String requestIdLog = request.getId().toString();
      UUID requestId = toRequestId(request.getId());
      int reservedForRequest = pickup.getReservedForRequest(requestId);
      boolean onCooldown = isRequestOnCooldown(level, request.getId());
      if (!onCooldown && reservedForRequest <= 0) {
        logPendingReasonChange(
            request.getId(),
            "skip:no-cooldown reserved="
                + reservedForRequest
                + " pending="
                + pendingRequestCounts.getOrDefault(request.getId(), 0));
        if (Config.DEBUG_LOGGING.getAsBoolean()) {
          TheSettlerXCreate.LOGGER.info(
              "[CreateShop] tickPending: {} skip (not on cooldown)", requestIdLog);
        }
        continue;
      }
      if (hasDeliveriesCreated(request.getId())) {
        logPendingReasonChange(request.getId(), "skip:deliveries-created");
        if (Config.DEBUG_LOGGING.getAsBoolean()) {
          TheSettlerXCreate.LOGGER.info(
              "[CreateShop] tickPending: {} skip (deliveries already created)", requestIdLog);
        }
        continue;
      }
      if (request.hasChildren()) {
        logPendingReasonChange(request.getId(), "skip:has-children");
        java.util.Collection<IToken<?>> children =
            java.util.Objects.requireNonNull(request.getChildren(), "children");
        int missing = 0;
        if (!children.isEmpty()) {
          for (IToken<?> childToken : java.util.List.copyOf(children)) {
            try {
              IRequest<?> child = requestHandler.getRequest(childToken);
              if (child == null) {
                missing++;
                request.removeChild(childToken);
              }
              if (child != null
                  && child.getRequest()
                      instanceof
                      com.minecolonies.api.colony.requestsystem.requestable.deliveryman.Delivery) {
                var childAssigned = assignmentStore.getAssignmentForValue(childToken);
                if (childAssigned == null) {
                  boolean enqueued = tryEnqueueDelivery(standardManager, childToken);
                  if (Config.DEBUG_LOGGING.getAsBoolean()) {
                    TheSettlerXCreate.LOGGER.info(
                        "[CreateShop] tickPending: {} child {} unassigned delivery -> enqueue={}",
                        requestIdLog,
                        childToken,
                        enqueued ? "ok" : "none");
                  }
                }
              }
              if (Config.DEBUG_LOGGING.getAsBoolean()) {
                String childType =
                    child == null ? "<null>" : child.getRequest().getClass().getName();
                String childState = child == null ? "<null>" : child.getState().toString();
                TheSettlerXCreate.LOGGER.info(
                    "[CreateShop] tickPending: {} child {} type={} state={}",
                    requestIdLog,
                    childToken,
                    childType,
                    childState);
                if (child == null) {
                  TheSettlerXCreate.LOGGER.info(
                      "[CreateShop] tickPending: {} child {} missing -> removed",
                      requestIdLog,
                      childToken);
                }
              }
            } catch (Exception ex) {
              missing++;
              request.removeChild(childToken);
              if (Config.DEBUG_LOGGING.getAsBoolean()) {
                TheSettlerXCreate.LOGGER.info(
                    "[CreateShop] tickPending: {} child {} lookup failed -> removed: {}",
                    requestIdLog,
                    childToken,
                    ex.getMessage() == null ? "<null>" : ex.getMessage());
              }
            }
          }
        }
        if (Config.DEBUG_LOGGING.getAsBoolean()) {
          TheSettlerXCreate.LOGGER.info(
              "[CreateShop] tickPending: {} skip (has children)", requestIdLog);
          logParentChildrenState(standardManager, request.getId(), "tickPending");
          if (children.isEmpty()) {
            TheSettlerXCreate.LOGGER.info(
                "[CreateShop] tickPending: {} children list empty despite hasChildren",
                requestIdLog);
          } else if (missing > 0) {
            TheSettlerXCreate.LOGGER.info(
                "[CreateShop] tickPending: {} missing children={} total={}",
                requestIdLog,
                missing,
                children.size());
          }
        }
        if (missing > 0) {
          continue;
        }
        continue;
      }

      if (!onCooldown && Config.DEBUG_LOGGING.getAsBoolean()) {
        TheSettlerXCreate.LOGGER.info(
            "[CreateShop] tickPending: {} proceed (cooldown cleared, reservedForRequest={})",
            requestIdLog,
            reservedForRequest);
      }
      int pendingCount = reservedForRequest;
      if (pendingCount <= 0) {
        pendingCount = pendingRequestCounts.getOrDefault(request.getId(), 0);
      }
      if (pendingCount <= 0) {
        logPendingReasonChange(
            request.getId(),
            "skip:pending-count reserved=" + reservedForRequest + " pending=" + pendingCount);
        if (Config.DEBUG_LOGGING.getAsBoolean()) {
          TheSettlerXCreate.LOGGER.info(
              "[CreateShop] tickPending: {} skip (reservedForRequest={}, pendingCount={})",
              requestIdLog,
              reservedForRequest,
              pendingCount);
        }
        continue;
      }
      int rackAvailable = getAvailableFromRacks(tile, deliverable);
      int pickupAvailable = getAvailableFromPickup(pickup, deliverable);
      int totalAvailable = rackAvailable + pickupAvailable;
      if (totalAvailable < pendingCount) {
        logPendingReasonChange(
            request.getId(),
            "wait:available="
                + totalAvailable
                + " rack="
                + rackAvailable
                + " pickup="
                + pickupAvailable
                + " pending="
                + pendingCount);
        if (shouldNotifyPending(level, request.getId())) {
          sendShopChat(
              manager,
              "com.thesettler_x_create.message.createshop.delivery_waiting",
              java.util.Collections.singletonList(deliverable.getResult()));
        }
        if (Config.DEBUG_LOGGING.getAsBoolean()) {
          TheSettlerXCreate.LOGGER.info(
              "[CreateShop] tickPending: {} waiting (available={}, rackAvailable={}, pickupAvailable={}, pendingCount={})",
              requestIdLog,
              totalAvailable,
              rackAvailable,
              pickupAvailable,
              pendingCount);
        }
        continue;
      }
      List<com.minecolonies.api.util.Tuple<ItemStack, BlockPos>> stacks =
          planFromPickupWithPositions(pickup, deliverable, pendingCount);
      int plannedCount = countPlanned(stacks);
      if (plannedCount < pendingCount) {
        stacks.addAll(
            planFromRacksWithPositions(tile, deliverable, pendingCount - plannedCount));
      }
      if (stacks.isEmpty()) {
        logPendingReasonChange(request.getId(), "wait:plan-empty");
        if (Config.DEBUG_LOGGING.getAsBoolean()) {
          TheSettlerXCreate.LOGGER.info(
              "[CreateShop] tickPending: {} skip (plan empty, rackAvailable={}, pickupAvailable={}, pendingCount={})",
              requestIdLog,
              rackAvailable,
              pickupAvailable,
              pendingCount);
        }
        continue;
      }
      if (Config.DEBUG_LOGGING.getAsBoolean()) {
        TheSettlerXCreate.LOGGER.info(
            "[CreateShop] tickPending: {} creating deliveries (stacks={}, pendingCount={}, rackAvailable={}, pickupAvailable={})",
            requestIdLog,
            stacks.size(),
            pendingCount,
            rackAvailable,
            pickupAvailable);
      }
      List<IToken<?>> created = createDeliveriesFromStacks(manager, request, stacks, pickup);
      if (created.isEmpty()) {
        logPendingReasonChange(request.getId(), "create:failed");
        if (Config.DEBUG_LOGGING.getAsBoolean()) {
          TheSettlerXCreate.LOGGER.info(
              "[CreateShop] tickPending: {} create failed (no deliveries created)", requestIdLog);
        }
        continue;
      }
      List<ItemStack> ordered = extractStacks(stacks);
      sendShopChat(manager, "com.thesettler_x_create.message.createshop.goods_arrived", ordered);
      sendShopChat(manager, "com.thesettler_x_create.message.createshop.delivery_created", ordered);
      logPendingReasonChange(request.getId(), "create:delivery");
      if (reservedForRequest > 0) {
        pickup.release(requestId);
      }
      pendingRequestCounts.remove(request.getId());
      clearRequestCooldown(request.getId());
      if (Config.DEBUG_LOGGING.getAsBoolean()) {
        TheSettlerXCreate.LOGGER.info(
            "[CreateShop] tickPending: {} skip assignRequest (delivery created)", requestIdLog);
      }
    }
    lastTickPendingNanos = System.nanoTime() - perfStart;
    maybeLogPerf(level);
  }

  private boolean shouldLogTickPending(Level level) {
    long now = level.getGameTime();
    if (now == 0L
        || now - lastTickPendingDebugTime >= Config.TICK_PENDING_DEBUG_COOLDOWN.getAsLong()) {
      lastTickPendingDebugTime = now;
      return true;
    }
    return false;
  }

  private void maybeLogPerf(Level level) {
    if (!Config.DEBUG_LOGGING.getAsBoolean()) {
      return;
    }
    if (level == null) {
      return;
    }
    long now = level.getGameTime();
    if (now != 0L && now - lastPerfLogTime < Config.PERF_LOG_COOLDOWN.getAsLong()) {
      return;
    }
    lastPerfLogTime = now;
    TheSettlerXCreate.LOGGER.info(
        "[CreateShop] perf tickPending={}us", lastTickPendingNanos / 1000L);
  }

  public static void onDeliveryCancelled(IRequestManager manager, IRequest<?> request) {
    CreateShopRequestResolver resolver = findResolverForDelivery(manager, request);
    if (resolver != null) {
      resolver.handleDeliveryCancelled(manager, request);
    }
  }

  public static void onDeliveryComplete(IRequestManager manager, IRequest<?> request) {
    CreateShopRequestResolver resolver = findResolverForDelivery(manager, request);
    if (resolver != null) {
      resolver.handleDeliveryComplete(manager, request);
    }
  }

  private static CreateShopRequestResolver findResolverForDelivery(
      IRequestManager manager, IRequest<?> request) {
    if (manager == null) {
      return null;
    }
    IStandardRequestManager standard = unwrapStandardManager(manager);
    if (standard == null) {
      return null;
    }
    IRequest<?> parent = null;
    try {
      IToken<?> parentToken = request.getParent();
      if (parentToken != null && standard.getRequestHandler() != null) {
        parent = standard.getRequestHandler().getRequest(parentToken);
      }
    } catch (Exception ignored) {
      // Ignore lookup errors.
    }
    try {
      var resolver =
          parent == null
              ? standard.getResolverHandler().getResolverForRequest(request)
              : standard.getResolverHandler().getResolverForRequest(parent);
      if (resolver instanceof CreateShopRequestResolver shopResolver) {
        return shopResolver;
      }
    } catch (Exception ignored) {
      // Ignore lookup errors.
    }
    return null;
  }

  private void handleDeliveryCancelled(IRequestManager manager, IRequest<?> request) {
    if (!(request.getRequest() instanceof Delivery delivery)) {
      return;
    }
    IToken<?> parentToken = request.getParent();
    if (parentToken == null) {
      parentToken = deliveryParents.remove(request.getId());
    } else {
      deliveryParents.remove(request.getId());
    }
    if (parentToken == null) {
      return;
    }
    UUID parentRequestId = toRequestId(parentToken);
    ItemStack stack = delivery.getStack().copy();

    Level level = manager.getColony().getWorld();
    if (level == null) {
      pendingRequestCounts.put(parentToken, Math.max(1, stack.getCount()));
      recordPendingSource(parentToken, "delivery-cancel");
      clearDeliveriesCreated(parentToken);
      return;
    }
    CreateShopBlockEntity pickup = null;
    BuildingCreateShop shop = getShop(manager);
    if (shop != null) {
      pickup = shop.getPickupBlockEntity();
    }
    if (pickup == null) {
      BlockPos start = delivery.getStart().getInDimensionLocation();
      BlockEntity entity = level.getBlockEntity(start);
      if (entity instanceof CreateShopBlockEntity shopPickup) {
        pickup = shopPickup;
      }
    }

    int reservedForRequest = pickup == null ? 0 : pickup.getReservedForRequest(parentRequestId);
    int pendingCount = Math.max(1, Math.max(reservedForRequest, stack.getCount()));
    if (pickup != null) {
      pickup.release(parentRequestId);
    }
    pendingRequestCounts.put(parentToken, pendingCount);
    recordPendingSource(parentToken, "delivery-cancel-reserve");
    // Keep on cooldown so tickPending can retry once stock is available.
    markRequestOrdered(level, parentToken);
    clearDeliveriesCreated(parentToken);

    if (Config.DEBUG_LOGGING.getAsBoolean()) {
      int reservedForStack = pickup == null ? 0 : pickup.getReservedFor(stack);
      BlockPos pickupPosition =
          pickup == null ? delivery.getStart().getInDimensionLocation() : pickup.getBlockPos();
      logDeliveryDiagnostics(
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
          pickup == null ? "<none>" : pickup.getBlockPos());
      IStandardRequestManager standard = unwrapStandardManager(manager);
      if (standard != null) {
        logParentChildrenState(standard, parentToken, "delivery-cancel");
        scheduleParentChildRecheck(standard, parentToken);
      }
    }
  }

  private void handleDeliveryComplete(IRequestManager manager, IRequest<?> request) {
    IToken<?> parentToken = request.getParent();
    if (parentToken == null) {
      parentToken = deliveryParents.remove(request.getId());
    } else {
      deliveryParents.remove(request.getId());
    }
    if (parentToken == null) {
      return;
    }
    if (Config.DEBUG_LOGGING.getAsBoolean()
        && request != null
        && request.getRequest() instanceof Delivery delivery) {
      try {
        CreateShopBlockEntity pickup = null;
        Level level = manager == null ? null : manager.getColony().getWorld();
        BlockPos startPos = delivery.getStart().getInDimensionLocation();
        if (level != null && WorldUtil.isBlockLoaded(level, startPos)) {
          BlockEntity startEntity = level.getBlockEntity(startPos);
          if (startEntity instanceof CreateShopBlockEntity shopPickup) {
            pickup = shopPickup;
          }
        }
        UUID parentRequestId = toRequestId(parentToken);
        ItemStack stack = delivery.getStack().copy();
        int reservedForRequest = pickup == null ? 0 : pickup.getReservedForRequest(parentRequestId);
        int reservedForStack = pickup == null ? 0 : pickup.getReservedFor(stack);
        BlockPos pickupPosition = pickup == null ? startPos : pickup.getBlockPos();
        logDeliveryDiagnostics(
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
      } catch (Exception ignored) {
        // Ignore delivery detail logging failures.
      }
    }
    clearDeliveriesCreated(parentToken);
    orderedRequests.remove(parentToken);
    pendingRequestCounts.remove(parentToken);
    clearRequestCooldown(parentToken);
    pendingRequestCounts.remove(parentToken);
    if (Config.DEBUG_LOGGING.getAsBoolean()) {
      IStandardRequestManager standard = unwrapStandardManager(manager);
      if (standard != null) {
        try {
          var handler = standard.getRequestHandler();
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
          logParentChildrenState(standard, parentToken, "delivery-complete");
          scheduleParentChildRecheck(standard, parentToken);
        } catch (Exception ignored) {
          // Ignore lookup errors.
        }
      }
    }
  }

  @Override
  public void onAssignedRequestBeingCancelled(
      @NotNull IRequestManager manager, @NotNull IRequest<? extends IDeliverable> request) {
    orderedRequests.remove(request.getId());
    pendingRequestCounts.remove(request.getId());
    pendingSources.remove(request.getId());
    releaseReservation(manager, request);
    clearRequestCooldown(request.getId());
    clearDeliveriesCreated(request.getId());
  }

  @Override
  public void onAssignedRequestCancelled(
      @NotNull IRequestManager manager, @NotNull IRequest<? extends IDeliverable> request) {
    orderedRequests.remove(request.getId());
    pendingRequestCounts.remove(request.getId());
    pendingSources.remove(request.getId());
    releaseReservation(manager, request);
    clearRequestCooldown(request.getId());
    clearDeliveriesCreated(request.getId());
  }

  @Override
  public void onRequestedRequestComplete(
      @NotNull IRequestManager manager, @NotNull IRequest<?> request) {
    clearRequestCooldown(request.getId());
    clearDeliveriesCreated(request.getId());
    pendingRequestCounts.remove(request.getId());
    pendingSources.remove(request.getId());
    if (request.getRequest() instanceof IDeliverable) {
      releaseReservation(manager, request);
    }
  }

  @Override
  public void onRequestedRequestCancelled(
      @NotNull IRequestManager manager, @NotNull IRequest<?> request) {
    orderedRequests.remove(request.getId());
    pendingRequestCounts.remove(request.getId());
    pendingSources.remove(request.getId());
    clearRequestCooldown(request.getId());
    clearDeliveriesCreated(request.getId());
    if (request.getRequest() instanceof IDeliverable) {
      releaseReservation(manager, request);
    }
  }

  @Override
  public int getSuitabilityMetric(
      @NotNull IRequestManager manager, @NotNull IRequest<? extends IDeliverable> request) {
    int distance =
        (int)
            BlockPosUtil.getDistance(
                request.getRequester().getLocation().getInDimensionLocation(),
                getLocation().getInDimensionLocation());
    return Math.max(1, distance / 10);
  }

  @Override
  protected int getWarehouseInternalCount(
      com.minecolonies.core.colony.buildings.workerbuildings.BuildingWareHouse ignored,
      IRequest<? extends IDeliverable> request) {
    IDeliverable deliverable = request.getRequest();
    var colonyManager = com.minecolonies.api.colony.IColonyManager.getInstance();
    var colony =
        colonyManager.getColonyByPosFromDim(
            getLocation().getDimension(), getLocation().getInDimensionLocation());
    var building =
        colony.getServerBuildingManager().getBuilding(getLocation().getInDimensionLocation());
    BuildingCreateShop shop = building instanceof BuildingCreateShop createShop ? createShop : null;
    if (shop == null) {
      return 0;
    }
    TileEntityCreateShop tile = shop.getCreateShopTileEntity();
    if (tile == null || tile.getStockNetworkId() == null) {
      return 0;
    }
    CreateShopBlockEntity pickup = shop.getPickupBlockEntity();
    if (pickup == null) {
      return 0;
    }
    ICreateNetworkFacade network = new CreateNetworkFacade(tile);
    int available = network.getAvailable(deliverable);
    int reserved = pickup.getReservedForDeliverable(deliverable);
    return Math.max(0, available - reserved);
  }

  private BuildingCreateShop getShop(IRequestManager manager) {
    if (manager == null) {
      return null;
    }
    IColony colony = manager.getColony();
    var building =
        colony.getServerBuildingManager().getBuilding(getLocation().getInDimensionLocation());
    if (building instanceof BuildingCreateShop shop) {
      return shop;
    }
    return null;
  }

  private void releaseReservation(IRequestManager manager, IRequest<?> request) {
    BuildingCreateShop shop = getShop(manager);
    if (shop == null) {
      return;
    }
    CreateShopBlockEntity pickup = shop.getPickupBlockEntity();
    if (pickup == null) {
      return;
    }
    pickup.release(toRequestId(request.getId()));
  }

  private UUID toRequestId(IToken<?> token) {
    Object id = token == null ? null : token.getIdentifier();
    if (id instanceof UUID uuid) {
      return uuid;
    }
    return UUID.nameUUIDFromBytes(
        String.valueOf(id).getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }

  private void sendShopChat(IRequestManager manager, String key, List<ItemStack> stacks) {
    if (!Config.CHAT_MESSAGES_ENABLED.getAsBoolean()) {
      return;
    }
    if (stacks == null || stacks.isEmpty()) {
      return;
    }
    for (ItemStack stack : stacks) {
      if (stack.isEmpty()) {
        continue;
      }
      MessageUtils.format(key, stack.getHoverName().getString(), stack.getCount())
          .sendTo(manager.getColony())
          .forAllPlayers();
    }
  }

  private List<IToken<?>> createDeliveriesFromStacks(
      IRequestManager manager,
      IRequest<?> request,
      List<com.minecolonies.api.util.Tuple<ItemStack, BlockPos>> stacks,
      CreateShopBlockEntity pickup) {
    if (manager == null || pickup == null || stacks == null) {
      return Lists.newArrayList();
    }
    BlockPos startPos = pickup.getBlockPos();
    ItemStack selected = null;
    for (var entry : stacks) {
      if (entry == null) {
        continue;
      }
      ItemStack stack = entry.getA();
      if (stack.isEmpty()) {
        continue;
      }
      selected = stack.copy();
      if (entry.getB() != null) {
        startPos = entry.getB();
      }
      break;
    }
    if (selected == null) {
      return Lists.newArrayList();
    }
    var factory = manager.getFactoryController();
    Level pickupLevel = pickup.getLevel();
    if (pickupLevel == null) {
      return Lists.newArrayList();
    }
    var pickupLocation =
        factory.getNewInstance(TypeConstants.ILOCATION, startPos, pickupLevel.dimension());
    if (Config.DEBUG_LOGGING.getAsBoolean()) {
      try {
        var targetLoc = request.getRequester().getLocation();
        var targetPos = targetLoc.getInDimensionLocation();
        var pickupBlock = pickupLevel.getBlockState(startPos).getBlock();
        var targetBlock = pickupLevel.getBlockState(targetPos).getBlock();
        TheSettlerXCreate.LOGGER.info(
            "[CreateShop] delivery location pickupPosition={} pickupBlock={} targetPosition={} targetBlock={}",
            startPos,
            pickupBlock,
            targetPos,
            targetBlock);
      } catch (Exception ignored) {
        // Do not fail delivery creation for debug output.
      }
    }
    if (hasDeliveriesCreated(request.getId())) {
      return Lists.newArrayList();
    }
    markDeliveriesCreated(request.getId());
    request.addDelivery(selected.copy());
    Delivery delivery =
        new Delivery(
            pickupLocation,
            request.getRequester().getLocation(),
            selected.copy(),
            AbstractDeliverymanRequestable.getDefaultDeliveryPriority(true));
    var requester = request.getRequester();
    if (!(requester instanceof SafeRequester)) {
      requester = new SafeRequester(requester);
    }
    IToken<?> token;
    try {
      token = manager.createRequest(requester, delivery);
    } catch (Exception ex) {
      if (Config.DEBUG_LOGGING.getAsBoolean()) {
        TheSettlerXCreate.LOGGER.info(
            "[CreateShop] delivery create failed requester={} error={}",
            requester.getClass().getName(),
            ex.getMessage() == null ? "<null>" : ex.getMessage());
      }
      return Lists.newArrayList();
    }
    deliveryParents.put(token, request.getId());
    if (Config.DEBUG_LOGGING.getAsBoolean()) {
      String key = token.toString();
      if (deliveryCreateLogged.add(key)) {
        TheSettlerXCreate.LOGGER.info(
            "[CreateShop] delivery create token={} requesterClass={} managerClass={}",
            token,
            requester.getClass().getName(),
            manager.getClass().getName());
        IStandardRequestManager standard = unwrapStandardManager(manager);
        if (standard != null && standard.getRequestHandler() != null) {
          try {
            IRequest<?> created = standard.getRequestHandler().getRequest(token);
            String createdRequester = created.getRequester().getClass().getName();
            IToken<?> childParent = created.getParent();
            TheSettlerXCreate.LOGGER.info(
                "[CreateShop] delivery create handler token={} createdRequester={} childParent={}",
                token,
                createdRequester,
                childParent == null ? "<none>" : childParent);
          } catch (Exception ex) {
            TheSettlerXCreate.LOGGER.info(
                "[CreateShop] delivery create handler token={} error={}",
                token,
                ex.getMessage() == null ? "<null>" : ex.getMessage());
          }
        } else {
          TheSettlerXCreate.LOGGER.info(
              "[CreateShop] delivery create handler unavailable token={} unwrappedManager={}",
              token,
              standard == null ? "<null>" : "ok");
        }
      }
    }
    IStandardRequestManager standard = unwrapStandardManager(manager);
    if (standard != null) {
      var assignmentStore = standard.getRequestResolverRequestAssignmentDataStore();
      var assigned = assignmentStore.getAssignmentForValue(token);
      if (assigned == null) {
        try {
          IRequest<?> created = standard.getRequestHandler().getRequest(token);
          standard.getRequestHandler().assignRequest(created);
        } catch (Exception ex) {
          if (Config.DEBUG_LOGGING.getAsBoolean()) {
            TheSettlerXCreate.LOGGER.info(
                "[CreateShop] delivery fallback assign failed token={} error={}",
                token,
                ex.getMessage() == null ? "<null>" : ex.getMessage());
          }
        }
        // If still unassigned, enqueue into a warehouse request queue with couriers.
        var assignedAfter = assignmentStore.getAssignmentForValue(token);
        if (assignedAfter == null) {
          boolean enqueued = tryEnqueueDelivery(standard, token);
          if (Config.DEBUG_LOGGING.getAsBoolean()) {
            TheSettlerXCreate.LOGGER.info(
                "[CreateShop] delivery fallback enqueue token={} result={}",
                token,
                enqueued ? "ok" : "none");
          }
        }
      }
    }
    if (Config.DEBUG_LOGGING.getAsBoolean()) {
      int reservedForRequest = pickup.getReservedForRequest(toRequestId(request.getId()));
      int reservedForDeliverable = -1;
      if (request.getRequest() instanceof IDeliverable deliverable) {
        reservedForDeliverable = pickup.getReservedForDeliverable(deliverable);
      }
      int reservedForStack = pickup.getReservedFor(selected);
      logDeliveryDiagnostics(
          "create",
          manager,
          token,
          toRequestId(request.getId()),
          startPos,
          selected,
          request.getRequester().getLocation(),
          reservedForRequest,
          reservedForDeliverable,
          reservedForStack);
    }
    if (manager instanceof IStandardRequestManager standardManager) {
      try {
        var handler = standardManager.getRequestHandler();
        IRequest<?> child = handler == null ? null : handler.getRequest(token);
        if (child != null) {
          if (child.getParent() == null) {
            child.setParent(request.getId());
          }
          var children = request.getChildren();
          if (!children.contains(token)) {
            request.addChild(token);
          }
          if (request.getState()
              != com.minecolonies.api.colony.requestsystem.request.RequestState
                  .FOLLOWUP_IN_PROGRESS) {
            standardManager.updateRequestState(
                request.getId(),
                com.minecolonies.api.colony.requestsystem.request.RequestState
                    .FOLLOWUP_IN_PROGRESS);
            logRequestStateChange(standardManager, request.getId(), "delivery-link");
          }
          logRequestStateChange(standardManager, token, "delivery-link-child");
          if (Config.DEBUG_LOGGING.getAsBoolean()) {
            logDeliveryLinkState("link", standardManager, request.getId(), token);
          }
        }
      } catch (Exception ex) {
        if (Config.DEBUG_LOGGING.getAsBoolean()) {
          TheSettlerXCreate.LOGGER.info(
              "[CreateShop] delivery link failed token={} error={}",
              token,
              ex.getMessage() == null ? "<null>" : ex.getMessage());
        }
      }
    }
    if (manager instanceof IStandardRequestManager standardManager) {
      try {
        var created = standardManager.getRequestHandler().getRequest(token);
        standardManager.getRequestHandler().assignRequest(created);
      } catch (IllegalArgumentException ignored) {
        // Request missing; leave unassigned.
      } catch (Exception ex) {
        if (Config.DEBUG_LOGGING.getAsBoolean()) {
          TheSettlerXCreate.LOGGER.info(
              "[CreateShop] delivery assign failed {}: {}", token, ex.getMessage());
        }
      }
    }
    if (Config.DEBUG_LOGGING.getAsBoolean()) {
      var created = manager.getRequestForToken(token);
      if (manager instanceof IStandardRequestManager standardManager) {
        var store = standardManager.getRequestResolverRequestAssignmentDataStore();
        var assigned = store.getAssignmentForValue(token);
        String resolverForRequest = "<unknown>";
        String resolverError = "<none>";
        if (assigned == null) {
          try {
            var resolver = standardManager.getResolverHandler().getResolverForRequest(created);
            resolverForRequest = resolver == null ? "<none>" : resolver.getClass().getName();
          } catch (IllegalArgumentException ex) {
            resolverForRequest = "<none>";
            resolverError = ex.getMessage() == null ? "<none>" : ex.getMessage();
          }
        }
        TheSettlerXCreate.LOGGER.info(
            "[CreateShop] delivery created {} state={} assigned={} resolverForRequest={} resolverError={}",
            token,
            created == null ? "<null>" : created.getState(),
            assigned == null ? "<none>" : assigned,
            resolverForRequest,
            resolverError);
        if (assigned != null) {
          var assignmentStore = standardManager.getRequestResolverRequestAssignmentDataStore();
          var assignments = assignmentStore.getAssignments();
          var assignedRequests = assignments.get(assigned);
          boolean inAssignments = assignedRequests != null && assignedRequests.contains(token);
          String assignedResolverName = "<missing>";
          String assignedResolverInfo = "<none>";
          try {
            var assignedResolver = standardManager.getResolverHandler().getResolver(assigned);
            assignedResolverName = assignedResolver.getClass().getName();
            if (assignedResolver
                instanceof
                com.minecolonies.core.colony.requestsystem.resolvers.DeliveryRequestResolver) {
              assignedResolverInfo = tryDescribeResolver(assignedResolver);
            }
          } catch (IllegalArgumentException ignored) {
            // Missing resolver.
          }
          TheSettlerXCreate.LOGGER.info(
              "[CreateShop] delivery assigned resolver={} class={} inAssignments={} state={} info={}",
              assigned,
              assignedResolverName,
              inAssignments,
              created.getState(),
              assignedResolverInfo);
        } else {
          TheSettlerXCreate.LOGGER.info("[CreateShop] delivery assigned resolver=<none> store=ok");
        }
        if (assigned == null && "<none>".equals(resolverForRequest)) {
          Level level = manager.getColony().getWorld();
          long now = level == null ? 0L : level.getGameTime();
          if (now == 0L
              || now - lastDeliveryAssignmentDebugTime
                  >= Config.DELIVERY_ASSIGNMENT_DEBUG_COOLDOWN.getAsLong()) {
            lastDeliveryAssignmentDebugTime = now;
            var typeStore = standardManager.getRequestableTypeRequestResolverAssignmentDataStore();
            var typeAssignments = typeStore.getAssignments();
            java.util.Collection<IToken<?>> requestableResolvers =
                typeAssignments.get(TypeConstants.REQUESTABLE);
            if (requestableResolvers == null) {
              requestableResolvers = com.google.common.collect.ImmutableList.of();
            }
            int resolverCount = requestableResolvers.size();
            int matchingResolvers = 0;
            java.util.List<String> examples = new java.util.ArrayList<>();
            int canResolveCount = 0;
            java.util.List<String> canResolveExamples = new java.util.ArrayList<>();
            java.util.List<String> canResolveErrors = new java.util.ArrayList<>();
            for (var resolverToken : requestableResolvers) {
              try {
                var resolver = standardManager.getResolverHandler().getResolver(resolverToken);
                com.google.common.reflect.TypeToken<?> typeToken = resolver.getRequestType();
                Class<?> requestType = typeToken == null ? null : typeToken.getRawType();
                boolean matches =
                    requestType != null
                        && (requestType.isAssignableFrom(Delivery.class)
                            || requestType.isAssignableFrom(
                                com.minecolonies.api.colony.requestsystem.requestable.deliveryman
                                    .IDeliverymanRequestable.class)
                            || requestType.isAssignableFrom(
                                com.minecolonies.api.colony.requestsystem.requestable.IRequestable
                                    .class));
                if (matches) {
                  matchingResolvers++;
                  if (examples.size() < 5) {
                    examples.add(resolver.getClass().getName());
                  }
                }
                boolean canResolve = false;
                try {
                  var method =
                      resolver
                          .getClass()
                          .getMethod("canResolveRequest", IRequestManager.class, IRequest.class);
                  Object result = method.invoke(resolver, manager, created);
                  if (result instanceof Boolean bool) {
                    canResolve = bool;
                  }
                } catch (Exception ex) {
                  if (canResolveErrors.size() < 3) {
                    String msg = ex.getMessage();
                    canResolveErrors.add(
                        resolver.getClass().getName() + (msg == null ? "" : " -> " + msg));
                  }
                }
                if (canResolve) {
                  canResolveCount++;
                  if (canResolveExamples.size() < 5) {
                    canResolveExamples.add(resolver.getClass().getName());
                  }
                }
              } catch (IllegalArgumentException ignored) {
                // Missing resolver.
              }
            }
            TheSettlerXCreate.LOGGER.info(
                "[CreateShop] delivery assignment check: requestableResolvers={} matchingDeliveryResolvers={} examples={}",
                resolverCount,
                matchingResolvers,
                examples.isEmpty() ? "<none>" : examples);
            TheSettlerXCreate.LOGGER.info(
                "[CreateShop] delivery assignment canResolve: canResolveResolvers={} examples={} errors={}",
                canResolveCount,
                canResolveExamples.isEmpty() ? "<none>" : canResolveExamples,
                canResolveErrors.isEmpty() ? "<none>" : canResolveErrors);
          }
        }
      }
    }
    if (Config.DEBUG_LOGGING.getAsBoolean()
        && manager instanceof IStandardRequestManager standardManager) {
      logParentChildrenState(standardManager, request.getId(), "delivery-create");
      logDeliveryLinkState("create", standardManager, request.getId(), token);
    }
    return Lists.newArrayList(token);
  }

  public void logParentChildrenState(
      IStandardRequestManager manager, IToken<?> parentToken, String phase) {
    if (!Config.DEBUG_LOGGING.getAsBoolean()) {
      return;
    }
    var handler = manager.getRequestHandler();
    logRequestStateChange(manager, parentToken, phase);
    IRequest<?> parent = handler.getRequest(parentToken);
    var children = java.util.Objects.requireNonNull(parent.getChildren(), "children");
    StringBuilder builder = new StringBuilder();
    builder.append("count=").append(children.size());
    for (IToken<?> child : children) {
      if (child == null) {
        continue;
      }
      logRequestStateChange(manager, child, phase + "-child");
      IRequest<?> childReq = handler.getRequest(child);
      String childState = childReq == null ? "<null>" : childReq.getState().toString();
      builder.append(" ").append(child).append(":").append(childState);
    }
    String snapshot = builder.toString();
    String previous = parentChildrenSnapshots.put(parentToken, snapshot);
    if (!snapshot.equals(previous)) {
      TheSettlerXCreate.LOGGER.info(
          "[CreateShop] parent children {} parent={} {}", phase, parentToken, snapshot);
    }
  }

  private void logRequestStateChange(
      IStandardRequestManager manager, IToken<?> token, String phase) {
    if (!Config.DEBUG_LOGGING.getAsBoolean()) {
      return;
    }
    try {
      var handler = manager.getRequestHandler();
      IRequest<?> request = handler.getRequest(token);
      if (request == null) {
        return;
      }
      String state = request.getState().toString();
      String previous = requestStateSnapshots.put(token, state);
      if (state.equals(previous)) {
        return;
      }
      String parent = request.getParent() == null ? "<none>" : request.getParent().toString();
      String type = request.getRequest().getClass().getName();
      boolean hasChildren = request.hasChildren();
      TheSettlerXCreate.LOGGER.info(
          "[CreateShop] request state {} token={} state={} prev={} parent={} children={} type={}",
          phase,
          token,
          state,
          previous == null ? "<none>" : previous,
          parent,
          hasChildren,
          type);
    } catch (Exception ignored) {
      // Ignore lookup errors.
    }
  }

  private void logPendingReasonChange(IToken<?> token, String reason) {
    if (!Config.DEBUG_LOGGING.getAsBoolean()) {
      return;
    }
    String previous = pendingReasonSnapshots.put(token, reason);
    if (reason.equals(previous)) {
      return;
    }
    TheSettlerXCreate.LOGGER.info(
        "[CreateShop] tickPending reason token={} reason={} prev={}",
        token,
        reason,
        previous == null ? "<none>" : previous);
  }

  private void recordPendingSource(IToken<?> token, String reason) {
    if (!Config.DEBUG_LOGGING.getAsBoolean() || token == null || reason == null) {
      return;
    }
    String previous = pendingSources.put(token, reason);
    if (reason.equals(previous)) {
      return;
    }
    TheSettlerXCreate.LOGGER.info(
        "[CreateShop] pending source token={} reason={} prev={}",
        token,
        reason,
        previous == null ? "<none>" : previous);
  }

  private void scheduleParentChildRecheck(IStandardRequestManager manager, IToken<?> parentToken) {
    var level = manager.getColony().getWorld();
    parentChildrenRecheck.put(parentToken, level.getGameTime() + 20L);
  }

  private void processParentChildRechecks(IStandardRequestManager manager, Level level) {
    if (parentChildrenRecheck.isEmpty()) {
      return;
    }
    long now = level.getGameTime();
    var iterator = parentChildrenRecheck.entrySet().iterator();
    while (iterator.hasNext()) {
      var entry = iterator.next();
      Long due = entry.getValue();
      if (due != null && due > now) {
        continue;
      }
      logParentChildrenState(manager, entry.getKey(), "recheck");
      iterator.remove();
    }
  }

  private void sanitizeRequestChain(IRequestManager manager, IRequest<?> root) {
    if (manager == null || root == null) {
      return;
    }
    IStandardRequestManager standard = unwrapStandardManager(manager);
    if (standard == null) {
      return;
    }
    var handler = standard.getRequestHandler();
    if (handler == null) {
      return;
    }
    java.util.Set<IToken<?>> visiting = new java.util.HashSet<>();
    java.util.Set<IToken<?>> visited = new java.util.HashSet<>();
    java.util.ArrayDeque<IRequest<?>> stack = new java.util.ArrayDeque<>();
    java.util.ArrayDeque<java.util.Iterator<IToken<?>>> itStack = new java.util.ArrayDeque<>();
    IToken<?> rootToken = root.getId();
    if (rootToken == null) {
      return;
    }
    stack.push(root);
    itStack.push(root.getChildren().iterator());
    visiting.add(rootToken);
    visited.add(rootToken);
    int steps = 0;
    while (!stack.isEmpty() && steps < MAX_CHAIN_SANITIZE_NODES) {
      steps++;
      var it = itStack.peek();
      if (it == null || !it.hasNext()) {
        IRequest<?> done = stack.pop();
        itStack.pop();
        if (done.getId() != null) {
          visiting.remove(done.getId());
        }
        continue;
      }
      IToken<?> childToken = it.next();
      if (childToken == null) {
        continue;
      }
      IRequest<?> parent = stack.peek();
      IToken<?> parentToken = parent.getId();
      if (childToken.equals(parentToken)) {
        parent.removeChild(childToken);
        if (Config.DEBUG_LOGGING.getAsBoolean()) {
          String key = "self:" + parentToken;
          if (chainCycleLogged.add(key)) {
            TheSettlerXCreate.LOGGER.info(
                "[CreateShop] removed self-cycle in request chain {}", parentToken);
          }
        }
        continue;
      }
      if (visiting.contains(childToken)) {
        parent.removeChild(childToken);
        if (Config.DEBUG_LOGGING.getAsBoolean()) {
          String key = "cycle:" + parentToken + ":" + childToken;
          if (chainCycleLogged.add(key)) {
            TheSettlerXCreate.LOGGER.info(
                "[CreateShop] removed request chain cycle parent={} child={}",
                parentToken,
                childToken);
          }
        }
        continue;
      }
      if (visited.contains(childToken)) {
        continue;
      }
      IRequest<?> child;
      try {
        child = handler.getRequest(childToken);
      } catch (IllegalArgumentException ex) {
        child = null;
      }
      if (child == null) {
        continue;
      }
      visited.add(childToken);
      visiting.add(childToken);
      stack.push(child);
      itStack.push(child.getChildren().iterator());
    }
    if (steps >= MAX_CHAIN_SANITIZE_NODES && Config.DEBUG_LOGGING.getAsBoolean()) {
      TheSettlerXCreate.LOGGER.info(
          "[CreateShop] request chain sanitize aborted after {} steps for {}", steps, rootToken);
    }
  }

  private boolean safeIsRequestChainValid(
      IRequestManager manager, IRequest<? extends IDeliverable> request) {
    try {
      return isRequestChainValid(manager, request);
    } catch (StackOverflowError error) {
      if (Config.DEBUG_LOGGING.getAsBoolean()) {
        TheSettlerXCreate.LOGGER.info(
            "[CreateShop] request chain validation overflow for {}", request.getId());
      }
      return false;
    }
  }

  private static IStandardRequestManager unwrapStandardManager(IRequestManager manager) {
    if (manager instanceof IStandardRequestManager standard) {
      return standard;
    }
    if (manager == null) {
      return null;
    }
    try {
      var method = manager.getClass().getMethod("getManager");
      Object value = method.invoke(manager);
      if (value instanceof IStandardRequestManager standard) {
        return standard;
      }
    } catch (Exception ex) {
      if (Config.DEBUG_LOGGING.getAsBoolean()) {
        TheSettlerXCreate.LOGGER.info(
            "[CreateShop] unwrap manager failed type={} err={}",
            manager.getClass().getName(),
            ex.getMessage() == null ? "<null>" : ex.getMessage());
      }
    }
    return null;
  }

  private void logDeliveryLinkState(
      String stage, IStandardRequestManager manager, IToken<?> parentToken, IToken<?> childToken) {
    String key = stage + ":" + childToken;
    if (!deliveryLinkLogged.add(key)) {
      return;
    }
    try {
      var handler = manager.getRequestHandler();
      IRequest<?> child = handler.getRequest(childToken);
      IToken<?> childParent = child.getParent();
      IRequest<?> parent = handler.getRequest(parentToken);
      int parentChildren = parent.getChildren().size();
      TheSettlerXCreate.LOGGER.info(
          "[CreateShop] delivery link state {} parent={} child={} childParent={} parentChildren={}",
          stage,
          parentToken,
          childToken,
          childParent == null ? "<none>" : childParent,
          parentChildren);
    } catch (Exception ex) {
      TheSettlerXCreate.LOGGER.info(
          "[CreateShop] delivery link state {} parent={} child={} error={}",
          stage,
          parentToken,
          childToken,
          ex.getMessage() == null ? "<null>" : ex.getMessage());
    }
  }

  private boolean isRequestOnCooldown(Level level, IToken<?> token) {
    Long until = orderedRequests.get(token);
    if (until == null) {
      return false;
    }
    long now = level.getGameTime();
    if (now >= until) {
      orderedRequests.remove(token);
      return false;
    }
    return true;
  }

  private void markRequestOrdered(Level level, IToken<?> token) {
    long until = level.getGameTime() + Config.ORDER_TTL_TICKS.getAsLong();
    orderedRequests.put(token, until);
    if (Config.DEBUG_LOGGING.getAsBoolean()) {
      pendingSources.put(token, "markRequestOrdered");
    }
    if (Config.DEBUG_LOGGING.getAsBoolean()) {
      TheSettlerXCreate.LOGGER.info(
          "[CreateShop] markRequestOrdered token={} resolver={} until={}", token, getId(), until);
    }
  }

  private void clearRequestCooldown(IToken<?> token) {
    orderedRequests.remove(token);
    if (Config.DEBUG_LOGGING.getAsBoolean() && token != null) {
      String source = pendingSources.remove(token);
      if (source != null) {
        TheSettlerXCreate.LOGGER.info(
            "[CreateShop] pending cleared token={} source=clearCooldown prev={}", token, source);
      }
    }
  }

  private boolean hasDeliveriesCreated(IToken<?> token) {
    return deliveriesCreated.contains(token);
  }

  private void markDeliveriesCreated(IToken<?> token) {
    deliveriesCreated.add(token);
  }

  private void clearDeliveriesCreated(IToken<?> token) {
    deliveriesCreated.remove(token);
  }

  private boolean shouldNotifyPending(Level level, IToken<?> token) {
    if (level == null || token == null) {
      return false;
    }
    Long next = pendingNotices.get(token);
    long now = level.getGameTime();
    if (next != null && now < next) {
      return false;
    }
    pendingNotices.put(token, now + Config.PENDING_NOTICE_COOLDOWN.getAsLong());
    return true;
  }

  private String tryDescribeResolver(Object resolver) {
    if (resolver == null) {
      return "<none>";
    }
    try {
      var getLocation = resolver.getClass().getMethod("getLocation");
      Object location = getLocation.invoke(resolver);
      if (location != null) {
        return "location=" + location;
      }
    } catch (Exception ignored) {
      // Fall through.
    }
    try {
      var getRequester = resolver.getClass().getMethod("getRequester");
      Object requester = getRequester.invoke(resolver);
      if (requester != null) {
        return "requester=" + requester;
      }
    } catch (Exception ignored) {
      // Fall through.
    }
    return "<unknown>";
  }

  private static void logDeliveryDiagnostics(
      String stage,
      IRequestManager manager,
      IToken<?> deliveryToken,
      UUID parentRequestId,
      BlockPos pickupPosition,
      ItemStack stack,
      ILocation targetLocation,
      int reservedForRequest,
      int reservedForDeliverable,
      int reservedForStack) {
    Level level = manager.getColony().getWorld();
    String tokenInfo = deliveryToken == null ? "<null>" : deliveryToken.toString();
    String parentInfo = parentRequestId.toString();
    String itemInfo = stack.isEmpty() ? "<empty>" : stack.getItem().toString();
    int count = stack.getCount();

    boolean pickupLoaded = WorldUtil.isBlockLoaded(level, pickupPosition);
    String pickupBlock =
        pickupLoaded ? level.getBlockState(pickupPosition).getBlock().toString() : "<unloaded>";
    BlockEntity pickupEntity = pickupLoaded ? level.getBlockEntity(pickupPosition) : null;
    String pickupEntityName = pickupEntity == null ? "<none>" : pickupEntity.getClass().getName();
    IItemHandler handler = null;
    if (pickupEntity instanceof CreateShopBlockEntity shopPickup) {
      handler = shopPickup.getItemHandler(null);
    } else if (pickupEntity instanceof AbstractTileEntityRack rack) {
      handler = rack.getItemHandlerCap();
    }
    int slots = handler == null ? -1 : handler.getSlots();
    int matchSlot = -1;
    int simExtract = 0;
    if (handler != null && !stack.isEmpty()) {
      for (int i = 0; i < slots; i++) {
        ItemStack slotStack = handler.getStackInSlot(i);
        if (slotStack.isEmpty() || !ItemStack.isSameItemSameComponents(slotStack, stack)) {
          continue;
        }
        matchSlot = i;
        ItemStack extracted = handler.extractItem(i, stack.getCount(), true);
        simExtract = extracted.getCount();
        break;
      }
    }

    BlockPos targetPosition = targetLocation.getInDimensionLocation();
    boolean targetLoaded = WorldUtil.isBlockLoaded(level, targetPosition);
    String targetBlock =
        targetLoaded ? level.getBlockState(targetPosition).getBlock().toString() : "<unloaded>";
    String reservedDel =
        reservedForDeliverable < 0 ? "<n/a>" : String.valueOf(reservedForDeliverable);

    TheSettlerXCreate.LOGGER.info(
        "[CreateShop] delivery diag {} token={} parent={} item={} count={} pickupPosition={} pickupLoaded={} pickupBlock={} pickupEntity={} handlerSlots={} matchSlot={} simExtract={} reservedReq={} reservedDel={} reservedStack={} targetPosition={} targetLoaded={} targetBlock={}",
        stage,
        tokenInfo,
        parentInfo,
        itemInfo,
        count,
        pickupPosition,
        pickupLoaded,
        pickupBlock,
        pickupEntityName,
        slots,
        matchSlot,
        simExtract,
        reservedForRequest,
        reservedDel,
        reservedForStack,
        targetPosition,
        targetLoaded,
        targetBlock);
  }

  private static boolean tryEnqueueDelivery(IStandardRequestManager manager, IToken<?> token) {
    if (manager == null || token == null) {
      return false;
    }
    var buildingManager = manager.getColony().getServerBuildingManager();
    if (buildingManager == null) {
      return false;
    }
    int warehousesChecked = 0;
    int warehousesWithCouriers = 0;
    int warehousesWithQueue = 0;
    for (var entry : buildingManager.getBuildings().entrySet()) {
      if (!(entry.getValue() instanceof IWareHouse warehouse)) {
        continue;
      }
      warehousesChecked++;
      CourierAssignmentModule couriers = warehouse.getModule(BuildingModules.WAREHOUSE_COURIERS);
      int courierCount = couriers == null ? 0 : couriers.getAssignedCitizen().size();
      if (courierCount <= 0) {
        continue;
      }
      warehousesWithCouriers++;
      WarehouseRequestQueueModule queue =
          warehouse.getModule(BuildingModules.WAREHOUSE_REQUEST_QUEUE);
      if (queue == null) {
        continue;
      }
      warehousesWithQueue++;
      queue.addRequest(token);
      if (Config.DEBUG_LOGGING.getAsBoolean()) {
        String warehouseInfo = "<unknown>";
        try {
          var getLocation = warehouse.getClass().getMethod("getLocation");
          Object location = getLocation.invoke(warehouse);
          if (location != null) {
            warehouseInfo = location.toString();
          }
        } catch (Exception ignored) {
          // Ignore reflection failures.
        }
        TheSettlerXCreate.LOGGER.info(
            "[CreateShop] delivery enqueue token={} warehouse={} couriers={} queued=true",
            token,
            warehouseInfo,
            courierCount);
      }
      return true;
    }
    if (Config.DEBUG_LOGGING.getAsBoolean()) {
      TheSettlerXCreate.LOGGER.info(
          "[CreateShop] delivery enqueue token={} queued=false warehousesChecked={} withCouriers={} withQueue={}",
          token,
          warehousesChecked,
          warehousesWithCouriers,
          warehousesWithQueue);
    }
    return false;
  }

  private int getAvailableFromRacks(TileEntityCreateShop tile, IDeliverable deliverable) {
    Level level = tile.getLevel();
    if (level == null) {
      return 0;
    }
    int total = 0;
    for (BlockPos pos : tile.getBuilding().getContainers()) {
      if (!WorldUtil.isBlockLoaded(level, pos)) {
        continue;
      }
      BlockEntity entity = level.getBlockEntity(pos);
      if (!(entity instanceof AbstractTileEntityRack rack)) {
        continue;
      }
      total += rack.getItemCount(deliverable::matches);
    }
    return Math.max(0, total);
  }

  private int getAvailableFromPickup(CreateShopBlockEntity pickup, IDeliverable deliverable) {
    if (pickup == null) {
      return 0;
    }
    IItemHandler handler = pickup.getItemHandler(null);
    if (handler == null) {
      return 0;
    }
    int total = 0;
    for (int i = 0; i < handler.getSlots(); i++) {
      ItemStack stack = handler.getStackInSlot(i);
      if (stack.isEmpty()) {
        continue;
      }
      if (deliverable.matches(stack)) {
        total += stack.getCount();
      }
    }
    return Math.max(0, total);
  }

  private List<com.minecolonies.api.util.Tuple<ItemStack, BlockPos>> planFromRacksWithPositions(
      TileEntityCreateShop tile, IDeliverable deliverable, int amount) {
    if (amount <= 0) {
      return Lists.newArrayList();
    }
    Level level = tile.getLevel();
    if (level == null) {
      return Lists.newArrayList();
    }
    int remaining = amount;
    List<com.minecolonies.api.util.Tuple<ItemStack, BlockPos>> planned = Lists.newArrayList();
    List<com.minecolonies.api.util.Tuple<ItemStack, BlockPos>> matches =
        tile.getMatchingItemStacksInWarehouse(deliverable::matches);
    if (deliverable instanceof Tool tool) {
      matches.sort(
          Comparator.comparingInt(
                  (com.minecolonies.api.util.Tuple<ItemStack, BlockPos> entry) ->
                      getToolLevel(tool, entry.getA()))
              .reversed());
    }
    for (var tuple : matches) {
      if (remaining <= 0) {
        break;
      }
      ItemStack stack = tuple.getA();
      if (stack.isEmpty()) {
        continue;
      }
      int toTake = Math.min(remaining, stack.getCount());
      ItemStack copy = stack.copy();
      copy.setCount(toTake);
      planned.add(new com.minecolonies.api.util.Tuple<>(copy, tuple.getB()));
      remaining -= toTake;
    }
    return planned;
  }

  private List<com.minecolonies.api.util.Tuple<ItemStack, BlockPos>> planFromPickupWithPositions(
      CreateShopBlockEntity pickup, IDeliverable deliverable, int amount) {
    if (amount <= 0 || pickup == null) {
      return Lists.newArrayList();
    }
    IItemHandler handler = pickup.getItemHandler(null);
    if (handler == null) {
      return Lists.newArrayList();
    }
    int remaining = amount;
    List<com.minecolonies.api.util.Tuple<ItemStack, BlockPos>> planned = Lists.newArrayList();
    BlockPos pickupPos = pickup.getBlockPos();
    for (int i = 0; i < handler.getSlots(); i++) {
      if (remaining <= 0) {
        break;
      }
      ItemStack stack = handler.getStackInSlot(i);
      if (stack.isEmpty()) {
        continue;
      }
      if (!deliverable.matches(stack)) {
        continue;
      }
      int toTake = Math.min(remaining, stack.getCount());
      ItemStack copy = stack.copy();
      copy.setCount(toTake);
      planned.add(new com.minecolonies.api.util.Tuple<>(copy, pickupPos));
      remaining -= toTake;
    }
    return planned;
  }

  private static int countPlanned(
      List<com.minecolonies.api.util.Tuple<ItemStack, BlockPos>> planned) {
    if (planned == null || planned.isEmpty()) {
      return 0;
    }
    int total = 0;
    for (var entry : planned) {
      if (entry == null) {
        continue;
      }
      ItemStack stack = entry.getA();
      if (!stack.isEmpty()) {
        total += stack.getCount();
      }
    }
    return total;
  }

  private static List<ItemStack> extractStacks(
      List<com.minecolonies.api.util.Tuple<ItemStack, BlockPos>> stacks) {
    List<ItemStack> result = Lists.newArrayList();
    if (stacks == null) {
      return result;
    }
    for (var entry : stacks) {
      if (entry == null) {
        continue;
      }
      ItemStack stack = entry.getA();
      if (!stack.isEmpty()) {
        result.add(stack.copy());
      }
    }
    return result;
  }

  private int getToolLevel(Tool tool, ItemStack stack) {
    if (tool == null || stack.isEmpty()) {
      return 0;
    }
    EquipmentTypeEntry type = tool.getEquipmentType();
    return type.getMiningLevel(stack);
  }
}
