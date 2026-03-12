package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import com.google.common.collect.Lists;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.requestsystem.location.ILocation;
import com.minecolonies.api.colony.requestsystem.manager.IRequestManager;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.request.RequestState;
import com.minecolonies.api.colony.requestsystem.requestable.IDeliverable;
import com.minecolonies.api.colony.requestsystem.requestable.INonExhaustiveDeliverable;
import com.minecolonies.api.colony.requestsystem.requestable.deliveryman.Delivery;
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
  private final java.util.Map<IToken<?>, Long> missingChildSince =
      new java.util.concurrent.ConcurrentHashMap<>();
  private final java.util.Map<IToken<?>, Long> parentDeliveryActiveSince =
      new java.util.concurrent.ConcurrentHashMap<>();
  private final java.util.Map<IToken<?>, Long> parentStaleRecoveryArmedAt =
      new java.util.concurrent.ConcurrentHashMap<>();
  private final java.util.Map<IToken<?>, Integer> parentLastKnownChildCount =
      new java.util.concurrent.ConcurrentHashMap<>();
  private final java.util.Map<IToken<?>, String> parentLastKnownChildren =
      new java.util.concurrent.ConcurrentHashMap<>();
  private final java.util.Map<IToken<?>, Long> parentChildDropLastLogTick =
      new java.util.concurrent.ConcurrentHashMap<>();
  private final java.util.Map<IToken<?>, String> deliveryRootCauseSnapshots =
      new java.util.concurrent.ConcurrentHashMap<>();
  private final java.util.Map<IToken<?>, Long> deliveryRootCauseLastLogTick =
      new java.util.concurrent.ConcurrentHashMap<>();
  private final CreateShopResolverPlanning planning = new CreateShopResolverPlanning();
  private final CreateShopDeliveryManager deliveryManager = new CreateShopDeliveryManager(this);
  private final CreateShopResolverDiagnostics diagnostics = new CreateShopResolverDiagnostics(this);
  private final CreateShopResolverChain chain = new CreateShopResolverChain(this);
  private final CreateShopResolverOwnership ownership = new CreateShopResolverOwnership(this);
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
  private final CreateShopPendingTopupService pendingTopupService;
  private final CreateShopPendingDeliveryCreationService pendingDeliveryCreationService;
  private final CreateShopReservationReleaseService reservationReleaseService;
  private final CreateShopWarehouseCountService warehouseCountService =
      new CreateShopWarehouseCountService();
  private final CreateShopFlowTimeoutCleanupService flowTimeoutCleanupService =
      new CreateShopFlowTimeoutCleanupService();
  private final CreateShopDeliveryCompletionService deliveryCompletionService =
      new CreateShopDeliveryCompletionService();
  private final CreateShopRetryingReassignService retryingReassignService =
      new CreateShopRetryingReassignService();
  private final CreateShopPendingTokenCollectorService pendingTokenCollectorService =
      new CreateShopPendingTokenCollectorService();
  private final CreateShopPendingRequestGateService pendingRequestGateService =
      new CreateShopPendingRequestGateService();
  private final CreateShopChildReconciliationService childReconciliationService =
      new CreateShopChildReconciliationService();
  private final CreateShopPendingStateDecisionService pendingStateDecisionService =
      new CreateShopPendingStateDecisionService();
  private final CreateShopPostCreationUpdateService postCreationUpdateService =
      new CreateShopPostCreationUpdateService();
  private final CreateShopDeliveryCancelService deliveryCancelService =
      new CreateShopDeliveryCancelService();
  private final CreateShopDeliveryRootCauseSnapshotService deliveryRootCauseSnapshotService =
      new CreateShopDeliveryRootCauseSnapshotService();
  private final CreateShopDeliveryChildRecoveryService deliveryChildRecoveryService =
      new CreateShopDeliveryChildRecoveryService();
  private final CreateShopWorkerAvailabilityGate workerAvailabilityGate =
      new CreateShopWorkerAvailabilityGate();
  private final CreateShopRequestStateMachine flowStateMachine =
      new CreateShopRequestStateMachine();

  public CreateShopRequestResolver(ILocation location, IToken<?> token) {
    super(location, token);
    this.pendingTopupService =
        new CreateShopPendingTopupService(
            cooldown, pendingTracker, diagnostics, flowStateMachine, stockResolver, messaging);
    this.pendingDeliveryCreationService =
        new CreateShopPendingDeliveryCreationService(
            planning, deliveryManager, pendingState, messaging, diagnostics, flowStateMachine);
    this.reservationReleaseService = new CreateShopReservationReleaseService(messaging);
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
    if (request.hasChildren()) {
      flowStateMachine.touch(request.getId(), now, "attemptResolve:has-children");
      if (Config.DEBUG_LOGGING.getAsBoolean()) {
        TheSettlerXCreate.LOGGER.info(
            "[CreateShop] attemptResolve skipped (has active children) request={}",
            request.getId());
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
    if (needed > 0 && pendingTracker.hasDeliveryStarted(request.getId())) {
      cooldown.markRequestOrdered(level, request.getId());
      pendingTracker.setPendingCount(request.getId(), Math.max(1, needed));
      diagnostics.recordPendingSource(request.getId(), "attemptResolve:block-auto-reorder-started");
      flowStateMachine.touch(request.getId(), now, "attemptResolve:block-auto-reorder-started");
      if (Config.DEBUG_LOGGING.getAsBoolean()) {
        TheSettlerXCreate.LOGGER.info(
            "[CreateShop] attemptResolve blocked auto-reorder (started-order) request={} needed={} reserved={}",
            request.getId(),
            needed,
            reservedForRequest);
      }
      return Lists.newArrayList();
    }
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
    boolean hasNetworkPortion = remaining > 0;
    if (!ordered.isEmpty()) {
      transitionFlow(
          manager,
          request,
          CreateShopFlowState.ORDERED_FROM_NETWORK,
          "attemptResolve:order-created",
          describeStack(ordered.get(0)),
          countStackList(ordered),
          "com.thesettler_x_create.message.createshop.flow_ordered");
      // If any portion came from the network, defer child creation to tickPending.
      if (hasNetworkPortion) {
        cooldown.markRequestOrdered(level, request.getId());
        pendingTracker.setPendingCount(request.getId(), Math.max(1, needed));
        diagnostics.recordPendingSource(request.getId(), "attemptResolve:defer-network-arrival");
        flowStateMachine.touch(request.getId(), now, "attemptResolve:defer-network-arrival");
        messaging.sendShopChat(
            manager, "com.thesettler_x_create.message.createshop.request_sent", ordered);
      } else if (rackUsable > 0) {
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
      } else {
        // Otherwise, order from network and wait for arrival.
        cooldown.markRequestOrdered(level, request.getId());
        pendingTracker.setPendingCount(request.getId(), needed);
        diagnostics.recordPendingSource(request.getId(), "attemptResolve:network-ordered");
        messaging.sendShopChat(
            manager, "com.thesettler_x_create.message.createshop.request_sent", ordered);
      }
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

    // If we ordered from the network, we wait for arrival and do not create deliveries here.
    if (hasNetworkPortion || rackUsable <= 0) {
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
    java.util.Set<IToken<?>> pendingTokens =
        pendingTokenCollectorService.collectPendingTokens(
            this, standardManager, level, assignments);
    if (pendingTokens.isEmpty()) {
      return;
    }
    if (Config.DEBUG_LOGGING.getAsBoolean() && shouldLogTickPending(level)) {
      int assignedCount = pendingTokens.size();
      int orderedCount = cooldown.getOrderedCount();
      TheSettlerXCreate.LOGGER.info(
          "[CreateShop] tickPending: assigned={}, ordered={}, total={}",
          assignedCount,
          orderedCount,
          pendingTokens.size());
      logTickPendingCandidates(requestHandler, pendingTokens);
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
        clearPendingTokenStateForOps(token, false);
        continue;
      }
      if (pendingRequestGateService.shouldSkipForPendingProcessing(
          this, manager, standardManager, request, token)) {
        continue;
      }
      if (isTerminalRequestState(request.getState())) {
        clearPendingTokenStateForOps(request.getId(), true);
        diagnostics.logPendingReasonChange(request.getId(), "skip:terminal-state");
        if (Config.DEBUG_LOGGING.getAsBoolean()) {
          TheSettlerXCreate.LOGGER.info(
              "[CreateShop] tickPending: {} skip (terminal state={})",
              request.getId(),
              request.getState());
        }
        continue;
      }
      diagnostics.logRequestStateChange(standardManager, token, "tickPending");
      IDeliverable deliverable = (IDeliverable) request.getRequest();
      String requestIdLog = request.getId().toString();
      UUID requestId = toRequestId(request.getId());
      int reservedForRequest = pickup.getReservedForRequest(requestId);
      boolean onCooldown = cooldown.isRequestOnCooldown(level, request.getId());
      if (request.hasChildren()) {
        diagnostics.logPendingReasonChange(request.getId(), "skip:has-children");
        java.util.Collection<IToken<?>> children =
            java.util.Objects.requireNonNull(request.getChildren(), "children");
        parentLastKnownChildCount.put(request.getId(), children.size());
        parentLastKnownChildren.put(request.getId(), children.toString());
        var childResult =
            childReconciliationService.reconcile(
                this,
                standardManager,
                level,
                request,
                requestHandler,
                assignmentStore::getAssignmentForValue,
                shop,
                pickup,
                requestIdLog);
        if (Config.DEBUG_LOGGING.getAsBoolean()) {
          TheSettlerXCreate.LOGGER.info(
              "[CreateShop] tickPending: {} skip (has children)", requestIdLog);
          diagnostics.logParentChildrenState(standardManager, request.getId(), "tickPending");
          if (childResult.childrenEmpty()) {
            TheSettlerXCreate.LOGGER.info(
                "[CreateShop] tickPending: {} children list empty despite hasChildren",
                requestIdLog);
          } else if (childResult.missing() > 0) {
            TheSettlerXCreate.LOGGER.info(
                "[CreateShop] tickPending: {} missing children={} total={}",
                requestIdLog,
                childResult.missing(),
                childResult.childrenCount());
          } else if (childResult.duplicateChildrenRemoved() > 0) {
            TheSettlerXCreate.LOGGER.info(
                "[CreateShop] tickPending: {} duplicate children removed={} total={}",
                requestIdLog,
                childResult.duplicateChildrenRemoved(),
                childResult.childrenCount());
          }
        }
        if (childResult.missing() > 0) {
          continue;
        }
        if (!childResult.hasActiveChildren()) {
          parentDeliveryActiveSince.remove(request.getId());
          clearStaleRecoveryArm(request.getId());
        }
        if (childResult.hasActiveChildren() || request.hasChildren()) {
          continue;
        }
      }
      Integer previousChildCount = parentLastKnownChildCount.get(request.getId());
      if (previousChildCount != null && previousChildCount > 0 && !request.hasChildren()) {
        long now = level.getGameTime();
        Long lastDropLog = parentChildDropLastLogTick.get(request.getId());
        if (lastDropLog == null || now - lastDropLog >= 100L) {
          parentChildDropLastLogTick.put(request.getId(), now);
          if (Config.DEBUG_LOGGING.getAsBoolean()) {
            String previousChildren = parentLastKnownChildren.getOrDefault(request.getId(), "[]");
            TheSettlerXCreate.LOGGER.info(
                "[CreateShop] root-cause parent-child-drop parent={} state={} prevChildCount={} prevChildren={} reservedForRequest={} pending={} cooldown={}",
                request.getId(),
                request.getState(),
                previousChildCount,
                previousChildren,
                reservedForRequest,
                pendingTracker.getPendingCount(request.getId()),
                cooldown.isRequestOnCooldown(level, request.getId()));
          }
        }
      }
      parentLastKnownChildCount.put(request.getId(), 0);
      parentLastKnownChildren.put(request.getId(), "[]");
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
      var pendingDecision =
          pendingStateDecisionService.decide(
              this,
              request,
              level,
              deliverable,
              onCooldown,
              reservedForRequest,
              workerWorking,
              requestIdLog);
      if (pendingDecision.shouldSkip()) {
        continue;
      }
      int pendingCount = pendingDecision.pendingCount();
      int rackAvailable = planning.getAvailableFromRacks(tile, deliverable);
      int reservedForDeliverable = pickup.getReservedForDeliverable(deliverable);
      int rackAvailableForRequest =
          Math.max(
              0,
              rackAvailable
                  - Math.max(0, reservedForDeliverable - Math.max(0, reservedForRequest)));
      int reservedSynced =
          syncReservationsFromRack(
              tile,
              pickup,
              requestId,
              request.getId(),
              deliverable,
              pendingCount,
              reservedForRequest,
              rackAvailableForRequest,
              level.getGameTime());
      if (reservedSynced > 0) {
        reservedForRequest += reservedSynced;
        reservedForDeliverable += reservedSynced;
        rackAvailableForRequest =
            Math.max(
                0,
                rackAvailable
                    - Math.max(0, reservedForDeliverable - Math.max(0, reservedForRequest)));
      }
      pendingTopupService.handleTopup(
          manager,
          request,
          level,
          tile,
          pickup,
          deliverable,
          workerWorking,
          pendingCount,
          reservedForRequest,
          rackAvailableForRequest,
          requestIdLog);
      var creationResult =
          pendingDeliveryCreationService.process(
              manager,
              request,
              level,
              tile,
              pickup,
              deliverable,
              pendingCount,
              rackAvailableForRequest,
              requestIdLog);
      if (!creationResult.created()) {
        continue;
      }
      postCreationUpdateService.apply(this, manager, request, level, creationResult, requestIdLog);
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

  private boolean shouldDropMissingChild(Level level, IToken<?> childToken) {
    if (level == null || childToken == null) {
      return false;
    }
    long now = level.getGameTime();
    Long since = missingChildSince.putIfAbsent(childToken, now);
    if (since == null) {
      return false;
    }
    return now - since >= 40L;
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

  public static void onDeliveryCancelled(IRequestManager manager, IRequest<?> request) {
    CreateShopRequestResolver resolver =
        CreateShopDeliveryResolverLocator.findResolverForDelivery(manager, request);
    if (resolver == null) {
      resolver = CreateShopDeliveryResolverLocator.findResolverByDeliveryToken(manager, request);
    }
    if (resolver != null) {
      resolver.handleDeliveryCancelled(manager, request);
      return;
    }
    CreateShopDeliveryResolverLocator.logUnresolvedDeliveryCallback("cancelled", manager, request);
  }

  private void logTickPendingCandidates(
      com.minecolonies.api.colony.requestsystem.management.IRequestHandler requestHandler,
      java.util.Set<IToken<?>> pendingTokens) {
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

  public static void onDeliveryComplete(IRequestManager manager, IRequest<?> request) {
    CreateShopRequestResolver resolver =
        CreateShopDeliveryResolverLocator.findResolverForDelivery(manager, request);
    if (resolver == null) {
      resolver = CreateShopDeliveryResolverLocator.findResolverByDeliveryToken(manager, request);
    }
    if (resolver != null) {
      resolver.handleDeliveryComplete(manager, request);
      return;
    }
    CreateShopDeliveryResolverLocator.logUnresolvedDeliveryCallback("complete", manager, request);
  }

  private void handleDeliveryCancelled(IRequestManager manager, IRequest<?> request) {
    deliveryCancelService.handleDeliveryCancelled(this, manager, request);
  }

  private void handleDeliveryComplete(IRequestManager manager, IRequest<?> request) {
    deliveryCompletionService.handleDeliveryComplete(this, manager, request);
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
    cleanupTerminalRequest(manager, request, true);
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
    cleanupTerminalRequest(manager, request, true);
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
    cleanupTerminalRequest(manager, request, request.getRequest() instanceof IDeliverable);
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
    cleanupTerminalRequest(manager, request, request.getRequest() instanceof IDeliverable);
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
    return warehouseCountService.getWarehouseInternalCount(getLocation(), request, stockResolver);
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
    reservationReleaseService.releaseReservation(manager, request, getLocation());
  }

  private void cleanupTerminalRequest(
      IRequestManager manager, IRequest<?> request, boolean releaseReservation) {
    if (request == null) {
      return;
    }
    pendingTracker.remove(request.getId());
    cooldown.clearRequestCooldown(request.getId());
    clearDeliveriesCreated(request.getId());
    clearTrackedChildrenForParent(unwrapStandardManager(manager), request.getId());
    if (releaseReservation) {
      releaseReservation(manager, request);
    }
  }

  static UUID toRequestId(IToken<?> token) {
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

  private static boolean isTerminalRequestState(RequestState state) {
    if (state == null) {
      return false;
    }
    return state == RequestState.CANCELLED
        || state == RequestState.COMPLETED
        || state == RequestState.FAILED
        || state == RequestState.RECEIVED
        || state == RequestState.RESOLVED;
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
    return flowStateMachine.hasNonTerminalWork();
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
    flowTimeoutCleanupService.processTimedOutFlows(this, manager, level);
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
    return deliveryChildRecoveryService.recover(
        this,
        manager,
        level,
        parentRequest,
        childToken,
        childRequest,
        shop,
        pickup,
        pendingSource,
        logTemplate);
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
    if (!level.dimension().equals(start.getDimension())) {
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

  private boolean isDeliveryFromLocalShopStart(
      Delivery delivery, BuildingCreateShop shop, CreateShopBlockEntity pickup) {
    if (delivery == null || pickup == null || pickup.getLevel() == null) {
      return false;
    }
    ILocation start = delivery.getStart();
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
    if (isDeliveryFromPickup(delivery, pickup)) {
      return true;
    }
    return shop != null && shop.hasContainerPosition(startPos);
  }

  private void clearTrackedChildrenForParent(
      IStandardRequestManager manager, IToken<?> parentToken) {
    if (manager == null || parentToken == null) {
      return;
    }
    parentDeliveryActiveSince.remove(parentToken);
    clearStaleRecoveryArm(parentToken);
    parentLastKnownChildCount.remove(parentToken);
    parentLastKnownChildren.remove(parentToken);
    parentChildDropLastLogTick.remove(parentToken);
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
    retryingReassignService.reassignResolvableRetryingRequests(this, manager, level);
  }

  private String describeStack(ItemStack stack) {
    if (stack == null || stack.isEmpty()) {
      return "";
    }
    return stack.getHoverName().getString();
  }

  private void logDeliveryRootCauseSnapshot(
      IStandardRequestManager manager,
      Level level,
      IRequest<?> parent,
      IRequest<?> child,
      IToken<?> childToken,
      IToken<?> assignedResolverToken) {
    deliveryRootCauseSnapshotService.logSnapshot(
        this, manager, level, parent, child, childToken, assignedResolverToken);
  }

  CreateShopRequestStateMachine getFlowStateMachineForOps() {
    return flowStateMachine;
  }

  long getInflightTimeoutTicksSafeForOps() {
    return getInflightTimeoutTicksSafe();
  }

  java.util.Map<IToken<?>, Long> getParentDeliveryActiveSinceForOps() {
    return parentDeliveryActiveSince;
  }

  java.util.Map<IToken<?>, String> getDeliveryRootCauseSnapshotsForOps() {
    return deliveryRootCauseSnapshots;
  }

  java.util.Map<IToken<?>, Long> getDeliveryRootCauseLastLogTickForOps() {
    return deliveryRootCauseLastLogTick;
  }

  void clearStaleRecoveryArmForOps(IToken<?> parentToken) {
    clearStaleRecoveryArm(parentToken);
  }

  void clearTrackedChildrenForParentForOps(
      IStandardRequestManager manager, IToken<?> parentToken) {
    clearTrackedChildrenForParent(manager, parentToken);
  }

  void transitionFlowForOps(
      IRequestManager manager,
      IRequest<?> request,
      CreateShopFlowState state,
      String detail,
      String stackLabel,
      int amount,
      String messageKey) {
    transitionFlow(manager, request, state, detail, stackLabel, amount, messageKey);
  }

  java.util.Map<IToken<?>, Long> getDeliveryChildActiveSinceForOps() {
    return deliveryChildActiveSince;
  }

  java.util.Map<IToken<?>, Long> getMissingChildSinceForOps() {
    return missingChildSince;
  }

  CreateShopDeliveryManager getDeliveryManagerForOps() {
    return deliveryManager;
  }

  CreateShopResolverDiagnostics getDiagnosticsForOps() {
    return diagnostics;
  }

  CreateShopResolverRecheck getRecheckForOps() {
    return recheck;
  }

  boolean isDebugLoggingEnabledForOps() {
    return isDebugLoggingEnabled();
  }

  boolean isDeliveryFromLocalShopStartForOps(
      Delivery delivery, BuildingCreateShop shop, CreateShopBlockEntity pickup) {
    return isDeliveryFromLocalShopStart(delivery, shop, pickup);
  }

  boolean isLocalShopDeliveryChildForOps(
      IRequest<?> childRequest, BuildingCreateShop shop, CreateShopBlockEntity pickup) {
    return isLocalShopDeliveryChild(childRequest, shop, pickup);
  }

  String describeStackForOps(ItemStack stack) {
    return describeStack(stack);
  }

  int countStackListForOps(List<ItemStack> stacks) {
    return countStackList(stacks);
  }

  java.util.Map<IToken<?>, Long> getRetryingReassignAttemptsForOps() {
    return retryingReassignAttempts;
  }

  CreateShopResolverOwnership getOwnershipForOps() {
    return ownership;
  }

  boolean shouldLogTickPendingForOps(Level level) {
    return shouldLogTickPending(level);
  }

  void clearPendingTokenStateForOps(IToken<?> token, boolean clearFlowState) {
    if (token == null) {
      return;
    }
    cooldown.clearRequestCooldown(token);
    pendingTracker.remove(token);
    clearDeliveriesCreated(token);
    if (clearFlowState) {
      flowStateMachine.remove(token);
    }
  }

  java.util.Set<IToken<?>> getCancelledRequestsForOps() {
    return cancelledRequests;
  }

  int computeOutstandingNeededForOps(
      IRequest<?> request, IDeliverable deliverable, int reservedForRequest) {
    return computeOutstandingNeeded(request, deliverable, reservedForRequest);
  }

  CreateShopWorkerAvailabilityGate getWorkerAvailabilityGateForOps() {
    return workerAvailabilityGate;
  }

  CreateShopResolverMessaging getMessagingForOps() {
    return messaging;
  }

  void touchFlowForOps(IToken<?> requestToken, long nowTick, String detail) {
    flowStateMachine.touch(requestToken, nowTick, detail);
  }

  boolean shouldDropMissingChildForOps(Level level, IToken<?> childToken) {
    return shouldDropMissingChild(level, childToken);
  }

  boolean recoverExtraActiveDeliveryChildForOps(
      IStandardRequestManager manager,
      Level level,
      IRequest<?> parentRequest,
      IToken<?> childToken,
      IRequest<?> childRequest,
      BuildingCreateShop shop,
      CreateShopBlockEntity pickup) {
    return recoverExtraActiveDeliveryChild(
        manager, level, parentRequest, childToken, childRequest, shop, pickup);
  }

  boolean recoverStaleDeliveryChildForOps(
      IStandardRequestManager manager,
      Level level,
      IRequest<?> parentRequest,
      IToken<?> childToken,
      IRequest<?> childRequest,
      BuildingCreateShop shop,
      CreateShopBlockEntity pickup) {
    return recoverStaleDeliveryChild(
        manager, level, parentRequest, childToken, childRequest, shop, pickup);
  }

  boolean isStaleDeliveryChildForOps(
      Level level, IToken<?> parentToken, IToken<?> childToken, RequestState state) {
    return isStaleDeliveryChild(level, parentToken, childToken, state);
  }

  boolean isStaleRecoveryArmedForOps(
      Level level, IStandardRequestManager manager, IToken<?> parentToken) {
    return isStaleRecoveryArmed(level, manager, parentToken);
  }

  void logDeliveryRootCauseSnapshotForOps(
      IStandardRequestManager manager,
      Level level,
      IRequest<?> parent,
      IRequest<?> child,
      IToken<?> childToken,
      IToken<?> assignedResolverToken) {
    logDeliveryRootCauseSnapshot(manager, level, parent, child, childToken, assignedResolverToken);
  }
}
