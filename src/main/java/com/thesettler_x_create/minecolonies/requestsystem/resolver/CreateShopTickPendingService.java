package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import com.minecolonies.api.colony.requestsystem.manager.IRequestManager;
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

  CreateShopTickPendingService(
      CreateShopPendingTokenCollectorService pendingTokenCollectorService,
      CreateShopPendingRequestProcessorService pendingRequestProcessorService,
      CreateShopFlowTimeoutCleanupService flowTimeoutCleanupService,
      CreateShopTickPendingTelemetryService tickPendingTelemetryService) {
    this.pendingTokenCollectorService = pendingTokenCollectorService;
    this.pendingRequestProcessorService = pendingRequestProcessorService;
    this.flowTimeoutCleanupService = flowTimeoutCleanupService;
    this.tickPendingTelemetryService = tickPendingTelemetryService;
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
    IStandardRequestManager standardManager = CreateShopRequestResolver.unwrapStandardManager(manager);
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
    resolver.reassignResolvableRetryingRequests(standardManager, level);
    resolver.getRecheck().processParentChildRechecks(standardManager, level);
    var assignmentStore = standardManager.getRequestResolverRequestAssignmentDataStore();
    var requestHandler = standardManager.getRequestHandler();
    Map<IToken<?>, java.util.Collection<IToken<?>>> assignments = assignmentStore.getAssignments();
    Set<IToken<?>> pendingTokens =
        pendingTokenCollectorService.collectPendingTokens(
            resolver, standardManager, level, assignments);
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
