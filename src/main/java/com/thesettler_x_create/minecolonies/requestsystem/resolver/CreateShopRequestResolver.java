package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import com.google.common.collect.Lists;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.requestsystem.location.ILocation;
import com.minecolonies.api.colony.requestsystem.manager.IRequestManager;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.requestable.IDeliverable;
import com.minecolonies.api.colony.requestsystem.requestable.INonExhaustiveDeliverable;
import com.minecolonies.api.colony.requestsystem.requestable.deliveryman.Delivery;
import com.minecolonies.api.colony.requestsystem.resolver.IRequestResolver;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.api.util.BlockPosUtil;
import com.minecolonies.api.util.WorldUtil;
import com.minecolonies.core.colony.requestsystem.management.IStandardRequestManager;
import com.minecolonies.core.colony.requestsystem.resolvers.core.AbstractWarehouseRequestResolver;
import com.thesettler_x_create.Config;
import com.thesettler_x_create.TheSettlerXCreate;
import com.thesettler_x_create.blockentity.CreateShopBlockEntity;
import com.thesettler_x_create.minecolonies.building.BuildingCreateShop;
import com.thesettler_x_create.minecolonies.tileentity.TileEntityCreateShop;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.NotNull;

/**
 * Request resolver that fulfills deliverable requests from Create Shop stock and warehouse racks.
 */
public class CreateShopRequestResolver extends AbstractWarehouseRequestResolver {
  // Keep below warehouse resolvers so MineColonies prefers warehouse stock before Create Shop.
  private static final int PRIORITY = 140;
  private static final int MAX_CHAIN_SANITIZE_NODES = 512;
  private static final long DELIVERY_CHILD_STALE_TIMEOUT_FLOOR_TICKS = 20L * 30L;
  private long lastPerfLogTime = 0L;
  private long lastTickPendingNanos = 0L;

