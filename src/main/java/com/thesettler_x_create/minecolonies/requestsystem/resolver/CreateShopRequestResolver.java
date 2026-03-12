package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import com.google.common.collect.Lists;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.requestsystem.location.ILocation;
import com.minecolonies.api.colony.requestsystem.manager.IRequestManager;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.request.RequestState;
import com.minecolonies.api.colony.requestsystem.requestable.IDeliverable;
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
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
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
  private static final CreateShopDeliveryCallbackService deliveryCallbackService =
      new CreateShopDeliveryCallbackService();

  private final java.util.Set<IToken<?>> cancelledRequests =
      java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());
  private final java.util.Map<IToken<?>, Long> pendingNotices =
      new java.util.concurrent.ConcurrentHashMap<>();
  private final java.util.Map<IToken<?>, Long> retryingReassignAttempts =
      new java.util.concurrent.ConcurrentHashMap<>();
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
  private final CreateShopOutstandingNeededService outstandingNeededService =
      new CreateShopOutstandingNeededService();
  private final CreateShopStockResolver stockResolver = new CreateShopStockResolver();
  private final CreateShopTickPendingTelemetryService tickPendingTelemetryService =
      new CreateShopTickPendingTelemetryService();
  private final CreateShopPendingDeliveryTracker pendingTracker =
      new CreateShopPendingDeliveryTracker();
  private final CreateShopPendingTopupService pendingTopupService;
  private final CreateShopPendingDeliveryCreationService pendingDeliveryCreationService;
  private final CreateShopReservationReleaseService reservationReleaseService;
  private final CreateShopWarehouseCountService warehouseCountService =
      new CreateShopWarehouseCountService();
  private final CreateShopFlowTimeoutCleanupService flowTimeoutCleanupService;
  private final CreateShopDeliveryCompletionService deliveryCompletionService;
  private final CreateShopRetryingReassignService retryingReassignService =
      new CreateShopRetryingReassignService();
  private final CreateShopPendingTokenCollectorService pendingTokenCollectorService =
      new CreateShopPendingTokenCollectorService(ownership, tickPendingTelemetryService);
  private final CreateShopPendingRequestGateService pendingRequestGateService =
      new CreateShopPendingRequestGateService(ownership);
  private final CreateShopChildReconciliationService childReconciliationService;
  private final CreateShopPendingStateDecisionService pendingStateDecisionService;
  private final CreateShopPostCreationUpdateService postCreationUpdateService;
  private final CreateShopDeliveryCancelService deliveryCancelService;
  private final CreateShopDeliveryRootCauseSnapshotService deliveryRootCauseSnapshotService =
      new CreateShopDeliveryRootCauseSnapshotService();
  private final CreateShopDeliveryChildRecoveryService deliveryChildRecoveryService;
  private final CreateShopRequestStateMutatorService requestStateMutatorService =
      new CreateShopRequestStateMutatorService();
  private final CreateShopReservationSyncService reservationSyncService;
  private final CreateShopPendingRequestProcessorService pendingRequestProcessorService;
  private final CreateShopAttemptResolveService attemptResolveService;
  private final CreateShopTickPendingService tickPendingService;
  private final CreateShopDeliveryChildLifecycleService deliveryChildLifecycleService =
      new CreateShopDeliveryChildLifecycleService();
  private final CreateShopTerminalRequestLifecycleService terminalRequestLifecycleService;
  private final CreateShopWorkerAvailabilityGate workerAvailabilityGate =
      new CreateShopWorkerAvailabilityGate();
  private final CreateShopRequestStateMachine flowStateMachine =
      new CreateShopRequestStateMachine();

  public CreateShopRequestResolver(ILocation location, IToken<?> token) {
    super(location, token);
    this.flowTimeoutCleanupService =
        new CreateShopFlowTimeoutCleanupService(requestStateMutatorService);
    this.deliveryCompletionService =
        new CreateShopDeliveryCompletionService(
            requestStateMutatorService, deliveryManager, diagnostics, recheck);
    this.pendingStateDecisionService =
        new CreateShopPendingStateDecisionService(
            requestStateMutatorService, workerAvailabilityGate, outstandingNeededService);
    this.postCreationUpdateService =
        new CreateShopPostCreationUpdateService(requestStateMutatorService, messaging);
    this.deliveryCancelService =
        new CreateShopDeliveryCancelService(
            requestStateMutatorService, diagnostics, recheck, deliveryManager);
    this.deliveryChildRecoveryService =
        new CreateShopDeliveryChildRecoveryService(requestStateMutatorService, ownership);
    this.reservationSyncService = new CreateShopReservationSyncService(requestStateMutatorService);
    this.attemptResolveService =
        new CreateShopAttemptResolveService(
            requestStateMutatorService, messaging, deliveryManager, outstandingNeededService);
    this.terminalRequestLifecycleService =
        new CreateShopTerminalRequestLifecycleService(
            requestStateMutatorService, cooldown, diagnostics);
    this.pendingTopupService =
        new CreateShopPendingTopupService(
            cooldown, pendingTracker, diagnostics, flowStateMachine, stockResolver, messaging);
    this.pendingDeliveryCreationService =
        new CreateShopPendingDeliveryCreationService(
            planning, deliveryManager, pendingState, messaging, diagnostics, flowStateMachine);
    this.reservationReleaseService = new CreateShopReservationReleaseService(messaging);
    this.childReconciliationService =
        new CreateShopChildReconciliationService(
            deliveryManager,
            deliveryChildLifecycleService,
            deliveryChildRecoveryService,
            deliveryRootCauseSnapshotService);
    this.pendingRequestProcessorService =
        new CreateShopPendingRequestProcessorService(
            pendingRequestGateService,
            childReconciliationService,
            pendingStateDecisionService,
            reservationSyncService,
            pendingTopupService,
            pendingDeliveryCreationService,
            postCreationUpdateService);
    this.tickPendingService =
        new CreateShopTickPendingService(
            pendingTokenCollectorService,
            pendingRequestProcessorService,
            flowTimeoutCleanupService,
            tickPendingTelemetryService);
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
    terminalRequestLifecycleService.resolveRequest(this, manager, request);
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

  boolean shouldDropMissingChild(Level level, IToken<?> childToken) {
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
    terminalRequestLifecycleService.onAssignedRequestBeingCancelled(this, manager, request);
  }

  @Override
  public void onAssignedRequestCancelled(
      @NotNull IRequestManager manager, @NotNull IRequest<? extends IDeliverable> request) {
    terminalRequestLifecycleService.onAssignedRequestCancelled(this, manager, request);
  }

  @Override
  public void onRequestedRequestComplete(
      @NotNull IRequestManager manager, @NotNull IRequest<?> request) {
    terminalRequestLifecycleService.onRequestedRequestComplete(this, manager, request);
  }

  @Override
  public void onRequestedRequestCancelled(
      @NotNull IRequestManager manager, @NotNull IRequest<?> request) {
    terminalRequestLifecycleService.onRequestedRequestCancelled(this, manager, request);
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

  static long getDeliveryChildStaleTimeoutFloorTicksForOps() {
    return DELIVERY_CHILD_STALE_TIMEOUT_FLOOR_TICKS;
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

  static boolean isTerminalRequestState(RequestState state) {
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

  BuildingCreateShop getShopForOps(IRequestManager manager) {
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

  long resolveNowTickForOps(IRequestManager manager) {
    return resolveNowTick(manager);
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

  private long getInflightTimeoutTicksSafe() {
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

  CreateShopRequestStateMachine getFlowStateMachineForOps() {
    return flowStateMachine;
  }

  long getInflightTimeoutTicksSafeForOps() {
    return getInflightTimeoutTicksSafe();
  }

  java.util.Map<IToken<?>, Long> getParentDeliveryActiveSinceForOps() {
    return parentDeliveryActiveSince;
  }

  java.util.Map<IToken<?>, Long> getParentStaleRecoveryArmedAtForOps() {
    return parentStaleRecoveryArmedAt;
  }

  java.util.Map<IToken<?>, String> getDeliveryRootCauseSnapshotsForOps() {
    return deliveryRootCauseSnapshots;
  }

  java.util.Map<IToken<?>, Long> getDeliveryRootCauseLastLogTickForOps() {
    return deliveryRootCauseLastLogTick;
  }

  java.util.Map<IToken<?>, Integer> getParentLastKnownChildCountForOps() {
    return parentLastKnownChildCount;
  }

  java.util.Map<IToken<?>, String> getParentLastKnownChildrenForOps() {
    return parentLastKnownChildren;
  }

  java.util.Map<IToken<?>, Long> getParentChildDropLastLogTickForOps() {
    return parentChildDropLastLogTick;
  }

  void clearStaleRecoveryArmForOps(IToken<?> parentToken) {
    deliveryChildLifecycleService.clearStaleRecoveryArm(this, parentToken);
  }

  void clearTrackedChildrenForParentForOps(
      IStandardRequestManager manager, IToken<?> parentToken) {
    deliveryChildLifecycleService.clearTrackedChildrenForParent(this, manager, parentToken);
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

  CreateShopResolverDiagnostics getDiagnosticsForOps() {
    return diagnostics;
  }

  CreateShopResolverRecheck getRecheck() {
    return recheck;
  }

  boolean isDebugLoggingEnabledForOps() {
    return isDebugLoggingEnabled();
  }

  java.util.Map<IToken<?>, Long> getRetryingReassignAttemptsForOps() {
    return retryingReassignAttempts;
  }

  void clearPendingTokenState(IToken<?> token, boolean clearFlowState) {
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

  void touchFlow(IToken<?> requestToken, long nowTick, String detail) {
    flowStateMachine.touch(requestToken, nowTick, detail);
  }

  void resolveViaWarehouse(
      IRequestManager manager, IRequest<? extends IDeliverable> request) {
    super.resolveRequest(manager, request);
  }

}
