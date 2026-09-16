package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import com.google.common.collect.Lists;
import com.minecolonies.api.colony.requestsystem.manager.IRequestManager;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.requestable.IDeliverable;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.thesettler_x_create.DebugLog;
import com.thesettler_x_create.TheSettlerXCreate;
import com.thesettler_x_create.blockentity.CreateShopBlockEntity;
import com.thesettler_x_create.minecolonies.building.BuildingCreateShop;
import com.thesettler_x_create.minecolonies.tileentity.TileEntityCreateShop;
import com.thesettler_x_create.stock.ShopStockAccounting;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Encapsulates attempt-resolve orchestration for Create Shop requests. */
final class CreateShopAttemptResolveService {
  private final CreateShopRequestStateMutatorService requestStateMutatorService;
  private final CreateShopResolverMessaging messaging;
  private final CreateShopDeliveryManager deliveryManager;
  private final CreateShopOutstandingNeededService outstandingNeededService;
  private final CreateShopResolverCooldown cooldown;
  private final CreateShopResolverChain chain;
  private final CreateShopResolverPlanning planning;
  private final CreateShopStockResolver stockResolver;
  private final CreateShopResolverDiagnostics diagnostics;
  private final CreateShopRequestStateMachine flowStateMachine;
  private final CreateShopNetworkOrderService networkOrderService;

  CreateShopAttemptResolveService(
      CreateShopRequestStateMutatorService requestStateMutatorService,
      CreateShopResolverMessaging messaging,
      CreateShopDeliveryManager deliveryManager,
      CreateShopOutstandingNeededService outstandingNeededService,
      CreateShopResolverCooldown cooldown,
      CreateShopResolverChain chain,
      CreateShopResolverPlanning planning,
      CreateShopStockResolver stockResolver,
      CreateShopResolverDiagnostics diagnostics,
      CreateShopRequestStateMachine flowStateMachine,
      CreateShopNetworkOrderService networkOrderService) {
    this.networkOrderService = networkOrderService;
    this.requestStateMutatorService = requestStateMutatorService;
    this.messaging = messaging;
    this.deliveryManager = deliveryManager;
    this.outstandingNeededService = outstandingNeededService;
    this.cooldown = cooldown;
    this.chain = chain;
    this.planning = planning;
    this.stockResolver = stockResolver;
    this.diagnostics = diagnostics;
    this.flowStateMachine = flowStateMachine;
  }