  private static final java.util.Set<IToken<?>> cancelledRequests =
      java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());
  private static final java.util.Map<IToken<?>, Long> pendingNotices =
      new java.util.concurrent.ConcurrentHashMap<>();
  private static final java.util.Map<IToken<?>, Long> retryingReassignAttempts =
      new java.util.concurrent.ConcurrentHashMap<>();
  private final Object debugLock = new Object();
  private volatile long lastTickPendingDebugTime = 0L;
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
  private final java.util.Map<IToken<?>, Long> deliveryChildActiveSince =
      new java.util.concurrent.ConcurrentHashMap<>();
  private final java.util.Map<IToken<?>, Long> parentDeliveryActiveSince =
      new java.util.concurrent.ConcurrentHashMap<>();
  private final java.util.Map<IToken<?>, Long> parentStaleRecoveryArmedAt =
      new java.util.concurrent.ConcurrentHashMap<>();
  private final CreateShopResolverPlanning planning = new CreateShopResolverPlanning();
  private final CreateShopDeliveryManager deliveryManager = new CreateShopDeliveryManager(this);
  private final CreateShopResolverDiagnostics diagnostics = new CreateShopResolverDiagnostics(this);
  private final CreateShopResolverChain chain = new CreateShopResolverChain(this);
  private final CreateShopResolverRecheck recheck =
      new CreateShopResolverRecheck(this, diagnostics);
  private final CreateShopResolverCooldown cooldown = new CreateShopResolverCooldown(this);
  private final CreateShopResolverPendingState pendingState =
      new CreateShopResolverPendingState(this);
  private final CreateShopResolverMessaging messaging = new CreateShopResolverMessaging(this);
  private final CreateShopRequestValidator validator = new CreateShopRequestValidator();
  private final CreateShopStockResolver stockResolver = new CreateShopStockResolver();
  private final CreateShopPendingDeliveryTracker pendingTracker =
      new CreateShopPendingDeliveryTracker();
  private final CreateShopWorkerAvailabilityGate workerAvailabilityGate =
      new CreateShopWorkerAvailabilityGate();
  private final CreateShopRequestStateMachine flowStateMachine =
      new CreateShopRequestStateMachine();

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
    return validator.canResolveRequest(this, manager, request);
  }

  @Override
  public List<IToken<?>> attemptResolveRequest(
      @NotNull IRequestManager manager, @NotNull IRequest<? extends IDeliverable> request) {
    long now = resolveNowTick(manager);
    transitionFlow(
        manager,
        request,
        CreateShopFlowState.ELIGIBILITY_CHECK,
        "attemptResolve:start",
        "",
        0,
        null);
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
    if (cooldown.isRequestOnCooldown(level, request.getId())) {
      if (Config.DEBUG_LOGGING.getAsBoolean()) {
        TheSettlerXCreate.LOGGER.info(
            "[CreateShop] attemptResolve skipped (request already ordered)");
      }
      return Lists.newArrayList();
    }
    if (hasDeliveriesCreated(request.getId())) {
      flowStateMachine.touch(request.getId(), now, "attemptResolve:deliveries-created");
      return Lists.newArrayList();
    }
    IDeliverable deliverable = request.getRequest();
    chain.sanitizeRequestChain(manager, request);

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

    UUID requestId = toRequestId(request.getId());
    int reservedForRequest = pickup.getReservedForRequest(requestId);
    int needed = computeOutstandingNeeded(request, deliverable, reservedForRequest);
    int reservedForDeliverable = pickup.getReservedForDeliverable(deliverable);
    int reservedForOthers = Math.max(0, reservedForDeliverable - reservedForRequest);
    if (needed <= 0) {
      flowStateMachine.touch(request.getId(), now, "attemptResolve:no-needed");
      if (Config.DEBUG_LOGGING.getAsBoolean()) {
        TheSettlerXCreate.LOGGER.info("[CreateShop] attemptResolve skipped (needed<=0)");
      }
      return Lists.newArrayList();
    }
    boolean workerWorking = shop.isWorkerWorking();

    CreateShopStockSnapshot snapshot =
        stockResolver.getAvailability(tile, pickup, deliverable, reservedForOthers, planning);
    int rackAvailable = snapshot.getRackAvailable();
    int rackUsable = snapshot.getRackUsable();
    int networkAvailable = workerWorking ? snapshot.getNetworkAvailable() : 0;
    int available = Math.max(0, networkAvailable + rackUsable);
    int provide = Math.min(available, needed);
    if (provide <= 0) {
      flowStateMachine.touch(request.getId(), now, "attemptResolve:insufficient");
      if (Config.DEBUG_LOGGING.getAsBoolean()) {
        TheSettlerXCreate.LOGGER.info(
            "[CreateShop] attemptResolve aborted (available={}, reserved={}, needed={}) for {}",
            available,
            reservedForOthers,
            needed,
            deliverable);
      }
      // Track pending requests so we can detect items arriving in racks.
      cooldown.markRequestOrdered(level, request.getId());
      pendingTracker.setPendingCount(request.getId(), needed);
      diagnostics.recordPendingSource(request.getId(), "attemptResolve:insufficient");
      return Lists.newArrayList();
    }

    List<com.minecolonies.api.util.Tuple<ItemStack, BlockPos>> planned =
        planning.planFromRacksWithPositions(tile, deliverable, Math.min(provide, rackUsable));
    List<ItemStack> ordered = planning.extractStacks(planned);
    int plannedCount = ordered.stream().mapToInt(ItemStack::getCount).sum();
    int remaining = Math.max(0, provide - plannedCount);
    if (remaining > 0 && workerWorking) {
      String requesterName = messaging.resolveRequesterName(manager, request);
      ordered.addAll(stockResolver.requestFromNetwork(tile, deliverable, remaining, requesterName));
    }
    if (Config.DEBUG_LOGGING.getAsBoolean()) {
      TheSettlerXCreate.LOGGER.info(
          "[CreateShop] attemptResolve provide={} (available={}, reserved={}, needed={}) -> ordered {} stack(s)",
          provide,
          available,
          reservedForOthers,
          needed,
          ordered.size());
    }
    if (!ordered.isEmpty()) {
      transitionFlow(
          manager,
          request,
          CreateShopFlowState.ORDERED_FROM_NETWORK,
          "attemptResolve:order-created",
          describeStack(ordered.get(0)),
          countStackList(ordered),
          "com.thesettler_x_create.message.createshop.flow_ordered");
      // If we can satisfy from racks, create deliveries immediately.
      if (rackUsable > 0) {
        if (unwrapStandardManager(manager) == null) {
          cooldown.markRequestOrdered(level, request.getId());
          pendingTracker.setPendingCount(request.getId(), Math.max(1, needed));
          diagnostics.recordPendingSource(request.getId(), "attemptResolve:defer-wrapped-manager");
          flowStateMachine.touch(request.getId(), now, "attemptResolve:defer-wrapped-manager");
          if (Config.DEBUG_LOGGING.getAsBoolean()) {
            TheSettlerXCreate.LOGGER.info(
                "[CreateShop] attemptResolve defer delivery creation (wrapped manager) request={} needed={} rackUsable={}",
                request.getId(),
                needed,
                rackUsable);
          }
          return Lists.newArrayList();
        }
        transitionFlow(
            manager,
            request,
            CreateShopFlowState.ARRIVED_IN_SHOP_RACK,
            "attemptResolve:rack-usable",
            describeStack(ordered.get(0)),
            countStackList(ordered),
            "com.thesettler_x_create.message.createshop.flow_arrived");
        List<IToken<?>> created =
            deliveryManager.createDeliveriesFromStacks(manager, request, planned, pickup);
        if (plannedCount > 0 && reservedForRequest > 0) {
          consumeReservedForRequest(pickup, requestId, planned);
          transitionFlow(
              manager,
              request,
              CreateShopFlowState.RESERVED_FOR_DELIVERY,
              "attemptResolve:reserved-consumed",
              describeStack(ordered.get(0)),
              plannedCount,
              "com.thesettler_x_create.message.createshop.flow_reserved");
        }
        if (remaining > 0) {
          cooldown.markRequestOrdered(level, request.getId());
          pendingTracker.setPendingCount(request.getId(), Math.max(1, needed - plannedCount));
          diagnostics.recordPendingSource(request.getId(), "attemptResolve:partial");
        }
        if (Config.DEBUG_LOGGING.getAsBoolean()) {
          TheSettlerXCreate.LOGGER.info(
              "[CreateShop] attemptResolve created deliveries parent={} manager={} tokens={}",
              request.getId(),
              manager.getClass().getName(),
              created);
        }
        if (!created.isEmpty()) {
          transitionFlow(
              manager,
              request,
              CreateShopFlowState.DELIVERY_CREATED,
              "attemptResolve:delivery-created",
              describeStack(ordered.get(0)),
              plannedCount,
              "com.thesettler_x_create.message.createshop.flow_delivery_created");
        }
        return created;
      }
      // Otherwise, order from network and wait for arrival.
      cooldown.markRequestOrdered(level, request.getId());
      pendingTracker.setPendingCount(request.getId(), needed);
      diagnostics.recordPendingSource(request.getId(), "attemptResolve:network-ordered");
      messaging.sendShopChat(
          manager, "com.thesettler_x_create.message.createshop.request_sent", ordered);
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
    boolean ordered = cooldown.isOrdered(request.getId());
    boolean cooldown = this.cooldown.isRequestOnCooldown(level, request.getId());
    if (ordered || cooldown) {
      if (Config.DEBUG_LOGGING.getAsBoolean()) {
        TheSettlerXCreate.LOGGER.info(
            "[CreateShop] resolveRequest skip parent={} ordered={} cooldown={}",
            request.getId(),
            ordered,
            cooldown);
      }
      if (manager instanceof IStandardRequestManager standardManager) {
        diagnostics.logRequestStateChange(standardManager, request.getId(), "resolveRequest-skip");
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
      diagnostics.logRequestStateChange(standardManager, request.getId(), "resolveRequest");
    }
  }

  @Override
  public java.util.List<IRequest<?>> getFollowupRequestForCompletion(
      @NotNull IRequestManager manager, @NotNull IRequest<? extends IDeliverable> request) {
    // Avoid AbstractWarehouseRequestResolver followup logic (casts to TileEntityWareHouse).
    // Keep MineColonies delivery-resolver behavior: no explicit followup requests here.
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
      diagnostics.logRequestStateChange(standardManager, request.getId(), "followup");
    }
    return null;
  }

  public void tickPendingDeliveries(IRequestManager manager) {
    if (Config.DEBUG_LOGGING.getAsBoolean()) {
      TheSettlerXCreate.LOGGER.info(
          "[CreateShop] tickPending entry manager={} resolverId={}",
          manager == null ? "<null>" : manager.getClass().getName(),
          getId());
    }
    IStandardRequestManager standardManager = unwrapStandardManager(manager);
    if (standardManager == null) {
      if (Config.DEBUG_LOGGING.getAsBoolean()) {
        TheSettlerXCreate.LOGGER.info("[CreateShop] tickPending skipped (no standard manager)");
      }
      return;
    }
    long perfStart = System.nanoTime();
    Level level = standardManager.getColony().getWorld();
    if (level == null) {
      if (Config.DEBUG_LOGGING.getAsBoolean()) {
        TheSettlerXCreate.LOGGER.info("[CreateShop] tickPending skipped (no level)");
      }
      return;
    }
    if (level.isClientSide) {
      if (Config.DEBUG_LOGGING.getAsBoolean()) {
        TheSettlerXCreate.LOGGER.info("[CreateShop] tickPending skipped (client side)");
      }
      return;
    }
    reassignResolvableRetryingRequests(standardManager, level);
    recheck.processParentChildRechecks(standardManager, level);
    var assignmentStore = standardManager.getRequestResolverRequestAssignmentDataStore();
    var requestHandler = standardManager.getRequestHandler();
    Map<IToken<?>, java.util.Collection<IToken<?>>> assignments = assignmentStore.getAssignments();
    java.util.Set<IToken<?>> assigned = new java.util.LinkedHashSet<>();
    java.util.Collection<IToken<?>> directAssigned = assignments.get(getId());
    if (directAssigned != null && !directAssigned.isEmpty()) {
      assigned.addAll(directAssigned);
    }
    java.util.Set<IToken<?>> assignedByOwner =
        collectAssignedTokensByRequestResolver(standardManager, assignments);
    if (!assignedByOwner.isEmpty()) {
      int before = assigned.size();
      assigned.addAll(assignedByOwner);
      if (Config.DEBUG_LOGGING.getAsBoolean()
          && shouldLogTickPending(level)
          && (before == 0 || assignedByOwner.size() > before)) {
        TheSettlerXCreate.LOGGER.info(
            "[CreateShop] tickPending owner-sync: resolverId={} directAssignments={} ownerAssignments={} effective={}",
            getId(),
            directAssigned == null ? 0 : directAssigned.size(),
            assignedByOwner.size(),
            assigned.size());
      }
    } else {
      java.util.Set<IToken<?>> recovered =
          collectAssignedTokensFromLocalResolvers(standardManager, assignments);
      if (!recovered.isEmpty()) {
        assigned.addAll(recovered);
        if (Config.DEBUG_LOGGING.getAsBoolean() && shouldLogTickPending(level)) {
          TheSettlerXCreate.LOGGER.info(
              "[CreateShop] tickPending assignment drift recovered: resolverId={} recoveredAssignments={}",
              getId(),
              recovered.size());
        }
      }
    }
    if (Config.DEBUG_LOGGING.getAsBoolean() && assigned.isEmpty()) {
      TheSettlerXCreate.LOGGER.info(
          "[CreateShop] tickPending no assignments for resolverId={} assignmentsKeys={}",
          getId(),
          assignments.keySet());
    }
    java.util.Set<IToken<?>> pendingTokens = new java.util.HashSet<>();
    pendingTokens.addAll(assigned);
    pendingTokens.addAll(pendingTracker.getTokens());
    if (pendingTokens.isEmpty()) {
      if (Config.DEBUG_LOGGING.getAsBoolean() && shouldLogTickPending(level)) {
        if (pendingTracker.hasEntries()) {
          TheSettlerXCreate.LOGGER.info(
              "[CreateShop] tickPending: empty snapshot but maps ordered={} pendingCounts={}",
              cooldown.getOrderedCount(),
              pendingTracker.size());
        }
      }
      if (Config.DEBUG_LOGGING.getAsBoolean() && shouldLogTickPending(level)) {
        TheSettlerXCreate.LOGGER.info(
            "[CreateShop] tickPending: no assigned or ordered requests for resolver {}", getId());
      }
      return;
    }
    if (Config.DEBUG_LOGGING.getAsBoolean() && shouldLogTickPending(level)) {
      int assignedCount = assigned.size();
      int orderedCount = cooldown.getOrderedCount();
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
    BuildingCreateShop shop = getShop(standardManager);
    if (shop == null) {
      return;
    }
    boolean workerWorking = shop.isWorkerWorking();
    if (!workerWorking) {
      if (Config.DEBUG_LOGGING.getAsBoolean() && shouldLogTickPending(level)) {
        TheSettlerXCreate.LOGGER.info("[CreateShop] tickPending worker not working; reconciling");
      }
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
        cooldown.clearRequestCooldown(token);
        pendingTracker.remove(token);
        continue;
      }
      if (!isRequestOwnedByLocalResolver(standardManager, request)) {
        cooldown.clearRequestCooldown(token);
        pendingTracker.remove(token);
        clearDeliveriesCreated(token);
        flowStateMachine.remove(token);
        continue;
      }
      if (cancelledRequests.contains(request.getId())) {
        if (request.getState()
            != com.minecolonies.api.colony.requestsystem.request.RequestState.CANCELLED) {
          cancelledRequests.remove(request.getId());
        } else {
          cooldown.clearRequestCooldown(request.getId());
          pendingTracker.remove(request.getId());
          cooldown.clearRequestCooldown(request.getId());
          clearDeliveriesCreated(request.getId());
          diagnostics.logPendingReasonChange(request.getId(), "skip:cancelled");
          transitionFlow(
              manager,
              request,
              CreateShopFlowState.CANCELLED,
              "tickPending:cancelled",
              "",
              0,
              "com.thesettler_x_create.message.createshop.flow_cancelled");
          if (Config.DEBUG_LOGGING.getAsBoolean()) {
            TheSettlerXCreate.LOGGER.info(
                "[CreateShop] tickPending: " + request.getId() + " skip (cancelled)");
          }
          continue;
        }
      }
      if (request.getState()
          == com.minecolonies.api.colony.requestsystem.request.RequestState.CANCELLED) {
        transitionFlow(
            manager,
            request,
            CreateShopFlowState.CANCELLED,
            "tickPending:state-cancelled",
            "",
            0,
            "com.thesettler_x_create.message.createshop.flow_cancelled");
        if (Config.DEBUG_LOGGING.getAsBoolean()) {
          TheSettlerXCreate.LOGGER.info(
              "[CreateShop] tickPending: " + request.getId() + " skip (state cancelled)");
        }
        continue;
      }
      diagnostics.logRequestStateChange(standardManager, token, "tickPending");
      if (!(request.getRequest() instanceof IDeliverable deliverable)) {
        diagnostics.logPendingReasonChange(token, "skip:not-deliverable");
        continue;
      }
      String requestIdLog = request.getId().toString();
      UUID requestId = toRequestId(request.getId());
      int reservedForRequest = pickup.getReservedForRequest(requestId);
      boolean onCooldown = cooldown.isRequestOnCooldown(level, request.getId());
      if (request.hasChildren()) {
        diagnostics.logPendingReasonChange(request.getId(), "skip:has-children");
        java.util.Collection<IToken<?>> children =
            java.util.Objects.requireNonNull(request.getChildren(), "children");
        int missing = 0;
        int duplicateChildrenRemoved = 0;
        boolean hasActiveChildren = false;
        IToken<?> activeLocalDeliveryChild = null;
        if (!children.isEmpty()) {
          java.util.Set<IToken<?>> seenChildren = new java.util.HashSet<>();
          for (IToken<?> childToken : java.util.List.copyOf(children)) {
            if (!seenChildren.add(childToken)) {
              request.removeChild(childToken);
              duplicateChildrenRemoved++;
              if (Config.DEBUG_LOGGING.getAsBoolean()) {
                TheSettlerXCreate.LOGGER.info(
                    "[CreateShop] tickPending: {} child {} duplicate -> removed",
                    requestIdLog,
                    childToken);
              }
              continue;
            }
            try {
              IRequest<?> child = requestHandler.getRequest(childToken);
              if (child == null) {
                hasActiveChildren = true;
                if (Config.DEBUG_LOGGING.getAsBoolean()) {
                  TheSettlerXCreate.LOGGER.info(
                      "[CreateShop] tickPending: {} child {} missing -> keep (fail-open)",
                      requestIdLog,
                      childToken);
                }
                continue;
              }
              if (child != null
                  && child.getRequest()
                      instanceof
                      com.minecolonies.api.colony.requestsystem.requestable.deliveryman.Delivery) {
                if (!isLocalShopDeliveryChild(child, shop, pickup)) {
                  hasActiveChildren = true;
                  if (Config.DEBUG_LOGGING.getAsBoolean()) {
                    TheSettlerXCreate.LOGGER.info(
                        "[CreateShop] tickPending: {} child {} skip (non-local delivery child)",
                        requestIdLog,
                        childToken);
                  }
                  continue;
                }
                var childAssigned = assignmentStore.getAssignmentForValue(childToken);
                if (childAssigned == null) {
                  if (child.getState()
                      == com.minecolonies.api.colony.requestsystem.request.RequestState.CREATED) {
                    try {
                      standardManager.assignRequest(childToken);
                    } catch (Exception ignored) {
                      // Best-effort kick so native delivery resolver can pick up CREATED children.
                    }
                    childAssigned = assignmentStore.getAssignmentForValue(childToken);
                    if (Config.DEBUG_LOGGING.getAsBoolean()) {
                      TheSettlerXCreate.LOGGER.info(
                          "[CreateShop] tickPending: {} child {} CREATED assignKick={}",
                          requestIdLog,
                          childToken,
                          childAssigned == null ? "none" : "ok");
                    }
                  }
                }
                if (childAssigned == null) {
                  boolean enqueued =
                      deliveryManager.tryEnqueueDelivery(standardManager, childToken);
                  if (Config.DEBUG_LOGGING.getAsBoolean()) {
                    TheSettlerXCreate.LOGGER.info(
                        "[CreateShop] tickPending: {} child {} unassigned delivery -> enqueue={}",
                        requestIdLog,
                        childToken,
                        enqueued ? "ok" : "none");
                  }
                }
                var childState = child.getState();
                boolean terminalChild =
                    childState
                            == com.minecolonies.api.colony.requestsystem.request.RequestState
                                .COMPLETED
                        || childState
                            == com.minecolonies.api.colony.requestsystem.request.RequestState
                                .CANCELLED
                        || childState
                            == com.minecolonies.api.colony.requestsystem.request.RequestState.FAILED
                        || childState
                            == com.minecolonies.api.colony.requestsystem.request.RequestState
                                .RESOLVED
                        || childState
                            == com.minecolonies.api.colony.requestsystem.request.RequestState
                                .RECEIVED;
                if (terminalChild) {
                  request.removeChild(childToken);
                  deliveryChildActiveSince.remove(childToken);
                  clearStaleRecoveryArm(request.getId());
                } else {
                  if (activeLocalDeliveryChild != null
                      && !activeLocalDeliveryChild.equals(childToken)) {
                    boolean recovered =
                        recoverExtraActiveDeliveryChild(
                            standardManager, level, request, childToken, child, shop, pickup);
                    if (recovered) {
                      missing++;
                      continue;
                    }
                  } else {
                    activeLocalDeliveryChild = childToken;
                  }
                  if (isStaleDeliveryChild(level, request.getId(), childToken, childState)) {
                    if (!isStaleRecoveryArmed(level, standardManager, request.getId())) {
                      hasActiveChildren = true;
                      continue;
                    }
                    boolean recovered =
                        recoverStaleDeliveryChild(
                            standardManager, level, request, childToken, child, shop, pickup);
                    if (recovered) {
                      missing++;
                      continue;
                    }
                  } else {
                    clearStaleRecoveryArm(request.getId());
                  }
                  hasActiveChildren = true;
                }
              } else {
                deliveryChildActiveSince.remove(childToken);
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
              hasActiveChildren = true;
              if (Config.DEBUG_LOGGING.getAsBoolean()) {
                TheSettlerXCreate.LOGGER.info(
                    "[CreateShop] tickPending: {} child {} lookup failed -> keep (fail-open): {}",
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
          diagnostics.logParentChildrenState(standardManager, request.getId(), "tickPending");
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
          } else if (duplicateChildrenRemoved > 0) {
            TheSettlerXCreate.LOGGER.info(
                "[CreateShop] tickPending: {} duplicate children removed={} total={}",
                requestIdLog,
                duplicateChildrenRemoved,
                children.size());
          }
        }
        if (missing > 0) {
          continue;
        }
        if (!hasActiveChildren) {
          parentDeliveryActiveSince.remove(request.getId());
          clearStaleRecoveryArm(request.getId());
        }
        if (hasActiveChildren || request.hasChildren()) {
          continue;
        }
      }
      parentDeliveryActiveSince.remove(request.getId());
      clearStaleRecoveryArm(request.getId());
      if (hasDeliveriesCreated(request.getId())) {
        diagnostics.logPendingReasonChange(request.getId(), "wait:delivery-in-progress");
        flowStateMachine.touch(
            request.getId(), level.getGameTime(), "tickPending:delivery-in-progress");
        if (Config.DEBUG_LOGGING.getAsBoolean()) {
          TheSettlerXCreate.LOGGER.info(
              "[CreateShop] tickPending: {} waiting (delivery in progress, topup blocked)",
              requestIdLog);
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
        pendingCount = pendingTracker.getPendingCount(request.getId());
      }
      if (pendingCount <= 0) {
        int derivedPending = computeOutstandingNeeded(request, deliverable, reservedForRequest);
        if (derivedPending > 0) {
          pendingTracker.setPendingCount(request.getId(), derivedPending);
          pendingCount = derivedPending;
          diagnostics.recordPendingSource(request.getId(), "tickPending:derived-needed");
          if (Config.DEBUG_LOGGING.getAsBoolean()) {
            TheSettlerXCreate.LOGGER.info(
                "[CreateShop] tickPending: {} derived pending from request={} (reservedForRequest={})",
                requestIdLog,
                derivedPending,
                reservedForRequest);
          }
        }
      }
      if (pendingCount <= 0 && !onCooldown) {
        diagnostics.logPendingReasonChange(
            request.getId(),
            "skip:no-pending reserved="
                + reservedForRequest
                + " pending="
                + pendingTracker.getPendingCount(request.getId()));
        if (Config.DEBUG_LOGGING.getAsBoolean()) {
          TheSettlerXCreate.LOGGER.info(
              "[CreateShop] tickPending: {} skip (no pending)", requestIdLog);
        }
        continue;
      }
      if (pendingCount <= 0) {
        diagnostics.logPendingReasonChange(
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
      if (!workerAvailabilityGate.shouldResumePending(workerWorking, pendingCount)) {
        flowStateMachine.touch(
            request.getId(), level.getGameTime(), "tickPending:worker-unavailable");
        if (workerAvailabilityGate.shouldKeepPendingState(workerWorking, pendingCount)) {
          cooldown.markRequestOrdered(level, request.getId());
          pendingTracker.setPendingCount(request.getId(), pendingCount);
          diagnostics.recordPendingSource(request.getId(), "tickPending:worker-unavailable");
        }
        diagnostics.logPendingReasonChange(request.getId(), "wait:worker-not-working");
        if (Config.DEBUG_LOGGING.getAsBoolean()) {
          TheSettlerXCreate.LOGGER.info(
              "[CreateShop] tickPending: {} waiting (worker unavailable, pendingCount={})",
              requestIdLog,
              pendingCount);
        }
        continue;
      }
      int rackAvailable = planning.getAvailableFromRacks(tile, deliverable);
      int reservedSynced =
          syncReservationsFromRack(
              tile,
              pickup,
              requestId,
              request.getId(),
              deliverable,
              pendingCount,
              reservedForRequest,
              rackAvailable,
              level.getGameTime());
      if (reservedSynced > 0) {
        reservedForRequest += reservedSynced;
      }
      int topupNeeded =
          Math.max(0, pendingCount - Math.max(0, reservedForRequest) - Math.max(0, rackAvailable));
      if (workerWorking && topupNeeded > 0) {
        int networkAvailable = stockResolver.getNetworkAvailable(tile, deliverable);
        int topupCount = Math.min(networkAvailable, topupNeeded);
        if (topupCount > 0) {
          String requesterName = messaging.resolveRequesterName(manager, request);
          List<ItemStack> topupOrdered =
              stockResolver.requestFromNetwork(tile, deliverable, topupCount, requesterName);
          if (!topupOrdered.isEmpty()) {
            for (ItemStack stack : topupOrdered) {
              if (stack.isEmpty()) {
                continue;
              }
              pickup.reserve(requestId, stack.copy(), stack.getCount());
            }
            cooldown.markRequestOrdered(level, request.getId());
            pendingTracker.setPendingCount(request.getId(), pendingCount);
            diagnostics.recordPendingSource(request.getId(), "tickPending:network-topup");
            flowStateMachine.touch(
                request.getId(), level.getGameTime(), "tickPending:network-topup");
            messaging.sendShopChat(
                manager, "com.thesettler_x_create.message.createshop.request_sent", topupOrdered);
            if (Config.DEBUG_LOGGING.getAsBoolean()) {
              TheSettlerXCreate.LOGGER.info(
                  "[CreateShop] tickPending: {} network topup ordered={} pending={} reserved={}",
                  requestIdLog,
                  countStackList(topupOrdered),
                  pendingCount,
                  reservedForRequest);
            }
          }
        }
      } else if (!workerWorking && topupNeeded > 0) {
        flowStateMachine.touch(
            request.getId(), level.getGameTime(), "tickPending:worker-idle-topup");
        diagnostics.logPendingReasonChange(request.getId(), "wait:worker-for-network-topup");
      }
      int totalAvailable = rackAvailable;
      if (totalAvailable <= 0) {
        flowStateMachine.touch(request.getId(), level.getGameTime(), "tickPending:waiting-arrival");
        diagnostics.logPendingReasonChange(
            request.getId(),
            "wait:available="
                + totalAvailable
                + " rack="
                + rackAvailable
                + " pending="
                + pendingCount);
        if (pendingState.shouldNotifyPending(level, request.getId())) {
          messaging.sendShopChat(
              manager,
              "com.thesettler_x_create.message.createshop.delivery_waiting",
              java.util.Collections.singletonList(deliverable.getResult()));
        }
        if (Config.DEBUG_LOGGING.getAsBoolean()) {
          TheSettlerXCreate.LOGGER.info(
              "[CreateShop] tickPending: {} waiting (available={}, rackAvailable={}, pendingCount={})",
              requestIdLog,
              totalAvailable,
              rackAvailable,
              pendingCount);
        }
        continue;
      }
      int deliverCount = Math.min(totalAvailable, pendingCount);
      List<com.minecolonies.api.util.Tuple<ItemStack, BlockPos>> stacks =
          planning.planFromRacksWithPositions(tile, deliverable, deliverCount);
      if (stacks.isEmpty()) {
        flowStateMachine.touch(request.getId(), level.getGameTime(), "tickPending:plan-empty");
        diagnostics.logPendingReasonChange(request.getId(), "wait:plan-empty");
        if (Config.DEBUG_LOGGING.getAsBoolean()) {
          TheSettlerXCreate.LOGGER.info(
              "[CreateShop] tickPending: {} skip (plan empty, rackAvailable={}, pendingCount={})",
              requestIdLog,
              rackAvailable,
              pendingCount);
        }
        continue;
      }
      if (Config.DEBUG_LOGGING.getAsBoolean()) {
        TheSettlerXCreate.LOGGER.info(
            "[CreateShop] tickPending: {} creating deliveries (stacks={}, deliverCount={}, pendingCount={}, rackAvailable={})",
            requestIdLog,
            stacks.size(),
            deliverCount,
            pendingCount,
            rackAvailable);
      }
      List<IToken<?>> created =
          deliveryManager.createDeliveriesFromStacks(manager, request, stacks, pickup);
      if (created.isEmpty()) {
        flowStateMachine.touch(request.getId(), level.getGameTime(), "tickPending:create-failed");
        diagnostics.logPendingReasonChange(request.getId(), "create:failed");
        if (Config.DEBUG_LOGGING.getAsBoolean()) {
          TheSettlerXCreate.LOGGER.info(
              "[CreateShop] tickPending: {} create failed (no deliveries created)", requestIdLog);
        }
        continue;
      }
      List<ItemStack> ordered = planning.extractStacks(stacks);
      transitionFlow(
          manager,
          request,
          CreateShopFlowState.ARRIVED_IN_SHOP_RACK,
          "tickPending:rack-arrived",
          describeStack(ordered.isEmpty() ? ItemStack.EMPTY : ordered.get(0)),
          countStackList(ordered),
          "com.thesettler_x_create.message.createshop.flow_arrived");
      messaging.sendShopChat(
          manager, "com.thesettler_x_create.message.createshop.delivery_created", ordered);
      transitionFlow(
          manager,
          request,
          CreateShopFlowState.DELIVERY_CREATED,
          "tickPending:delivery-created",
          describeStack(ordered.isEmpty() ? ItemStack.EMPTY : ordered.get(0)),
          countStackList(ordered),
          "com.thesettler_x_create.message.createshop.flow_delivery_created");
      diagnostics.logPendingReasonChange(request.getId(), "create:delivery");
      int deliveredCount = planning.countPlanned(stacks);
      if (reservedForRequest > 0 && deliveredCount > 0) {
        consumeReservedForRequest(pickup, requestId, stacks);
        transitionFlow(
            manager,
            request,
            CreateShopFlowState.RESERVED_FOR_DELIVERY,
            "tickPending:reserved-consumed",
            describeStack(ordered.isEmpty() ? ItemStack.EMPTY : ordered.get(0)),
            deliveredCount,
            "com.thesettler_x_create.message.createshop.flow_reserved");
      }
      int remainingCount = Math.max(0, pendingCount - deliveredCount);
      if (remainingCount > 0) {
        pendingTracker.setPendingCount(request.getId(), remainingCount);
        cooldown.markRequestOrdered(level, request.getId());
        diagnostics.recordPendingSource(request.getId(), "tickPending:partial");
      } else {
        pendingTracker.remove(request.getId());
        cooldown.clearRequestCooldown(request.getId());
      }
      if (Config.DEBUG_LOGGING.getAsBoolean()) {
        TheSettlerXCreate.LOGGER.info(
            "[CreateShop] tickPending: {} skip assignRequest (delivery created)", requestIdLog);
      }
    }
    processTimedOutFlows(standardManager, level);
    lastTickPendingNanos = System.nanoTime() - perfStart;
    maybeLogPerf(level);
  }

  private boolean shouldLogTickPending(Level level) {
    long now = level.getGameTime();
    if (now == 0L) {
      return true;
    }
    synchronized (debugLock) {
      if (now - lastTickPendingDebugTime >= Config.TICK_PENDING_DEBUG_COOLDOWN.getAsLong()) {
        lastTickPendingDebugTime = now;
        return true;
      }
      return false;
    }
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

  private int computeOutstandingNeeded(
      IRequest<?> request, IDeliverable deliverable, int reservedForRequest) {
    if (request == null || deliverable == null) {
      return 0;
    }
    int needed = deliverable.getCount();
    if (deliverable instanceof INonExhaustiveDeliverable nonExhaustive) {
      needed -= nonExhaustive.getLeftOver();
    }
    needed = Math.max(0, needed - Math.max(0, reservedForRequest));
    return needed;
  }

  private java.util.Set<IToken<?>> collectAssignedTokensFromLocalResolvers(
      IStandardRequestManager manager,
      Map<IToken<?>, java.util.Collection<IToken<?>>> assignments) {
    java.util.Set<IToken<?>> recovered = new java.util.LinkedHashSet<>();
    if (manager == null || assignments == null || assignments.isEmpty()) {
      return recovered;
    }
    for (Map.Entry<IToken<?>, java.util.Collection<IToken<?>>> entry : assignments.entrySet()) {
      java.util.Collection<IToken<?>> values = entry.getValue();
      if (values == null || values.isEmpty()) {
        continue;
      }
      IToken<?> resolverToken = entry.getKey();
      IRequestResolver<?> resolver;
      try {
        resolver = manager.getResolverHandler().getResolver(resolverToken);
      } catch (Exception ignored) {
        continue;
      }
      if (resolver instanceof CreateShopRequestResolver shopResolver
          && isLocalShopResolver(shopResolver)) {
        recovered.addAll(values);
      }
    }
    return recovered;
  }

  private java.util.Set<IToken<?>> collectAssignedTokensByRequestResolver(
      IStandardRequestManager manager,
      Map<IToken<?>, java.util.Collection<IToken<?>>> assignments) {
    java.util.Set<IToken<?>> recovered = new java.util.LinkedHashSet<>();
    if (manager == null || assignments == null || assignments.isEmpty()) {
      return recovered;
    }
    var requestHandler = manager.getRequestHandler();
    if (requestHandler == null) {
      return recovered;
    }
    for (java.util.Collection<IToken<?>> values : assignments.values()) {
      if (values == null || values.isEmpty()) {
        continue;
      }
      for (IToken<?> token : values) {
        IRequest<?> request;
        try {
          request = requestHandler.getRequest(token);
        } catch (Exception ignored) {
          continue;
        }
        if (request == null) {
          continue;
        }
        IRequestResolver<?> ownerResolver;
        try {
          ownerResolver = manager.getResolverHandler().getResolverForRequest(request);
        } catch (Exception ignored) {
          continue;
        }
        if (ownerResolver instanceof CreateShopRequestResolver shopResolver
            && isLocalShopResolver(shopResolver)) {
          recovered.add(token);
        }
      }
    }
    return recovered;
  }

  private boolean isLocalShopResolver(CreateShopRequestResolver resolver) {
    if (resolver == null || resolver.getLocation() == null || getLocation() == null) {
      return false;
    }
    return resolver.getLocation().getDimension().equals(getLocation().getDimension())
        && resolver
            .getLocation()
            .getInDimensionLocation()
            .equals(getLocation().getInDimensionLocation());
  }

  private boolean isRequestOwnedByLocalResolver(
      IStandardRequestManager manager, IRequest<?> request) {
    if (manager == null || request == null) {
      return false;
    }
    try {
      IRequestResolver<?> owner = manager.getResolverHandler().getResolverForRequest(request);
      if (owner instanceof CreateShopRequestResolver shopResolver) {
        return isLocalShopResolver(shopResolver);
      }
    } catch (Exception ignored) {
      // Treat unresolved owner as stale for this resolver tick.
    }
    return false;
  }

  public static void onDeliveryCancelled(IRequestManager manager, IRequest<?> request) {
    CreateShopRequestResolver resolver = findResolverForDelivery(manager, request);
    if (resolver == null) {
      resolver = findResolverByDeliveryToken(manager, request);
    }
    if (resolver != null) {
      resolver.handleDeliveryCancelled(manager, request);
    }
  }

  public static void onDeliveryComplete(IRequestManager manager, IRequest<?> request) {
    CreateShopRequestResolver resolver = findResolverForDelivery(manager, request);
    if (resolver == null) {
      resolver = findResolverByDeliveryToken(manager, request);
    }
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
    if (request.getId() != null) {
      deliveryChildActiveSince.remove(request.getId());
    }
    IToken<?> parentToken = resolveParentTokenForDelivery(manager, request);
    if (parentToken == null) {
      return;
    }
    parentDeliveryActiveSince.remove(parentToken);
    clearStaleRecoveryArm(parentToken);
    UUID parentRequestId = toRequestId(parentToken);
    ItemStack stack = delivery.getStack().copy();

    Level level = manager.getColony().getWorld();
    if (level == null) {
      pendingTracker.setPendingCount(parentToken, Math.max(1, stack.getCount()));
      diagnostics.recordPendingSource(parentToken, "delivery-cancel");
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
    if (pickup == null || !isDeliveryFromPickup(delivery, pickup)) {
      return;
    }

    int reservedForRequest = pickup.getReservedForRequest(parentRequestId);
    int pendingCount = Math.max(1, Math.max(reservedForRequest, stack.getCount()));
    pickup.release(parentRequestId);
    pendingTracker.setPendingCount(parentToken, pendingCount);
    diagnostics.recordPendingSource(parentToken, "delivery-cancel-reserve");
    // Keep on cooldown so tickPending can retry once stock is available.
    cooldown.markRequestOrdered(level, parentToken);
    clearDeliveriesCreated(parentToken);

    if (isDebugLoggingEnabled()) {
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
      IStandardRequestManager standard = unwrapStandardManager(manager);
      if (standard != null) {
        diagnostics.logParentChildrenState(standard, parentToken, "delivery-cancel");
        recheck.scheduleParentChildRecheck(standard, parentToken);
      }
    }
  }

  private void handleDeliveryComplete(IRequestManager manager, IRequest<?> request) {
    if (request != null && request.getId() != null) {
      deliveryChildActiveSince.remove(request.getId());
    }
    IToken<?> parentToken = resolveParentTokenForDelivery(manager, request);
    if (parentToken == null) {
      return;
    }
    parentDeliveryActiveSince.remove(parentToken);
    clearStaleRecoveryArm(parentToken);
    IRequest<?> parentRequest = null;
    IStandardRequestManager standard = unwrapStandardManager(manager);
    if (standard != null) {
      try {
        parentRequest = standard.getRequestHandler().getRequest(parentToken);
      } catch (Exception ignored) {
        // Ignore lookup failures; callbacks remain best-effort.
      }
    }
    if (parentRequest != null) {
      transitionFlow(
          manager,
          parentRequest,
          CreateShopFlowState.DELIVERY_COMPLETED,
          "delivery-complete",
          describeStack(
              request.getRequest() instanceof Delivery d ? d.getStack() : ItemStack.EMPTY),
          request.getRequest() instanceof Delivery d ? d.getStack().getCount() : 0,
          "com.thesettler_x_create.message.createshop.flow_delivery_completed");
    }
    if (isDebugLoggingEnabled()
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
        if (pickup == null || !isDeliveryFromPickup(delivery, pickup)) {
          return;
        }
        UUID parentRequestId = toRequestId(parentToken);
        ItemStack stack = delivery.getStack().copy();
        int reservedForRequest = pickup.getReservedForRequest(parentRequestId);
        int reservedForStack = pickup.getReservedFor(stack);
        BlockPos pickupPosition = pickup.getBlockPos();
        deliveryManager.logDeliveryDiagnostics(
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
    int pending = pendingTracker.getPendingCount(parentToken);
    if (pending > 0) {
      // Keep cooldown so tickPending continues creating partial deliveries.
      if (manager != null && manager.getColony() != null) {
        cooldown.markRequestOrdered(manager.getColony().getWorld(), parentToken);
      }
    } else {
      cooldown.clearRequestCooldown(parentToken);
      pendingTracker.remove(parentToken);
    }
    if (isDebugLoggingEnabled()) {
      IStandardRequestManager debugManager = unwrapStandardManager(manager);
      if (debugManager != null) {
        try {
          var handler = debugManager.getRequestHandler();
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
          diagnostics.logParentChildrenState(debugManager, parentToken, "delivery-complete");
          recheck.scheduleParentChildRecheck(debugManager, parentToken);
        } catch (Exception ignored) {
          // Ignore lookup errors.
        }
      }
    }
  }

  @Override
  public void onAssignedRequestBeingCancelled(
      @NotNull IRequestManager manager, @NotNull IRequest<? extends IDeliverable> request) {
    transitionFlow(
        manager,
        request,
        CreateShopFlowState.CANCELLED,
        "assigned-cancelled",
        "",
        0,
        "com.thesettler_x_create.message.createshop.flow_cancelled");
    pendingTracker.remove(request.getId());
    releaseReservation(manager, request);
    cooldown.clearRequestCooldown(request.getId());
    clearDeliveriesCreated(request.getId());
    clearTrackedChildrenForParent(unwrapStandardManager(manager), request.getId());
  }

  @Override
  public void onAssignedRequestCancelled(
      @NotNull IRequestManager manager, @NotNull IRequest<? extends IDeliverable> request) {
    transitionFlow(
        manager,
        request,
        CreateShopFlowState.CANCELLED,
        "assigned-cancelled-post",
        "",
        0,
        "com.thesettler_x_create.message.createshop.flow_cancelled");
    pendingTracker.remove(request.getId());
    releaseReservation(manager, request);
    cooldown.clearRequestCooldown(request.getId());
    clearDeliveriesCreated(request.getId());
    clearTrackedChildrenForParent(unwrapStandardManager(manager), request.getId());
  }

  @Override
  public void onRequestedRequestComplete(
      @NotNull IRequestManager manager, @NotNull IRequest<?> request) {
    transitionFlow(
        manager,
        request,
        CreateShopFlowState.REQUEST_COMPLETED,
        "request-completed",
        "",
        0,
        "com.thesettler_x_create.message.createshop.flow_request_completed");
    cooldown.clearRequestCooldown(request.getId());
    clearDeliveriesCreated(request.getId());
    pendingTracker.remove(request.getId());
    clearTrackedChildrenForParent(unwrapStandardManager(manager), request.getId());
    if (request.getRequest() instanceof IDeliverable) {
      releaseReservation(manager, request);
    }
  }

  @Override
  public void onRequestedRequestCancelled(
      @NotNull IRequestManager manager, @NotNull IRequest<?> request) {
    transitionFlow(
        manager,
        request,
        CreateShopFlowState.CANCELLED,
        "request-cancelled",
        "",
        0,
        "com.thesettler_x_create.message.createshop.flow_cancelled");
    pendingTracker.remove(request.getId());
    cooldown.clearRequestCooldown(request.getId());
    clearDeliveriesCreated(request.getId());
    clearTrackedChildrenForParent(unwrapStandardManager(manager), request.getId());
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
    if (request == null || getLocation() == null) {
      return 0;
    }
    IDeliverable deliverable = request.getRequest();
    if (deliverable == null) {
      return 0;
    }
    var colonyManager = com.minecolonies.api.colony.IColonyManager.getInstance();
    if (colonyManager == null) {
      return 0;
    }
    var colony =
        colonyManager.getColonyByPosFromDim(
            getLocation().getDimension(), getLocation().getInDimensionLocation());
    if (colony == null || colony.getServerBuildingManager() == null) {
      return 0;
    }
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
    int available = stockResolver.getNetworkAvailable(tile, deliverable);
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

  void releaseReservation(IRequestManager manager, IRequest<?> request) {
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

  private void consumeReservedForRequest(
      CreateShopBlockEntity pickup,
      UUID requestId,
      List<com.minecolonies.api.util.Tuple<ItemStack, BlockPos>> stacks) {
    if (pickup == null || requestId == null || stacks == null) {
      return;
    }
    for (var entry : stacks) {
      if (entry == null) {
        continue;
      }
      ItemStack stack = entry.getA();
      if (stack == null || stack.isEmpty()) {
        continue;
      }
      pickup.consumeReservedForRequest(requestId, stack, stack.getCount());
    }
  }

  UUID toRequestId(IToken<?> token) {
    Object id = token == null ? null : token.getIdentifier();
    if (id instanceof UUID uuid) {
      return uuid;
    }
    return UUID.nameUUIDFromBytes(
        String.valueOf(id).getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }

  static IStandardRequestManager unwrapStandardManager(IRequestManager manager) {
    return manager instanceof IStandardRequestManager standard ? standard : null;
  }

  private static CreateShopRequestResolver findResolverByDeliveryToken(
      IRequestManager manager, IRequest<?> request) {
    if (manager == null || request == null) {
      return null;
    }
    IToken<?> deliveryToken = request.getId();
    if (deliveryToken == null) {
      return null;
    }
    IToken<?> parentToken = findParentTokenByChild(manager, deliveryToken);
    if (parentToken == null) {
      return null;
    }
    IStandardRequestManager standard = unwrapStandardManager(manager);
    if (standard == null) {
      return null;
    }
    try {
      IRequest<?> parent = standard.getRequestHandler().getRequest(parentToken);
      if (parent == null) {
        return null;
      }
      var resolver = standard.getResolverHandler().getResolverForRequest(parent);
      if (resolver instanceof CreateShopRequestResolver shopResolver) {
        return shopResolver;
      }
    } catch (Exception ignored) {
      // Ignore lookup errors.
    }
    return null;
  }

  private static IToken<?> findParentTokenByChild(IRequestManager manager, IToken<?> childToken) {
    if (manager == null || childToken == null) {
      return null;
    }
    IStandardRequestManager standard = unwrapStandardManager(manager);
    if (standard == null) {
      return null;
    }
    var assignments = standard.getRequestResolverRequestAssignmentDataStore().getAssignments();
    if (assignments == null || assignments.isEmpty() || standard.getRequestHandler() == null) {
      return null;
    }
    for (java.util.Collection<IToken<?>> requestTokens : assignments.values()) {
      if (requestTokens == null || requestTokens.isEmpty()) {
        continue;
      }
      for (IToken<?> requestToken : requestTokens) {
        IRequest<?> candidateParent;
        try {
          candidateParent = standard.getRequestHandler().getRequest(requestToken);
        } catch (Exception ignored) {
          continue;
        }
        if (candidateParent == null || !candidateParent.hasChildren()) {
          continue;
        }
        var children = candidateParent.getChildren();
        if (children != null && children.contains(childToken)) {
          return candidateParent.getId();
        }
      }
    }
    return null;
  }

  private IToken<?> resolveParentTokenForDelivery(IRequestManager manager, IRequest<?> request) {
    if (request == null) {
      return null;
    }
    IToken<?> parentToken = request.getParent();
    if (parentToken != null) {
      return parentToken;
    }
    return findParentTokenByChild(manager, request.getId());
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

  boolean hasDeliveriesCreated(IToken<?> token) {
    return pendingTracker.isDeliveryCreated(token);
  }

  void markDeliveriesCreated(IToken<?> token) {
    pendingTracker.markDeliveryCreated(token);
  }

  void clearDeliveriesCreated(IToken<?> token) {
    pendingTracker.clearDeliveryCreated(token);
  }

  String tryDescribeResolver(Object resolver) {
    return resolver == null ? "<none>" : resolver.getClass().getSimpleName();
  }

  private static boolean isDebugLoggingEnabled() {
    try {
      return Config.DEBUG_LOGGING.getAsBoolean();
    } catch (IllegalStateException ignored) {
      return false;
    }
  }

  int getMaxChainSanitizeNodes() {
    return MAX_CHAIN_SANITIZE_NODES;
  }

  java.util.Set<String> getChainCycleLogged() {
    return chainCycleLogged;
  }

  java.util.Map<IToken<?>, String> getParentChildrenSnapshots() {
    return parentChildrenSnapshots;
  }

  java.util.Map<IToken<?>, Long> getParentChildrenRecheck() {
    return parentChildrenRecheck;
  }

  java.util.Map<IToken<?>, String> getRequestStateSnapshots() {
    return requestStateSnapshots;
  }

  java.util.Map<IToken<?>, String> getPendingReasonSnapshots() {
    return pendingReasonSnapshots;
  }

  java.util.Map<IToken<?>, Long> getPendingNotices() {
    return pendingNotices;
  }

  java.util.Set<IToken<?>> getCancelledRequests() {
    return cancelledRequests;
  }

  CreateShopResolverPlanning getPlanning() {
    return planning;
  }

  CreateShopResolverChain getChain() {
    return chain;
  }

  CreateShopResolverCooldown getCooldown() {
    return cooldown;
  }

  CreateShopPendingDeliveryTracker getPendingTracker() {
    return pendingTracker;
  }

  CreateShopStockResolver getStockResolver() {
    return stockResolver;
  }

  BuildingCreateShop getShopForValidator(IRequestManager manager) {
    return getShop(manager);
  }

  java.util.Set<String> getDeliveryCreateLogged() {
    return deliveryCreateLogged;
  }

  java.util.Set<String> getDeliveryLinkLogged() {
    return deliveryLinkLogged;
  }

  IToken<?> getResolverToken() {
    return getId();
  }

  public boolean hasActiveWork() {
    if (pendingTracker.hasEntries()) {
      return true;
    }
    if (cooldown.getOrderedCount() > 0) {
      return true;
    }
    return !flowStateMachine.snapshot().isEmpty();
  }

  void logDeliveryLinkStateForOps(
      String stage, IStandardRequestManager manager, IToken<?> parentToken, IToken<?> childToken) {
    logDeliveryLinkState(stage, manager, parentToken, childToken);
  }

  private long resolveNowTick(IRequestManager manager) {
    if (manager == null || manager.getColony() == null || manager.getColony().getWorld() == null) {
      return 0L;
    }
    return manager.getColony().getWorld().getGameTime();
  }

  private void transitionFlow(
      IRequestManager manager,
      IRequest<?> request,
      CreateShopFlowState state,
      String detail,
      String stackLabel,
      int amount,
      String messageKey) {
    if (manager == null || request == null) {
      return;
    }
    long now = resolveNowTick(manager);
    boolean changed =
        flowStateMachine.transition(
            request.getId(), state, now, detail, stackLabel, Math.max(0, amount));
    if (changed && messageKey != null) {
      messaging.sendFlowStep(manager, messageKey, request, stackLabel, amount);
    }
  }

  private void processTimedOutFlows(IStandardRequestManager manager, Level level) {
    long timeout = getInflightTimeoutTicksSafe();
    for (CreateShopFlowRecord record :
        flowStateMachine.collectTimedOut(level.getGameTime(), timeout)) {
      IToken<?> token = record.getRequestToken();
      IRequest<?> request = null;
      try {
        request = manager.getRequestHandler().getRequest(token);
      } catch (Exception ignored) {
        // Missing requests are cleaned up below.
      }
      if (request != null) {
        transitionFlow(
            manager,
            request,
            CreateShopFlowState.FAILED,
            "timeout-cleanup",
            record.getStackLabel(),
            record.getAmount(),
            "com.thesettler_x_create.message.createshop.flow_timeout");
        if (request.getRequest() instanceof IDeliverable) {
          releaseReservation(manager, request);
        }
      }
      pendingTracker.remove(token);
      cooldown.clearRequestCooldown(token);
      clearDeliveriesCreated(token);
      parentDeliveryActiveSince.remove(token);
      clearStaleRecoveryArm(token);
      clearTrackedChildrenForParent(manager, token);
      flowStateMachine.remove(token);
    }
  }

  private boolean isStaleRecoveryArmed(
      Level level, IStandardRequestManager manager, IToken<?> parentToken) {
    if (level == null || manager == null || parentToken == null) {
      return false;
    }
    long now = level.getGameTime();
    Long armedAt = parentStaleRecoveryArmedAt.get(parentToken);
    if (armedAt == null) {
      parentStaleRecoveryArmedAt.put(parentToken, now);
      recheck.scheduleParentChildRecheck(manager, parentToken);
      return false;
    }
    long staleRecheckDelay = 20L;
    if (now - armedAt < staleRecheckDelay) {
      return false;
    }
    return true;
  }

  private void clearStaleRecoveryArm(IToken<?> parentToken) {
    if (parentToken == null) {
      return;
    }
    parentStaleRecoveryArmedAt.remove(parentToken);
  }

  private boolean isStaleDeliveryChild(
      Level level,
      IToken<?> parentToken,
      IToken<?> childToken,
      com.minecolonies.api.colony.requestsystem.request.RequestState state) {
    if (level == null || parentToken == null || childToken == null || state == null) {
      return false;
    }
    boolean activeState =
        state == com.minecolonies.api.colony.requestsystem.request.RequestState.CREATED
            || state == com.minecolonies.api.colony.requestsystem.request.RequestState.ASSIGNED
            || state == com.minecolonies.api.colony.requestsystem.request.RequestState.IN_PROGRESS;
    if (!activeState) {
      deliveryChildActiveSince.remove(childToken);
      return false;
    }
    long now = level.getGameTime();
    Long since = parentDeliveryActiveSince.putIfAbsent(parentToken, now);
    if (since == null) {
      deliveryChildActiveSince.put(childToken, now);
      return false;
    }
    deliveryChildActiveSince.put(childToken, since);
    long timeout =
        Math.max(DELIVERY_CHILD_STALE_TIMEOUT_FLOOR_TICKS, getInflightTimeoutTicksSafe());
    return now - since >= timeout;
  }

  private long getInflightTimeoutTicksSafe() {
    try {
      return Math.max(
          DELIVERY_CHILD_STALE_TIMEOUT_FLOOR_TICKS, Config.INFLIGHT_TIMEOUT_TICKS.getAsLong());
    } catch (IllegalStateException ignored) {
      return DELIVERY_CHILD_STALE_TIMEOUT_FLOOR_TICKS;
    }
  }

  private boolean recoverStaleDeliveryChild(
      IStandardRequestManager manager,
      Level level,
      IRequest<?> parentRequest,
      IToken<?> childToken,
      IRequest<?> childRequest,
      BuildingCreateShop shop,
      CreateShopBlockEntity pickup) {
    return recoverDeliveryChild(
        manager,
        level,
        parentRequest,
        childToken,
        childRequest,
        shop,
        pickup,
        "stale-child-recovery",
        "[CreateShop] stale delivery-child recovery parent={} child={} stateUpdated={} item={} count={}");
  }

  private boolean recoverExtraActiveDeliveryChild(
      IStandardRequestManager manager,
      Level level,
      IRequest<?> parentRequest,
      IToken<?> childToken,
      IRequest<?> childRequest,
      BuildingCreateShop shop,
      CreateShopBlockEntity pickup) {
    return recoverDeliveryChild(
        manager,
        level,
        parentRequest,
        childToken,
        childRequest,
        shop,
        pickup,
        "extra-active-child-recovery",
        "[CreateShop] extra active delivery-child recovery parent={} child={} stateUpdated={} item={} count={}");
  }

  private boolean recoverDeliveryChild(
      IStandardRequestManager manager,
      Level level,
      IRequest<?> parentRequest,
      IToken<?> childToken,
      IRequest<?> childRequest,
      BuildingCreateShop shop,
      CreateShopBlockEntity pickup,
      String pendingSource,
      String logTemplate) {
    if (manager == null || level == null || parentRequest == null || childToken == null) {
      return false;
    }
    if (!isRequestOwnedByLocalResolver(manager, parentRequest)) {
      clearStaleRecoveryArm(parentRequest.getId());
      return false;
    }
    if (!isLocalShopDeliveryChild(childRequest, shop, pickup)) {
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
    clearDeliveriesCreated(parentRequest.getId());
    int currentPending = pendingTracker.getPendingCount(parentRequest.getId());
    pendingTracker.setPendingCount(parentRequest.getId(), Math.max(currentPending, childCount));
    diagnostics.recordPendingSource(parentRequest.getId(), pendingSource);
    cooldown.markRequestOrdered(level, parentRequest.getId());
    parentDeliveryActiveSince.put(parentRequest.getId(), level.getGameTime());
    clearStaleRecoveryArm(parentRequest.getId());
    deliveryChildActiveSince.put(childToken, level.getGameTime());
    recheck.scheduleParentChildRecheck(manager, parentRequest.getId());
    if (Config.DEBUG_LOGGING.getAsBoolean()) {
      TheSettlerXCreate.LOGGER.info(
          logTemplate, parentRequest.getId(), childToken, stateUpdated, childItem, childCount);
    }
    return true;
  }

  private boolean isLocalShopDeliveryChild(
      IRequest<?> childRequest, BuildingCreateShop shop, CreateShopBlockEntity pickup) {
    if (childRequest == null || shop == null || pickup == null) {
      return false;
    }
    if (!(childRequest.getRequest() instanceof Delivery delivery)) {
      return false;
    }
    ILocation start = delivery.getStart();
    Level level = pickup.getLevel();
    if (start == null || level == null || start.getDimension() == null) {
      return false;
    }
    return level.dimension().equals(start.getDimension())
        && pickup.getBlockPos().equals(start.getInDimensionLocation());
  }

  private boolean isDeliveryFromPickup(Delivery delivery, CreateShopBlockEntity pickup) {
    if (delivery == null || pickup == null || pickup.getLevel() == null) {
      return false;
    }
    ILocation start = delivery.getStart();
    if (start == null || start.getDimension() == null) {
      return false;
    }
    return pickup.getLevel().dimension().equals(start.getDimension())
        && pickup.getBlockPos().equals(start.getInDimensionLocation());
  }

  private void clearTrackedChildrenForParent(
      IStandardRequestManager manager, IToken<?> parentToken) {
    if (manager == null || parentToken == null) {
      return;
    }
    parentDeliveryActiveSince.remove(parentToken);
    clearStaleRecoveryArm(parentToken);
    if (deliveryChildActiveSince.isEmpty()) {
      return;
    }
    var handler = manager.getRequestHandler();
    if (handler == null) {
      return;
    }
    for (IToken<?> childToken : java.util.List.copyOf(deliveryChildActiveSince.keySet())) {
      try {
        IRequest<?> child = handler.getRequest(childToken);
        IToken<?> parent = child == null ? null : child.getParent();
        if (parentToken.equals(parent)) {
          deliveryChildActiveSince.remove(childToken);
        }
      } catch (Exception ignored) {
        deliveryChildActiveSince.remove(childToken);
      }
    }
  }

  private int countStackList(List<ItemStack> stacks) {
    if (stacks == null || stacks.isEmpty()) {
      return 0;
    }
    int total = 0;
    for (ItemStack stack : stacks) {
      if (stack == null || stack.isEmpty()) {
        continue;
      }
      total += stack.getCount();
    }
    return total;
  }

  private int syncReservationsFromRack(
      TileEntityCreateShop tile,
      CreateShopBlockEntity pickup,
      UUID requestId,
      IToken<?> requestToken,
      IDeliverable deliverable,
      int pendingCount,
      int reservedForRequest,
      int rackAvailable,
      long now) {
    if (tile == null
        || pickup == null
        || requestId == null
        || requestToken == null
        || deliverable == null
        || pendingCount <= 0
        || rackAvailable <= 0) {
      return 0;
    }
    int reservedForDeliverable = pickup.getReservedForDeliverable(deliverable);
    int rackUnreserved = Math.max(0, rackAvailable - Math.max(0, reservedForDeliverable));
    int missingReservation = Math.max(0, pendingCount - Math.max(0, reservedForRequest));
    int reserveTarget = Math.min(rackUnreserved, missingReservation);
    if (reserveTarget <= 0) {
      return 0;
    }
    List<com.minecolonies.api.util.Tuple<ItemStack, BlockPos>> reservePlan =
        planning.planFromRacksWithPositions(tile, deliverable, Math.max(1, reserveTarget));
    if (reservePlan.isEmpty()) {
      return 0;
    }
    int reservedNow = 0;
    for (var entry : reservePlan) {
      if (entry == null) {
        continue;
      }
      ItemStack stack = entry.getA();
      if (stack == null || stack.isEmpty()) {
        continue;
      }
      pickup.reserve(requestId, stack.copy(), stack.getCount());
      reservedNow += stack.getCount();
    }
    if (reservedNow > 0) {
      pendingTracker.setPendingCount(requestToken, Math.max(1, pendingCount));
      diagnostics.recordPendingSource(requestToken, "tickPending:reservation-refresh");
      flowStateMachine.touch(requestToken, now, "tickPending:reservation-refresh");
    }
    return reservedNow;
  }

  private void reassignResolvableRetryingRequests(IStandardRequestManager manager, Level level) {
    if (manager == null || level == null) {
      return;
    }
    var assignmentStore = manager.getRequestResolverRequestAssignmentDataStore();
    var requestHandler = manager.getRequestHandler();
    if (assignmentStore == null || requestHandler == null) {
      return;
    }
    Map<IToken<?>, java.util.Collection<IToken<?>>> assignments = assignmentStore.getAssignments();
    if (assignments == null || assignments.isEmpty()) {
      return;
    }
    // Snapshot to avoid ConcurrentModificationException when reassignRequest mutates assignment
    // store.
    java.util.List<Map.Entry<IToken<?>, java.util.Collection<IToken<?>>>> assignmentSnapshot =
        new java.util.ArrayList<>(assignments.entrySet());
    long now = level.getGameTime();
    for (Map.Entry<IToken<?>, java.util.Collection<IToken<?>>> entry : assignmentSnapshot) {
      IToken<?> ownerToken = entry.getKey();
      java.util.Collection<IToken<?>> tokens = entry.getValue();
      if (ownerToken == null || tokens == null || tokens.isEmpty()) {
        continue;
      }
      IRequestResolver<?> ownerResolver;
      try {
        ownerResolver = manager.getResolverHandler().getResolver(ownerToken);
      } catch (Exception ignored) {
        continue;
      }
      if (ownerResolver == null
          || !"StandardRetryingRequestResolver".equals(ownerResolver.getClass().getSimpleName())) {
        continue;
      }
      java.util.List<IToken<?>> tokenSnapshot = new java.util.ArrayList<>(tokens);
      for (IToken<?> requestToken : tokenSnapshot) {
        if (requestToken == null) {
          continue;
        }
        Long last = retryingReassignAttempts.get(requestToken);
        if (last != null && now - last < 40L) {
          continue;
        }
        IRequest<?> request;
        try {
          request = requestHandler.getRequest(requestToken);
        } catch (Exception ignored) {
          continue;
        }
        if (request == null
            || !(request.getRequest() instanceof IDeliverable deliverable)
            || request.getState()
                == com.minecolonies.api.colony.requestsystem.request.RequestState.CANCELLED) {
          continue;
        }
        @SuppressWarnings("unchecked")
        IRequest<? extends IDeliverable> casted = (IRequest<? extends IDeliverable>) request;
        if (!canResolveRequest(manager, casted)) {
          continue;
        }
        retryingReassignAttempts.put(requestToken, now);
        try {
          IToken<?> newResolver =
              manager.reassignRequest(requestToken, java.util.List.of(ownerToken));
          if (Config.DEBUG_LOGGING.getAsBoolean()) {
            TheSettlerXCreate.LOGGER.info(
                "[CreateShop] retrying reassign token={} from={} to={}",
                requestToken,
                ownerToken,
                newResolver);
          }
          // Keep one reassignment per tick to reduce assignment churn and avoid drift races.
          return;
        } catch (Exception ex) {
          if (Config.DEBUG_LOGGING.getAsBoolean()) {
            TheSettlerXCreate.LOGGER.info(
                "[CreateShop] retrying reassign failed token={} from={} error={}",
                requestToken,
                ownerToken,
                ex.getMessage() == null ? "<null>" : ex.getMessage());
          }
        }
      }
    }
  }

  private String describeStack(ItemStack stack) {
    if (stack == null || stack.isEmpty()) {
      return "";
    }
    return stack.getHoverName().getString();
  }
}
