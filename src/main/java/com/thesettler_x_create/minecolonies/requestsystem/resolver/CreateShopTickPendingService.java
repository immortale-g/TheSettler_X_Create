package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import com.minecolonies.api.colony.requestsystem.management.IRequestHandler;
import com.minecolonies.api.colony.requestsystem.manager.IRequestManager;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.requestable.IDeliverable;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.core.colony.requestsystem.management.IStandardRequestManager;
import com.thesettler_x_create.Config;
import com.thesettler_x_create.TheSettlerXCreate;
import com.thesettler_x_create.blockentity.CreateShopBlockEntity;
import com.thesettler_x_create.minecolonies.building.BuildingCreateShop;
import com.thesettler_x_create.minecolonies.tileentity.TileEntityCreateShop;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.world.level.Level;

/** Orchestrates tick-pending execution for Create Shop resolver state. */
final class CreateShopTickPendingService {
  private final CreateShopPendingTokenCollectorService pendingTokenCollectorService;
  private final CreateShopPendingRequestProcessorService pendingRequestProcessorService;
  private final CreateShopFlowTimeoutCleanupService flowTimeoutCleanupService;
  private final CreateShopTickPendingTelemetryService tickPendingTelemetryService;
  private final CreateShopLifecycleRehydrateService lifecycleRehydrateService;

  CreateShopTickPendingService(
      CreateShopPendingTokenCollectorService pendingTokenCollectorService,
      CreateShopPendingRequestProcessorService pendingRequestProcessorService,
      CreateShopFlowTimeoutCleanupService flowTimeoutCleanupService,
      CreateShopTickPendingTelemetryService tickPendingTelemetryService,
      CreateShopLifecycleRehydrateService lifecycleRehydrateService) {
    this.pendingTokenCollectorService = pendingTokenCollectorService;
    this.pendingRequestProcessorService = pendingRequestProcessorService;
    this.flowTimeoutCleanupService = flowTimeoutCleanupService;
    this.tickPendingTelemetryService = tickPendingTelemetryService;
    this.lifecycleRehydrateService = lifecycleRehydrateService;
  }

