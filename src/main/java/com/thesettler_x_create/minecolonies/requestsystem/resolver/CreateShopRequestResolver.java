package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.requestsystem.location.ILocation;
import com.minecolonies.api.colony.requestsystem.manager.IRequestManager;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.request.RequestState;
import com.minecolonies.api.colony.requestsystem.requestable.IDeliverable;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.api.util.BlockPosUtil;
import com.minecolonies.core.colony.requestsystem.management.IStandardRequestManager;
import com.minecolonies.core.colony.requestsystem.resolvers.core.AbstractWarehouseRequestResolver;
import com.thesettler_x_create.Config;
import com.thesettler_x_create.TheSettlerXCreate;
import com.thesettler_x_create.minecolonies.building.BuildingCreateShop;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

/**
 * Request resolver that fulfills deliverable requests from Create Shop stock and warehouse racks.
 */
public class CreateShopRequestResolver extends AbstractWarehouseRequestResolver {
  // Keep below warehouse resolvers so MineColonies prefers warehouse stock before Create Shop.
  private static final int PRIORITY = 140;
  private static final int MAX_CHAIN_SANITIZE_NODES = 512;
  private static final long DELIVERY_CHILD_STALE_TIMEOUT_FLOOR_TICKS = 20L * 30L;
  private static final CreateShopDeliveryCallbackService deliveryCallbackService =
      new CreateShopDeliveryCallbackService();

