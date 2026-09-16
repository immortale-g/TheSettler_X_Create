package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import com.minecolonies.api.colony.requestsystem.management.IRequestHandler;
import com.minecolonies.api.colony.requestsystem.manager.IRequestManager;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.requestable.IDeliverable;
import com.minecolonies.api.colony.requestsystem.requestable.deliveryman.Delivery;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.api.util.Tuple;
import com.thesettler_x_create.DebugLog;
import com.thesettler_x_create.TheSettlerXCreate;
import com.thesettler_x_create.blockentity.CreateShopBlockEntity;
import com.thesettler_x_create.minecolonies.building.BuildingCreateShop;
import com.thesettler_x_create.minecolonies.tileentity.TileEntityCreateShop;
import com.thesettler_x_create.stock.OpenDeliveryPlan;
import com.thesettler_x_create.stock.ShopStockAccounting;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Keeps a request moving while some of its deliveries are still open: reserves free rack stock,
 * orders what is missing and hands out deliveries for stock that arrived in the meantime, instead
 * of waiting until every open delivery is done.
 *
 * <p>The numbers are counted so that nothing is ordered or delivered twice; see {@link
 * OpenDeliveryPlan} for which side each decision leans to.
 */
final class CreateShopOpenDeliveryTopupService {
  private final CreateShopOutstandingNeededService outstandingNeededService;
  private final CreateShopResolverPlanning planning;
  private final CreateShopNetworkOrderService networkOrderService;
  private final CreateShopStockResolver stockResolver;
  private final CreateShopDeliveryManager deliveryManager;
  private final CreateShopPostCreationUpdateService postCreationUpdateService;
  private final CreateShopRequestStateMutatorService requestStateMutatorService;
  private final CreateShopResolverMessaging messaging;
  private final CreateShopResolverDiagnostics diagnostics;

  CreateShopOpenDeliveryTopupService(
      CreateShopOutstandingNeededService outstandingNeededService,
      CreateShopResolverPlanning planning,
      CreateShopNetworkOrderService networkOrderService,
      CreateShopStockResolver stockResolver,
      CreateShopDeliveryManager deliveryManager,
      CreateShopPostCreationUpdateService postCreationUpdateService,
      CreateShopRequestStateMutatorService requestStateMutatorService,
      CreateShopResolverMessaging messaging,
      CreateShopResolverDiagnostics diagnostics) {
    this.outstandingNeededService = outstandingNeededService;
    this.planning = planning;
    this.networkOrderService = networkOrderService;
    this.stockResolver = stockResolver;
    this.deliveryManager = deliveryManager;
    this.postCreationUpdateService = postCreationUpdateService;
    this.requestStateMutatorService = requestStateMutatorService;
    this.messaging = messaging;
    this.diagnostics = diagnostics;
  }

  /** Items in open deliveries of a request, and the part not confirmed as picked up. */
  record OpenDeliveries(int total, int notPickedUp) {}

