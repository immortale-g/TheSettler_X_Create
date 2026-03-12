package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import com.minecolonies.api.colony.requestsystem.manager.IRequestManager;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.requestable.IDeliverable;
import com.thesettler_x_create.Config;
import com.thesettler_x_create.TheSettlerXCreate;
import com.thesettler_x_create.blockentity.CreateShopBlockEntity;
import com.thesettler_x_create.minecolonies.tileentity.TileEntityCreateShop;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Handles pending top-up ordering decisions for resolver tick reconciliation. */
final class CreateShopPendingTopupService {
  private final CreateShopResolverCooldown cooldown;
  private final CreateShopPendingDeliveryTracker pendingTracker;
  private final CreateShopResolverDiagnostics diagnostics;
  private final CreateShopRequestStateMachine flowStateMachine;
  private final CreateShopStockResolver stockResolver;
  private final CreateShopResolverMessaging messaging;

  CreateShopPendingTopupService(
      CreateShopResolverCooldown cooldown,
      CreateShopPendingDeliveryTracker pendingTracker,
      CreateShopResolverDiagnostics diagnostics,
      CreateShopRequestStateMachine flowStateMachine,
      CreateShopStockResolver stockResolver,
      CreateShopResolverMessaging messaging) {
    this.cooldown = cooldown;
    this.pendingTracker = pendingTracker;
    this.diagnostics = diagnostics;
    this.flowStateMachine = flowStateMachine;
    this.stockResolver = stockResolver;
    this.messaging = messaging;
  }

  void handleTopup(
      IRequestManager manager,
      IRequest<?> request,
      Level level,
      TileEntityCreateShop tile,
      CreateShopBlockEntity pickup,
      IDeliverable deliverable,
      boolean workerWorking,
      int pendingCount,
      int reservedForRequest,
      int rackAvailableForRequest,
      String requestIdLog) {
    int topupNeeded =
        Math.max(
            0,
            pendingCount - Math.max(0, reservedForRequest) - Math.max(0, rackAvailableForRequest));

    if (workerWorking && topupNeeded > 0) {
      if (pendingTracker.hasDeliveryStarted(request.getId())) {
        cooldown.markRequestOrdered(level, request.getId());
        pendingTracker.setPendingCount(request.getId(), pendingCount);
        diagnostics.recordPendingSource(request.getId(), "tickPending:block-auto-reorder-started");
        flowStateMachine.touch(
            request.getId(), level.getGameTime(), "tickPending:block-auto-reorder-started");
        if (Config.DEBUG_LOGGING.getAsBoolean()) {
          TheSettlerXCreate.LOGGER.info(
              "[CreateShop] tickPending: {} network topup blocked (started-order, pending={}, reserved={}, rack={})",
              requestIdLog,
              pendingCount,
              reservedForRequest,
              rackAvailableForRequest);
        }
        return;
      }

      String requesterName = messaging.resolveRequesterName(manager, request);
      int inflightRemaining =
          pickup.getInflightRemaining(deliverable.getResult(), requesterName, tile.getShopAddress());
      if (inflightRemaining > 0) {
        cooldown.markRequestOrdered(level, request.getId());
        pendingTracker.setPendingCount(request.getId(), pendingCount);
        diagnostics.recordPendingSource(request.getId(), "tickPending:wait-inflight");
        flowStateMachine.touch(request.getId(), level.getGameTime(), "tickPending:wait-inflight");
        if (Config.DEBUG_LOGGING.getAsBoolean()) {
          TheSettlerXCreate.LOGGER.info(
              "[CreateShop] tickPending: {} network topup blocked (inflightRemaining={}, pending={}, reserved={}, rack={})",
              requestIdLog,
              inflightRemaining,
              pendingCount,
              reservedForRequest,
              rackAvailableForRequest);
        }
        return;
      }

      int networkAvailable = stockResolver.getNetworkAvailable(tile, deliverable);
      int topupCount = Math.min(networkAvailable, topupNeeded);
      if (topupCount <= 0) {
        return;
      }
      List<ItemStack> topupOrdered =
          stockResolver.requestFromNetwork(tile, deliverable, topupCount, requesterName);
      if (topupOrdered.isEmpty()) {
        return;
      }
      for (ItemStack stack : topupOrdered) {
        if (stack.isEmpty()) {
          continue;
        }
        pickup.reserve(request.getId(), stack.copy(), stack.getCount());
      }
      cooldown.markRequestOrdered(level, request.getId());
      pendingTracker.setPendingCount(request.getId(), pendingCount);
      diagnostics.recordPendingSource(request.getId(), "tickPending:network-topup");
      flowStateMachine.touch(request.getId(), level.getGameTime(), "tickPending:network-topup");
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
      return;
    }

    if (!workerWorking && topupNeeded > 0) {
      flowStateMachine.touch(request.getId(), level.getGameTime(), "tickPending:worker-idle-topup");
      diagnostics.logPendingReasonChange(request.getId(), "wait:worker-for-network-topup");
    }
  }

  private int countStackList(List<ItemStack> stacks) {
    int total = 0;
    if (stacks == null) {
      return 0;
    }
    for (ItemStack stack : stacks) {
      if (stack == null || stack.isEmpty()) {
        continue;
      }
      total += stack.getCount();
    }
    return total;
  }
}
