package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import com.minecolonies.api.colony.requestsystem.manager.IRequestManager;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.requestable.IDeliverable;
import com.thesettler_x_create.Config;
import com.thesettler_x_create.TheSettlerXCreate;
import com.thesettler_x_create.blockentity.CreateShopBlockEntity;
import com.thesettler_x_create.minecolonies.tileentity.TileEntityCreateShop;
import com.thesettler_x_create.stock.ShopStockAccounting;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Handles pending top-up ordering decisions for resolver tick reconciliation. */
final class CreateShopPendingTopupService {
  private final CreateShopResolverDiagnostics diagnostics;
  private final CreateShopRequestStateMachine flowStateMachine;
  private final CreateShopStockResolver stockResolver;
  private final CreateShopResolverMessaging messaging;
  private final CreateShopRequestStateMutatorService requestStateMutatorService;
  private final CreateShopNetworkOrderService networkOrderService;

  CreateShopPendingTopupService(
      CreateShopResolverDiagnostics diagnostics,
      CreateShopRequestStateMachine flowStateMachine,
      CreateShopStockResolver stockResolver,
      CreateShopResolverMessaging messaging,
      CreateShopRequestStateMutatorService requestStateMutatorService,
      CreateShopNetworkOrderService networkOrderService) {
    this.networkOrderService = networkOrderService;
    this.diagnostics = diagnostics;
    this.flowStateMachine = flowStateMachine;
    this.stockResolver = stockResolver;
    this.messaging = messaging;
    this.requestStateMutatorService = requestStateMutatorService;
  }

  void handleTopup(
      CreateShopRequestResolver resolver,
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
    if (resolver == null) {
      return;
    }
    int topupNeeded =
        ShopStockAccounting.topupNeed(pendingCount, reservedForRequest, rackAvailableForRequest);

    // A completed partial delivery is already subtracted from pendingCount. Orders on their way
    // for this request, and unowned ones it takes over, are counted by the order service, so only
    // the real remainder is ordered. Nothing is reserved here; arrivals are.
    if (workerWorking && topupNeeded > 0) {
      CreateShopNetworkOrderService.OrderResult order =
          networkOrderService.orderMissing(
              tile,
              pickup,
              deliverable,
              CreateShopRequestResolver.toRequestId(request.getId()),
              topupNeeded,
              () -> stockResolver.getNetworkAvailable(tile, deliverable),
              messaging.resolveRequesterName(manager, request));
      List<ItemStack> topupOrdered = order.ordered();
      if (topupOrdered.isEmpty()) {
        if (order.somethingOnItsWay()) {
          requestStateMutatorService.markOrderedWithPending(
              resolver, level, request.getId(), pendingCount);
          diagnostics.recordPendingSource(request.getId(), "tickPending:wait-inflight");
          flowStateMachine.touch(request.getId(), level.getGameTime(), "tickPending:wait-inflight");
        }
        if (Config.DEBUG_LOGGING.getAsBoolean()) {
          TheSettlerXCreate.LOGGER.info(
              "[CreateShop] tickPending: {} network topup not ordered (inflightRemaining={}, claimed={}, topupNeeded={}, pending={}, reserved={}, rack={})",
              requestIdLog,
              order.ownInflight(),
              order.claimed(),
              topupNeeded,
              pendingCount,
              reservedForRequest,
              rackAvailableForRequest);
        }
        return;
      }
      requestStateMutatorService.markOrderedWithPending(
          resolver, level, request.getId(), pendingCount);
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