  void process(
      CreateShopRequestResolver resolver,
      IRequestManager manager,
      IRequestHandler requestHandler,
      IRequest<?> request,
      Level level,
      BuildingCreateShop shop,
      TileEntityCreateShop tile,
      CreateShopBlockEntity pickup,
      IDeliverable deliverable,
      boolean workerWorking,
      String requestIdLog) {
    if (!workerWorking
        || resolver == null
        || request == null
        || tile == null
        || pickup == null
        || deliverable == null) {
      return;
    }
    int needed = outstandingNeededService.compute(request, deliverable, 0);
    if (needed <= 0) {
      return;
    }
    UUID requestId = CreateShopRequestResolver.toRequestId(request.getId());
    OpenDeliveries open =
        countOpenDeliveries(resolver, requestHandler, request, shop, pickup, deliverable);
    int rackAvailable = planning.getAvailableFromRacks(tile, deliverable);
    OpenDeliveryPlan plan =
        OpenDeliveryPlan.of(
            needed,
            pickup.getReservedForRequest(requestId),
            open.total(),
            open.notPickedUp(),
            pickup.getInflightRemainingFor(requestId, deliverable::matches),
            ShopStockAccounting.unreservedStock(
                rackAvailable, pickup.getReservedForDeliverable(deliverable)));
    if (DebugLog.enabled()) {
      TheSettlerXCreate.LOGGER.info(
          "[CreateShop] tickPending: {} open deliveries={} notPickedUp={} needed={} plan={}",
          requestIdLog,
          open.total(),
          open.notPickedUp(),
          needed,
          plan);
    }

    if (plan.reserveFromRack() > 0) {
      for (Tuple<ItemStack, BlockPos> entry :
          planning.planFromRacksWithPositions(tile, deliverable, plan.reserveFromRack())) {
        ItemStack stack = entry == null ? null : entry.getA();
        if (stack != null && !stack.isEmpty()) {
          pickup.reserve(requestId, stack.copy(), stack.getCount());
        }
      }
    }

    if (plan.missingBeforeInflight() > 0) {
      CreateShopNetworkOrderService.OrderResult order =
          networkOrderService.orderMissing(
              tile,
              pickup,
              deliverable,
              requestId,
              plan.missingBeforeInflight(),
              () -> stockResolver.getNetworkAvailable(tile, deliverable),
              messaging.resolveRequesterName(manager, request));
      if (!order.ordered().isEmpty()) {
        diagnostics.recordPendingSource(request.getId(), "tickPending:open-delivery-topup");
        resolver.touchFlow(request.getId(), level.getGameTime(), "tickPending:open-delivery-topup");
        messaging.sendShopChat(
            manager, "com.thesettler_x_create.message.createshop.request_sent", order.ordered());
      }
    }

    if (plan.deliverNow() > 0) {
      List<Tuple<ItemStack, BlockPos>> planned =
          planning.planFromRacksWithPositions(
              tile, deliverable, Math.min(plan.deliverNow(), rackAvailable));
      if (planned.isEmpty()) {
        return;
      }
      List<IToken<?>> created =
          deliveryManager.createDeliveriesFromStacks(manager, request, planned, pickup);
      if (created.isEmpty()) {
        return;
      }
      int deliveredCount = planning.countPlanned(planned);
      postCreationUpdateService.apply(
          resolver,
          manager,
          request,
          level,
          CreateShopPendingDeliveryCreationService.DeliveryCreationResult.created(
              planning.extractStacks(planned), Math.max(0, needed - open.total() - deliveredCount)),
          requestIdLog);
    } else if (plan.missingBeforeInflight() > 0) {
      requestStateMutatorService.markOrderedWithPendingAtLeastOne(
          resolver, level, request.getId(), plan.missingBeforeInflight());
    }
  }

  private static OpenDeliveries countOpenDeliveries(
      CreateShopRequestResolver resolver,
      IRequestHandler requestHandler,
      IRequest<?> request,
      BuildingCreateShop shop,
      CreateShopBlockEntity pickup,
      IDeliverable deliverable) {
    int total = 0;
    int notPickedUp = 0;
    if (request.getChildren() == null) {
      return new OpenDeliveries(0, 0);
    }
    for (IToken<?> childToken : List.copyOf(request.getChildren())) {
      IRequest<?> child;
      try {
        child = requestHandler.getRequest(childToken);
      } catch (Exception ignored) {
        continue;
      }
      if (child == null
          || CreateShopRequestResolver.isTerminalRequestState(child.getState())
          || !(child.getRequest() instanceof Delivery delivery)
          || !CreateShopDeliveryOriginMatcher.isLocalShopDeliveryChild(child, shop, pickup)
          || !deliverable.matches(delivery.getStack())) {
        continue;
      }
      int count = delivery.getStack().getCount();
      total += count;
      CreateShopDeliveryChildLedgerEntry ledger = resolver.getDeliveryChildLedgerEntry(childToken);
      if (ledger == null || ledger.pickupConfirmedAtTick < 0L) {
        notPickedUp += count;
      }
    }
    return new OpenDeliveries(total, notPickedUp);
  }
}