  void tickPendingDeliveries(CreateShopRequestResolver resolver, IRequestManager manager) {
    if (resolver == null) {
      return;
    }
    if (Config.DEBUG_LOGGING.getAsBoolean()) {
      TheSettlerXCreate.LOGGER.info(
          "[CreateShop] tickPending entry manager={} resolverId={}",
          manager == null ? "<null>" : manager.getClass().getName(),
          resolver.getResolverToken());
    }
    IStandardRequestManager standardManager =
        CreateShopRequestResolver.unwrapStandardManager(manager);
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
    resolver
        .getTerminalRequestLifecycleService()
        .sweepFastOrphanPickedUpRecoveries(resolver, manager, standardManager);
    resolver.reassignResolvableRetryingRequests(standardManager, level);
    resolver.getRecheck().processParentChildRechecks(standardManager, level);
    var assignmentStore = standardManager.getRequestResolverRequestAssignmentDataStore();
    var requestHandler = standardManager.getRequestHandler();
    Map<IToken<?>, java.util.Collection<IToken<?>>> assignments = assignmentStore.getAssignments();
    Set<IToken<?>> pendingTokens =
        pendingTokenCollectorService.collectPendingTokens(
            resolver, standardManager, level, assignments);
    pendingTokens =
        lifecycleRehydrateService.rehydrateAndFilter(
            resolver, standardManager, level, pendingTokens);
    if (pendingTokens.isEmpty()) {
      return;
    }
    if (Config.DEBUG_LOGGING.getAsBoolean()
        && tickPendingTelemetryService.shouldLogTickPending(level)) {
      int assignedCount = pendingTokens.size();
      int orderedCount = resolver.getCooldown().getOrderedCount();
      TheSettlerXCreate.LOGGER.info(
          "[CreateShop] tickPending: assigned={}, ordered={}, total={}",
          assignedCount,
          orderedCount,
          pendingTokens.size());
      tickPendingTelemetryService.logTickPendingCandidates(requestHandler, pendingTokens);
    }
    BuildingCreateShop shop = resolver.getShop(standardManager);
    if (shop == null) {
      return;
    }
    boolean workerWorking = shop.isWorkerWorking();
    if (!workerWorking) {
      if (Config.DEBUG_LOGGING.getAsBoolean()
          && tickPendingTelemetryService.shouldLogTickPending(level)) {
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

    for (IToken<?> token : List.copyOf(pendingTokens)) {
      pendingRequestProcessorService.processToken(
          resolver,
          manager,
          standardManager,
          requestHandler,
          assignmentStore::getAssignmentForValue,
          token,
          level,
          shop,
          tile,
          pickup,
          workerWorking);
    }
    flowTimeoutCleanupService.processTimedOutFlows(resolver, standardManager, level);
    tickPendingTelemetryService.recordAndMaybeLogPerf(level, System.nanoTime() - perfStart);
  }
}

/** Handles timeout-driven resolver cleanup for stale non-terminal flow records. */
final class CreateShopFlowTimeoutCleanupService {
  private final CreateShopRequestStateMutatorService requestStateMutatorService;

  CreateShopFlowTimeoutCleanupService(
      CreateShopRequestStateMutatorService requestStateMutatorService) {
    this.requestStateMutatorService = requestStateMutatorService;
  }

  void processTimedOutFlows(
      CreateShopRequestResolver resolver, IStandardRequestManager manager, Level level) {
    if (resolver == null || manager == null || level == null) {
      return;
    }
    long timeout = resolver.getInflightTimeoutTicksSafe();
    for (CreateShopFlowRecord record :
        resolver.getFlowStateMachine().collectTimedOut(level.getGameTime(), timeout)) {
      IToken<?> token = record.getRequestToken();
      boolean runtimeDeliveryWindowOpen =
          resolver.getParentDeliveryTokensSnapshot().contains(token)
              || resolver.hasDeliveriesCreated(token)
              || resolver.getPendingTracker().hasDeliveryStarted(token);
      IRequest<?> request = null;
      try {
        request = manager.getRequestHandler().getRequest(token);
      } catch (Exception ignored) {
        // Missing requests are cleaned up below.
      }
      if (request == null && runtimeDeliveryWindowOpen) {
        // Request graph lookups can be transiently stale; do not clear active parent lifecycle.
        resolver.touchFlow(token, level.getGameTime(), "timeout-cleanup:skip-runtime-active");
        continue;
      }
      if (request != null) {
        boolean terminal = CreateShopRequestResolver.isTerminalRequestState(request.getState());
        boolean deliveryWindowOpen = request.hasChildren() || runtimeDeliveryWindowOpen;
        if (!terminal && deliveryWindowOpen) {
          // Active deliveries can outlive local flow timestamps; do not clear parent lifecycle
          // here.
          resolver.touchFlow(token, level.getGameTime(), "timeout-cleanup:skip-active-delivery");
          continue;
        }
        resolver.transitionFlow(
            manager,
            request,
            CreateShopFlowState.FAILED,
            "timeout-cleanup",
            record.getStackLabel(),
            record.getAmount(),
            "com.thesettler_x_create.message.createshop.flow_timeout");
        if (request.getRequest() instanceof IDeliverable) {
          resolver.releaseReservation(manager, request);
        }
      }
      requestStateMutatorService.clearPendingTokenState(resolver, manager, token, true);
    }
  }
}

/** Rate-limited tick-pending diagnostics and perf logging state. */
final class CreateShopTickPendingTelemetryService {
  private final Object debugLock = new Object();
  private volatile long lastTickPendingDebugTime = 0L;
  private long lastPerfLogTime = 0L;
  private long lastTickPendingNanos = 0L;

  boolean shouldLogTickPending(Level level) {
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

  void logTickPendingCandidates(IRequestHandler requestHandler, Set<IToken<?>> pendingTokens) {
    int logged = 0;
    for (IToken<?> token : List.copyOf(pendingTokens)) {
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

  void recordAndMaybeLogPerf(Level level, long tickPendingNanos) {
    lastTickPendingNanos = tickPendingNanos;
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
}