  List<IToken<?>> attemptResolve(
      CreateShopRequestResolver resolver,
      IRequestManager manager,
      IRequest<? extends IDeliverable> request) {
    long now = resolver.resolveNowTick(manager);
    resolver.transitionFlow(
        manager,
        request,
        CreateShopFlowState.ELIGIBILITY_CHECK,
        "attemptResolve:start",
        "",
        0,
        null);
    if (request.getState()
        == com.minecolonies.api.colony.requestsystem.request.RequestState.CANCELLED) {
      resolver.markCancelledRequest(request.getId());
    } else {
      resolver.clearCancelledRequest(request.getId());
    }
    if (resolver.isCancelledRequest(request.getId())) {
      if (DebugLog.enabled()) {
        TheSettlerXCreate.LOGGER.info(
            "[CreateShop] attemptResolve skipped (request cancelled) {}",
            (IToken<?>) request.getId());
      }
      return Lists.newArrayList();
    }
    Level level = manager.getColony().getWorld();
    if (level.isClientSide) {
      DebugLog.info("[CreateShop] attemptResolve skipped (no level or client)");
      return Lists.newArrayList();
    }
    if (cooldown.isRequestOnCooldown(level, request.getId())) {
      DebugLog.info("[CreateShop] attemptResolve skipped (request already ordered)");
      return Lists.newArrayList();
    }
    if (request.hasChildren()) {
      flowStateMachine.touch(request.getId(), now, "attemptResolve:has-children");
      if (DebugLog.enabled()) {
        TheSettlerXCreate.LOGGER.info(
            "[CreateShop] attemptResolve skipped (has active children) request={}",
            (IToken<?>) request.getId());
      }
      return Lists.newArrayList();
    }
    IDeliverable deliverable = request.getRequest();
    chain.sanitizeRequestChain(manager, request);

    BuildingCreateShop shop = resolver.getShop(manager);
    if (shop == null) {
      DebugLog.info("[CreateShop] attemptResolve skipped (shop missing)");
      return Lists.newArrayList();
    }
    TileEntityCreateShop tile = shop.getCreateShopTileEntity();
    if (tile == null || tile.getStockNetworkId() == null) {
      DebugLog.info("[CreateShop] attemptResolve skipped (missing stock network id)");
      return Lists.newArrayList();
    }
    shop.ensurePickupLink();
    CreateShopBlockEntity pickup = shop.getPickupBlockEntity();
    if (pickup == null) {
      DebugLog.info("[CreateShop] attemptResolve skipped (pickup block missing)");
      return Lists.newArrayList();
    }
    if (pickup.getLevel() == null) {
      DebugLog.info("[CreateShop] attemptResolve skipped (pickup level missing)");
      return Lists.newArrayList();
    }

    UUID requestId = CreateShopRequestResolver.toRequestId(request.getId());
    int reservedForRequest = pickup.getReservedForRequest(requestId);
    int needed = outstandingNeededService.compute(request, deliverable, reservedForRequest);
    int reservedForDeliverable = pickup.getReservedForDeliverable(deliverable);
    int reservedForOthers =
        ShopStockAccounting.reservedForOthers(reservedForDeliverable, reservedForRequest);
    if (needed <= 0) {
      flowStateMachine.touch(request.getId(), now, "attemptResolve:no-needed");
      DebugLog.info("[CreateShop] attemptResolve skipped (needed<=0)");
      return Lists.newArrayList();
    }
    boolean workerWorking = shop.isWorkerWorking();

    CreateShopStockSnapshot snapshot =
        stockResolver.getAvailability(tile, pickup, deliverable, reservedForOthers, planning);
    int rackUsable = snapshot.rackUsable();
    int networkAvailable = workerWorking ? snapshot.networkAvailable() : 0;
    int available = ShopStockAccounting.totalAvailable(networkAvailable, rackUsable, 0);
    int provide = Math.min(available, needed);
    if (provide <= 0) {
      flowStateMachine.touch(request.getId(), now, "attemptResolve:insufficient");
      DebugLog.info(
          "[CreateShop] attemptResolve aborted (available={}, reserved={}, needed={}) for {}",
          available,
          reservedForOthers,
          needed,
          deliverable);
      requestStateMutatorService.markOrderedWithPending(resolver, level, request.getId(), needed);
      diagnostics.recordPendingSource(request.getId(), "attemptResolve:insufficient");
      return Lists.newArrayList();
    }

    List<com.minecolonies.api.util.Tuple<ItemStack, BlockPos>> planned =
        planning.planFromRacksWithPositions(tile, deliverable, Math.min(provide, rackUsable));
    List<ItemStack> rackPlanned = planning.extractStacks(planned);
    List<ItemStack> ordered = Lists.newArrayList(rackPlanned);
    List<ItemStack> networkOrdered = Lists.newArrayList();
    int plannedCount = rackPlanned.stream().mapToInt(ItemStack::getCount).sum();
    int remaining = Math.max(0, provide - plannedCount);
    CreateShopNetworkOrderService.OrderResult networkOrder = null;
    if (remaining > 0 && workerWorking) {
      networkOrder =
          networkOrderService.orderMissing(
              tile,
              pickup,
              deliverable,
              requestId,
              remaining,
              () -> networkAvailable,
              messaging.resolveRequesterName(manager, request));
      networkOrdered.addAll(networkOrder.ordered());
      ordered.addAll(networkOrdered);
    }
    int effectiveNetworkNeeded = networkOrder == null ? remaining : networkOrder.orderedCount();
    if (DebugLog.enabled()) {
      TheSettlerXCreate.LOGGER.info(
          "[CreateShop] attemptResolve provide={} (available={}, reserved={}, needed={}, remaining={}, inflightRemaining={}, claimed={}, orderedNow={}) -> ordered {} stack(s)",
          provide,
          available,
          reservedForOthers,
          needed,
          remaining,
          networkOrder == null ? 0 : networkOrder.ownInflight(),
          networkOrder == null ? 0 : networkOrder.claimed(),
          effectiveNetworkNeeded,
          ordered.size());
    }
    boolean hasNetworkPortion = remaining > 0;
    if (!ordered.isEmpty()) {
      resolver.transitionFlow(
          manager,
          request,
          CreateShopFlowState.ORDERED_FROM_NETWORK,
          "attemptResolve:order-created",
          CreateShopStackMetrics.describeStack(ordered.get(0)),
          CreateShopStackMetrics.countStackList(ordered),
          "com.thesettler_x_create.message.createshop.flow_ordered");
      if (hasNetworkPortion) {
        requestStateMutatorService.markOrderedWithPendingAtLeastOne(
            resolver, level, request.getId(), needed);
        if (effectiveNetworkNeeded <= 0) {
          diagnostics.recordPendingSource(request.getId(), "attemptResolve:wait-existing-inflight");
          flowStateMachine.touch(request.getId(), now, "attemptResolve:wait-existing-inflight");
        } else {
          diagnostics.recordPendingSource(request.getId(), "attemptResolve:defer-network-arrival");
          flowStateMachine.touch(request.getId(), now, "attemptResolve:defer-network-arrival");
          messaging.sendShopChat(
              manager, "com.thesettler_x_create.message.createshop.request_sent", networkOrdered);
        }
      } else if (rackUsable > 0) {
        if (CreateShopRequestResolver.unwrapStandardManager(manager) == null) {
          requestStateMutatorService.markOrderedWithPendingAtLeastOne(
              resolver, level, request.getId(), needed);
          diagnostics.recordPendingSource(request.getId(), "attemptResolve:defer-wrapped-manager");
          flowStateMachine.touch(request.getId(), now, "attemptResolve:defer-wrapped-manager");
          if (DebugLog.enabled()) {
            TheSettlerXCreate.LOGGER.info(
                "[CreateShop] attemptResolve defer delivery creation (wrapped manager) request={} needed={} rackUsable={}",
                request.getId(),
                needed,
                rackUsable);
          }
          return Lists.newArrayList();
        }
        resolver.transitionFlow(
            manager,
            request,
            CreateShopFlowState.ARRIVED_IN_SHOP_RACK,
            "attemptResolve:rack-usable",
            CreateShopStackMetrics.describeStack(ordered.get(0)),
            CreateShopStackMetrics.countStackList(ordered),
            "com.thesettler_x_create.message.createshop.flow_arrived");
        List<IToken<?>> created =
            deliveryManager.createDeliveriesFromStacks(manager, request, planned, pickup);
        if (DebugLog.enabled()) {
          TheSettlerXCreate.LOGGER.info(
              "[CreateShop] attemptResolve created deliveries parent={} manager={} tokens={}",
              request.getId(),
              manager.getClass().getName(),
              created);
        }
        if (!created.isEmpty()) {
          resolver.transitionFlow(
              manager,
              request,
              CreateShopFlowState.DELIVERY_CREATED,
              "attemptResolve:delivery-created",
              CreateShopStackMetrics.describeStack(ordered.get(0)),
              plannedCount,
              "com.thesettler_x_create.message.createshop.flow_delivery_created");
        }
        return created;
      } else {
        requestStateMutatorService.markOrderedWithPending(resolver, level, request.getId(), needed);
        diagnostics.recordPendingSource(request.getId(), "attemptResolve:network-ordered");
        if (!networkOrdered.isEmpty()) {
          messaging.sendShopChat(
              manager, "com.thesettler_x_create.message.createshop.request_sent", networkOrdered);
        }
      }
    }

    if (ordered.isEmpty()
        && remaining > 0
        && networkOrder != null
        && networkOrder.somethingOnItsWay()) {
      requestStateMutatorService.markOrderedWithPendingAtLeastOne(
          resolver, level, request.getId(), needed);
      diagnostics.recordPendingSource(request.getId(), "attemptResolve:wait-existing-inflight");
      flowStateMachine.touch(request.getId(), now, "attemptResolve:wait-existing-inflight");
      return Lists.newArrayList();
    }

    // Only rack stock is reserved. What was ordered from the network is tracked as on its way and
    // reserved for this request when it arrives.
    for (ItemStack stack : rackPlanned) {
      if (!stack.isEmpty()) {
        pickup.reserve(requestId, stack.copy(), stack.getCount());
      }
    }

    return Lists.newArrayList();
  }
}