  private final java.util.Set<IToken<?>> cancelledRequests =
      java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());
  private final CreateShopRuntimeStateStore runtimeStateStore = new CreateShopRuntimeStateStore();
  private final java.util.Set<String> deliveryLinkLogged =
      java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());
  private final java.util.Set<String> deliveryCreateLogged =
      java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());
  private final java.util.Set<String> chainCycleLogged =
      java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());
  private final CreateShopResolverPlanning planning = new CreateShopResolverPlanning();
  private final CreateShopResolverDiagnostics diagnostics = new CreateShopResolverDiagnostics(this);
  private final CreateShopResolverRecheck recheck =
      new CreateShopResolverRecheck(this, diagnostics);
  private final CreateShopResolverCooldown cooldown = new CreateShopResolverCooldown(this);
  private final CreateShopResolverMessaging messaging = new CreateShopResolverMessaging();
  private final CreateShopRequestValidator validator;
  private final CreateShopStockResolver stockResolver = new CreateShopStockResolver();
  private final CreateShopReservationReleaseService reservationReleaseService;
  private final CreateShopWarehouseCountService warehouseCountService =
      new CreateShopWarehouseCountService();
  private final CreateShopDeliveryCompletionService deliveryCompletionService;
  private final CreateShopRetryingReassignService retryingReassignService =
      new CreateShopRetryingReassignService();
  private final CreateShopDeliveryCancelService deliveryCancelService;
  // Kept as fields (not local-ized like their siblings below) - two runtime tests reach these via
  // reflection (CreateShopRequestResolverLifecycleRuntimeTest,
  // CreateShopRequestResolverTimeoutCleanupRuntimeTest), which only works against instance fields.
  private final CreateShopDeliveryChildRecoveryService deliveryChildRecoveryService;
  private final CreateShopFlowTimeoutCleanupService flowTimeoutCleanupService;
  private final CreateShopDeliveryChildLedgerService deliveryChildLedgerService =
      new CreateShopDeliveryChildLedgerService();
  private final CreateShopAttemptResolveService attemptResolveService;
  private final CreateShopTickPendingService tickPendingService;
  private final CreateShopDeliveryChildGuardService deliveryChildGuardService;
  private final CreateShopResolverCallbackService resolverCallbackService;
  private final CreateShopRequestStateMachine flowStateMachine =
      new CreateShopRequestStateMachine();

  public CreateShopRequestResolver(ILocation location, IToken<?> token) {
    super(location, token);
    // Everything declared below is wired into a field above or into another local collaborator
    // right here in the constructor and never referenced anywhere else in this class - it doesn't
    // need to be a field itself. Clean Code Audit finding: this alone cuts the resolver's field
    // count from 41 to ~23, with zero behavior change (same objects, same wiring order).
    CreateShopRequestStateMutatorService requestStateMutatorService =
        new CreateShopRequestStateMutatorService();
    CreateShopDeliveryManager deliveryManager = new CreateShopDeliveryManager(this);
    CreateShopResolverChain chain = new CreateShopResolverChain(this);
    CreateShopResolverOwnership ownership = new CreateShopResolverOwnership(this);
    CreateShopResolverPendingState pendingState = new CreateShopResolverPendingState();
    CreateShopOutstandingNeededService outstandingNeededService =
        new CreateShopOutstandingNeededService();
    CreateShopTickPendingTelemetryService tickPendingTelemetryService =
        new CreateShopTickPendingTelemetryService();
    CreateShopWorkerAvailabilityGate workerAvailabilityGate =
        new CreateShopWorkerAvailabilityGate();
    CreateShopPendingTokenCollectorService pendingTokenCollectorService =
        new CreateShopPendingTokenCollectorService(ownership, tickPendingTelemetryService);
    CreateShopDeliveryRootCauseSnapshotService deliveryRootCauseSnapshotService =
        new CreateShopDeliveryRootCauseSnapshotService();

    this.deliveryChildGuardService =
        new CreateShopDeliveryChildGuardService(requestStateMutatorService);
    this.flowTimeoutCleanupService =
        new CreateShopFlowTimeoutCleanupService(requestStateMutatorService);
    this.deliveryCompletionService =
        new CreateShopDeliveryCompletionService(
            requestStateMutatorService, deliveryManager, diagnostics, recheck);
    CreateShopPendingStateDecisionService pendingStateDecisionService =
        new CreateShopPendingStateDecisionService(
            requestStateMutatorService,
            workerAvailabilityGate,
            outstandingNeededService,
            diagnostics);
    CreateShopPostCreationUpdateService postCreationUpdateService =
        new CreateShopPostCreationUpdateService(requestStateMutatorService, messaging, diagnostics);
    this.deliveryCancelService =
        new CreateShopDeliveryCancelService(
            requestStateMutatorService, diagnostics, recheck, deliveryManager);
    this.deliveryChildRecoveryService =
        new CreateShopDeliveryChildRecoveryService(
            requestStateMutatorService, ownership, diagnostics);
    CreateShopPendingRequestGateService pendingRequestGateService =
        new CreateShopPendingRequestGateService(ownership, diagnostics, requestStateMutatorService);
    CreateShopReservationSyncService reservationSyncService =
        new CreateShopReservationSyncService(requestStateMutatorService, diagnostics);
    this.validator = new CreateShopRequestValidator(chain, stockResolver, planning, cooldown);
    CreateShopFlowStateRehydrateService flowStateRehydrateService =
        new CreateShopFlowStateRehydrateService(
            requestStateMutatorService, outstandingNeededService, diagnostics);
    this.attemptResolveService =
        new CreateShopAttemptResolveService(
            requestStateMutatorService,
            messaging,
            deliveryManager,
            outstandingNeededService,
            cooldown,
            chain,
            planning,
            stockResolver,
            diagnostics,
            flowStateMachine);
    this.resolverCallbackService =
        new CreateShopResolverCallbackService(requestStateMutatorService, cooldown, diagnostics);
    CreateShopPendingTopupService pendingTopupService =
        new CreateShopPendingTopupService(
            runtimeStateStore.getPendingTracker(),
            diagnostics,
            flowStateMachine,
            stockResolver,
            messaging,
            requestStateMutatorService);
    CreateShopPendingDeliveryCreationService pendingDeliveryCreationService =
        new CreateShopPendingDeliveryCreationService(
            planning, deliveryManager, pendingState, messaging, diagnostics, flowStateMachine);
    this.reservationReleaseService = new CreateShopReservationReleaseService(messaging);
    CreateShopChildReconciliationService childReconciliationService =
        new CreateShopChildReconciliationService(
            deliveryManager,
            deliveryChildRecoveryService,
            deliveryRootCauseSnapshotService,
            requestStateMutatorService);
    CreateShopPendingRequestProcessorService pendingRequestProcessorService =
        new CreateShopPendingRequestProcessorService(
            pendingRequestGateService,
            childReconciliationService,
            pendingStateDecisionService,
            reservationSyncService,
            pendingTopupService,
            pendingDeliveryCreationService,
            postCreationUpdateService,
            diagnostics,
            requestStateMutatorService);
    this.tickPendingService =
        new CreateShopTickPendingService(
            pendingTokenCollectorService,
            pendingRequestProcessorService,
            flowTimeoutCleanupService,
            tickPendingTelemetryService,
            flowStateRehydrateService);
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
    return attemptResolveService.attemptResolve(this, manager, request);
  }

  @Override
  public void resolveRequest(
      @NotNull IRequestManager manager, @NotNull IRequest<? extends IDeliverable> request) {
    resolverCallbackService.resolveRequest(this, manager, request);
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
    tickPendingService.tickPendingDeliveries(this, manager);
  }

  public void sweepFastOrphanRecoveries(IRequestManager manager) {
    IStandardRequestManager standardManager = unwrapStandardManager(manager);
    if (standardManager == null) {
      return;
    }
    resolverCallbackService.sweepFastOrphanPickedUpRecoveries(this, manager, standardManager);
  }

  public static void onDeliveryCancelled(IRequestManager manager, IRequest<?> request) {
    deliveryCallbackService.onDeliveryCancelled(manager, request);
  }

  public static void onDeliveryComplete(IRequestManager manager, IRequest<?> request) {
    deliveryCallbackService.onDeliveryComplete(manager, request);
  }

  void handleDeliveryCancelled(IRequestManager manager, IRequest<?> request) {
    deliveryCancelService.handleDeliveryCancelled(this, manager, request);
  }

  void handleDeliveryComplete(IRequestManager manager, IRequest<?> request) {
    deliveryCompletionService.handleDeliveryComplete(this, manager, request);
  }

  @Override
  public void onAssignedRequestBeingCancelled(
      @NotNull IRequestManager manager, @NotNull IRequest<? extends IDeliverable> request) {
    resolverCallbackService.onAssignedRequestBeingCancelled(this, manager, request);
  }

  @Override
  public void onAssignedRequestCancelled(
      @NotNull IRequestManager manager, @NotNull IRequest<? extends IDeliverable> request) {
    resolverCallbackService.onAssignedRequestCancelled(this, manager, request);
  }

  @Override
  public void onRequestedRequestComplete(
      @NotNull IRequestManager manager, @NotNull IRequest<?> request) {
    resolverCallbackService.onRequestedRequestComplete(this, manager, request);
  }

  @Override
  public void onRequestedRequestCancelled(
      @NotNull IRequestManager manager, @NotNull IRequest<?> request) {
    resolverCallbackService.onRequestedRequestCancelled(this, manager, request);
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

  BuildingCreateShop getShop(IRequestManager manager) {
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

  static long getDeliveryChildStaleTimeoutFloorTicks() {
    return DELIVERY_CHILD_STALE_TIMEOUT_FLOOR_TICKS;
  }

  void logDeliveryLinkState(
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
    return runtimeStateStore.getPendingTracker().isDeliveryCreated(token);
  }

  void markDeliveriesCreated(IToken<?> token) {
    runtimeStateStore.getPendingTracker().markDeliveryCreated(token);
  }

  void clearDeliveriesCreated(IToken<?> token) {
    runtimeStateStore.getPendingTracker().clearDeliveryCreated(token);
  }

  String tryDescribeResolver(Object resolver) {
    return resolver == null ? "<none>" : resolver.getClass().getSimpleName();
  }

  private static boolean isDebugLoggingEnabledSafe() {
    try {
      return Config.DEBUG_LOGGING.getAsBoolean();
    } catch (IllegalStateException ignored) {
      return false;
    }
  }

  static boolean isTerminalRequestState(RequestState state) {
    return RequestStateUtil.isTerminalRequestState(state);
  }

  int getMaxChainSanitizeNodes() {
    return MAX_CHAIN_SANITIZE_NODES;
  }

  boolean markChainCycleLogged(String key) {
    return chainCycleLogged.add(key);
  }

  void markCancelledRequest(IToken<?> token) {
    if (token != null) {
      cancelledRequests.add(token);
    }
  }

  boolean clearCancelledRequest(IToken<?> token) {
    return token != null && cancelledRequests.remove(token);
  }

  boolean isCancelledRequest(IToken<?> token) {
    return token != null && cancelledRequests.contains(token);
  }

  CreateShopResolverPlanning getPlanning() {
    return planning;
  }

  CreateShopResolverCooldown getCooldown() {
    return cooldown;
  }

  CreateShopPendingDeliveryTracker getPendingTracker() {
    return runtimeStateStore.getPendingTracker();
  }

  boolean markDeliveryCreateLogged(String key) {
    return deliveryCreateLogged.add(key);
  }

  IToken<?> getResolverToken() {
    return getId();
  }

  public boolean hasActiveWork() {
    if (runtimeStateStore.getPendingTracker().hasEntries()) {
      return true;
    }
    if (cooldown.getOrderedCount() > 0) {
      return true;
    }
    return flowStateMachine.hasNonTerminalWork();
  }

  public boolean hasProtectedInventoryWindow() {
    return hasActiveWork() || runtimeStateStore.getPendingTracker().hasEntries();
  }

  long resolveNowTick(IRequestManager manager) {
    if (manager == null || manager.getColony() == null || manager.getColony().getWorld() == null) {
      return 0L;
    }
    return manager.getColony().getWorld().getGameTime();
  }

  void transitionFlow(
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

  long getInflightTimeoutTicksSafe() {
    try {
      return Math.max(
          DELIVERY_CHILD_STALE_TIMEOUT_FLOOR_TICKS, Config.INFLIGHT_TIMEOUT_TICKS.getAsLong());
    } catch (IllegalStateException ignored) {
      return DELIVERY_CHILD_STALE_TIMEOUT_FLOOR_TICKS;
    }
  }

  void reassignResolvableRetryingRequests(IStandardRequestManager manager, Level level) {
    retryingReassignService.reassignResolvableRetryingRequests(this, manager, level);
  }

  CreateShopRequestStateMachine getFlowStateMachine() {
    return flowStateMachine;
  }

  /**
   * Saves persisted FlowStates to the given NBT tag. Called from BuildingCreateShop.serializeNBT.
   */
  public void saveFlowStatesToNbt(net.minecraft.nbt.CompoundTag tag) {
    flowStateMachine.saveFlowStates(tag);
  }

  /**
   * Loads FlowStates from NBT for lazy restore on the next tick. Called from
   * BuildingCreateShop.deserializeNBT.
   */
  public void loadFlowStatesFromNbt(net.minecraft.nbt.CompoundTag tag) {
    flowStateMachine.loadFlowStates(tag);
  }

  CreateShopResolverCallbackService getResolverCallbackService() {
    return resolverCallbackService;
  }

  void markParentChildCompletedSeen(IToken<?> parentToken, long tick) {
    if (parentToken == null) {
      return;
    }
    runtimeStateStore.getParentChildCompletedSeenAt().put(parentToken, tick);
  }

  public boolean hasParentChildCompletedSeen(IToken<?> parentToken) {
    return parentToken != null
        && runtimeStateStore.getParentChildCompletedSeenAt().containsKey(parentToken);
  }

  void clearParentChildCompletedSeen(IToken<?> parentToken) {
    if (parentToken == null) {
      return;
    }
    runtimeStateStore.getParentChildCompletedSeenAt().remove(parentToken);
  }

  void clearMissingChildSince(IToken<?> childToken) {
    runtimeStateStore.getMissingChildSince().remove(childToken);
  }

  Long markMissingChildIfAbsent(IToken<?> childToken, long nowTick) {
    return runtimeStateStore.getMissingChildSince().putIfAbsent(childToken, nowTick);
  }

  Long getRootCauseLastLogTick(IToken<?> childToken) {
    return runtimeStateStore.getDeliveryRootCauseLastLogTick().get(childToken);
  }

  void markRootCauseLastLogTick(IToken<?> childToken, long nowTick) {
    runtimeStateStore.getDeliveryRootCauseLastLogTick().put(childToken, nowTick);
  }

  String putRootCauseSnapshot(IToken<?> childToken, String snapshot) {
    return runtimeStateStore.getDeliveryRootCauseSnapshots().put(childToken, snapshot);
  }

  Integer getParentLastKnownChildCount(IToken<?> parentToken) {
    return runtimeStateStore.getParentLastKnownChildCount().get(parentToken);
  }

  String getParentLastKnownChildren(IToken<?> parentToken) {
    return runtimeStateStore.getParentLastKnownChildren().get(parentToken);
  }

  Long getParentChildDropLastLogTick(IToken<?> parentToken) {
    return runtimeStateStore.getParentChildDropLastLogTick().get(parentToken);
  }

  void markParentChildDropLastLogTick(IToken<?> parentToken, long nowTick) {
    runtimeStateStore.getParentChildDropLastLogTick().put(parentToken, nowTick);
  }

  void setParentChildrenSnapshot(IToken<?> parentToken, int childCount, String childrenState) {
    runtimeStateStore.getParentLastKnownChildCount().put(parentToken, Math.max(0, childCount));
    runtimeStateStore
        .getParentLastKnownChildren()
        .put(parentToken, childrenState == null ? "[]" : childrenState);
  }

  void clearParentChildrenSnapshot(IToken<?> parentToken) {
    runtimeStateStore.getParentLastKnownChildCount().remove(parentToken);
    runtimeStateStore.getParentLastKnownChildren().remove(parentToken);
    runtimeStateStore.getParentChildDropLastLogTick().remove(parentToken);
  }

  void clearTrackedChildrenForParent(IStandardRequestManager manager, IToken<?> parentToken) {
    deliveryChildGuardService.clearTrackedChildrenForParent(this, manager, parentToken);
  }

  CreateShopResolverRecheck getRecheck() {
    return recheck;
  }

  void scheduleParentChildRecheckAtForTest(IToken<?> parentToken, long dueTick) {
    recheck.scheduleParentChildRecheckAtForTest(parentToken, dueTick);
  }

  Long getParentChildRecheckDueTick(IToken<?> parentToken) {
    return recheck.getParentChildRecheckDueTick(parentToken);
  }

  boolean isDebugLoggingEnabled() {
    return isDebugLoggingEnabledSafe();
  }

  Long getRetryingReassignAttempt(IToken<?> token) {
    return runtimeStateStore.getRetryingReassignAttempts().get(token);
  }

  void markRetryingReassignAttempt(IToken<?> token, long nowTick) {
    runtimeStateStore.getRetryingReassignAttempts().put(token, nowTick);
  }

  void clearRetryingReassignAttempt(IToken<?> token) {
    runtimeStateStore.getRetryingReassignAttempts().remove(token);
  }

  void clearRootCauseTracking(IToken<?> childToken) {
    runtimeStateStore.getDeliveryRootCauseSnapshots().remove(childToken);
    runtimeStateStore.getDeliveryRootCauseLastLogTick().remove(childToken);
    runtimeStateStore.getDeliveryChildLedger().remove(childToken);
    runtimeStateStore.getDeliveryChildLedgerLastLogTick().remove(childToken);
  }

  void clearDeliveryChildLedgerForParent(IToken<?> parentToken) {
    if (parentToken == null) {
      return;
    }
    for (var entry : java.util.List.copyOf(runtimeStateStore.getDeliveryChildLedger().entrySet())) {
      CreateShopDeliveryChildLedgerEntry ledger = entry.getValue();
      if (ledger == null || !parentToken.equals(ledger.parentToken)) {
        continue;
      }
      IToken<?> childToken = entry.getKey();
      runtimeStateStore.getDeliveryChildLedger().remove(childToken);
      runtimeStateStore.getDeliveryChildLedgerLastLogTick().remove(childToken);
      runtimeStateStore.getDeliveryRootCauseSnapshots().remove(childToken);
      runtimeStateStore.getDeliveryRootCauseLastLogTick().remove(childToken);
    }
  }

  void touchFlow(IToken<?> requestToken, long nowTick, String detail) {
    flowStateMachine.touch(requestToken, nowTick, detail);
  }

  void resolveViaWarehouse(IRequestManager manager, IRequest<? extends IDeliverable> request) {
    super.resolveRequest(manager, request);
  }

  Map<IToken<?>, CreateShopDeliveryChildLedgerEntry> getDeliveryChildLedger() {
    return runtimeStateStore.getDeliveryChildLedger();
  }

  Long getDeliveryLedgerLastLogTick(IToken<?> childToken) {
    return runtimeStateStore.getDeliveryChildLedgerLastLogTick().get(childToken);
  }

  void markDeliveryLedgerLastLogTick(IToken<?> childToken, long nowTick) {
    runtimeStateStore.getDeliveryChildLedgerLastLogTick().put(childToken, nowTick);
  }

  void observeDeliveryChildLifecycle(
      IStandardRequestManager manager,
      Level level,
      IToken<?> parentToken,
      IToken<?> childToken,
      IRequest<?> child,
      IToken<?> assignedResolverToken,
      String source) {
    deliveryChildLedgerService.observeChild(
        this, manager, level, parentToken, childToken, child, assignedResolverToken, source);
  }

  void observeDeliveryChildMissing(
      Level level, IToken<?> parentToken, IToken<?> childToken, String source, String detail) {
    deliveryChildLedgerService.observeMissingChild(
        this, level, parentToken, childToken, source, detail);
  }

  void observeDeliveryChildCallbackTerminal(
      Level level, IToken<?> parentToken, IToken<?> childToken, String callbackType) {
    deliveryChildLedgerService.observeCallbackTerminal(
        this, level, parentToken, childToken, callbackType);
  }

  CreateShopDeliveryChildLedgerEntry getDeliveryChildLedgerEntry(IToken<?> childToken) {
    if (childToken == null) {
      return null;
    }
    return runtimeStateStore.getDeliveryChildLedger().get(childToken);
  }

  IToken<?> findPickedUpOrphanChildForParent(IToken<?> parentToken) {
    if (parentToken == null) {
      return null;
    }
    for (var entry : runtimeStateStore.getDeliveryChildLedger().entrySet()) {
      CreateShopDeliveryChildLedgerEntry ledger = entry.getValue();
      if (ledger == null) {
        continue;
      }
      if (!parentToken.equals(ledger.parentToken)) {
        continue;
      }
      if (ledger.pickupConfirmedAtTick < 0L) {
        continue;
      }
      if (ledger.terminalSeenAtTick >= 0L) {
        continue;
      }
      return entry.getKey();
    }
    return null;
  }
}
