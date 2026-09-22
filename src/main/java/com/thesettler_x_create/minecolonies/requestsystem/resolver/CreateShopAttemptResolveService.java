package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import com.google.common.collect.Lists;
import com.minecolonies.api.colony.requestsystem.manager.IRequestManager;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.request.RequestState;
import com.minecolonies.api.colony.requestsystem.requestable.IDeliverable;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.api.util.Tuple;
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

/**
 * MineColonies' first attempt to resolve a request with the Create Shop.
 *
 * <p>The attempt runs in three steps: check that the shop can work on the request at all, plan how
 * much the racks and the Create network can provide, then take exactly one way out. Nothing
 * available: wait with the full need pending. Racks cover everything: hand out deliveries now.
 * Otherwise: order the rest from the network and reserve the rack part until the order arrives.
 * Returning no child tokens leaves the request with the shop; the tick carries it on from there.
 */
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

  /** Everything an eligible attempt works with, gathered once by {@link #checkEligible}. */
  private record Attempt(
      CreateShopRequestResolver resolver,
      IRequestManager manager,
      IRequest<? extends IDeliverable> request,
      IDeliverable deliverable,
      Level level,
      long now,
      TileEntityCreateShop tile,
      CreateShopBlockEntity pickup,
      UUID requestId,
      int needed,
      int reservedForOthers,
      boolean workerWorking) {}

  /**
   * What the racks and the network can provide for the attempt.
   *
   * @param networkAvailable what the network offers; zero while the shopkeeper is idle
   * @param provide what the shop takes on now: the need, capped at what is available
   * @param planned rack stacks with their positions, for creating deliveries
   * @param rackPlanned the same rack stacks without positions
   * @param remaining the part of {@code provide} the racks do not cover
   */
  private record StockPlan(
      int rackUsable,
      int networkAvailable,
      int available,
      int provide,
      List<Tuple<ItemStack, BlockPos>> planned,
      List<ItemStack> rackPlanned,
      int plannedCount,
      int remaining) {}

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
    Attempt attempt = checkEligible(resolver, manager, request, now);
    if (attempt == null) {
      return Lists.newArrayList();
    }
    StockPlan plan = planStock(attempt);
    if (plan.provide() <= 0) {
      waitForStock(attempt, plan);
      return Lists.newArrayList();
    }
    if (plan.remaining() <= 0) {
      return deliverFromRacks(attempt, plan);
    }
    orderRemainder(attempt, plan);
    return Lists.newArrayList();
  }

  /**
   * The three shop objects an attempt works on: the building, its tile entity, the pickup block.
   */
  private record ShopParts(
      BuildingCreateShop shop, TileEntityCreateShop tile, CreateShopBlockEntity pickup) {}

  /** The attempt's inputs, or null when the shop cannot work on the request now. */
  private Attempt checkEligible(
      CreateShopRequestResolver resolver,
      IRequestManager manager,
      IRequest<? extends IDeliverable> request,
      long now) {
    if (isCancelled(resolver, request)) {
      return null;
    }
    Level level = manager.getColony().getWorld();
    if (!isOpenForAnAttempt(request, level, now)) {
      return null;
    }
    IDeliverable deliverable = request.getRequest();
    chain.sanitizeRequestChain(manager, request);

    ShopParts parts = findShopParts(resolver, manager);
    if (parts == null) {
      return null;
    }
    CreateShopBlockEntity pickup = parts.pickup();

    UUID requestId = CreateShopRequestResolver.toRequestId(request.getId());
    int reservedForRequest = pickup.getReservedForRequest(requestId);
    int needed = outstandingNeededService.compute(request, deliverable, reservedForRequest);
    int reservedForOthers =
        ShopStockAccounting.reservedForOthers(
            pickup.getReservedForDeliverable(deliverable), reservedForRequest);
    if (needed <= 0) {
      flowStateMachine.touch(request.getId(), now, "attemptResolve:no-needed");
      DebugLog.info("[CreateShop] attemptResolve skipped (needed<=0)");
      return null;
    }
    return new Attempt(
        resolver,
        manager,
        request,
        deliverable,
        level,
        now,
        parts.tile(),
        pickup,
        requestId,
        needed,
        reservedForOthers,
        parts.shop().isWorkerWorking());
  }

  /**
   * Keeps the resolver's own cancelled-request note in step with the request's state and answers
   * whether the request is cancelled.
   */
  private boolean isCancelled(
      CreateShopRequestResolver resolver, IRequest<? extends IDeliverable> request) {
    if (request.getState() == RequestState.CANCELLED) {
      resolver.markCancelledRequest(request.getId());
    } else {
      resolver.clearCancelledRequest(request.getId());
    }
    if (!resolver.isCancelledRequest(request.getId())) {
      return false;
    }
    if (DebugLog.enabled()) {
      TheSettlerXCreate.LOGGER.info(
          "[CreateShop] attemptResolve skipped (request cancelled) {}",
          (IToken<?>) request.getId());
    }
    return true;
  }

  /**
   * Server side, not on cooldown from an earlier order, and without children already working on it.
   * Children mean another attempt is under way, so this one only keeps the flow clock alive.
   */
  private boolean isOpenForAnAttempt(
      IRequest<? extends IDeliverable> request, Level level, long now) {
    if (level.isClientSide) {
      DebugLog.info("[CreateShop] attemptResolve skipped (no level or client)");
      return false;
    }
    if (cooldown.isRequestOnCooldown(level, request.getId())) {
      DebugLog.info("[CreateShop] attemptResolve skipped (request already ordered)");
      return false;
    }
    if (request.hasChildren()) {
      flowStateMachine.touch(request.getId(), now, "attemptResolve:has-children");
      if (DebugLog.enabled()) {
        TheSettlerXCreate.LOGGER.info(
            "[CreateShop] attemptResolve skipped (has active children) request={}",
            (IToken<?>) request.getId());
      }
      return false;
    }
    return true;
  }

  /** The shop's parts, or null when one of them is missing or not set up yet. */
  private ShopParts findShopParts(CreateShopRequestResolver resolver, IRequestManager manager) {
    BuildingCreateShop shop = resolver.getShop(manager);
    if (shop == null) {
      DebugLog.info("[CreateShop] attemptResolve skipped (shop missing)");
      return null;
    }
    TileEntityCreateShop tile = shop.getCreateShopTileEntity();
    if (tile == null || tile.getStockNetworkId() == null) {
      DebugLog.info("[CreateShop] attemptResolve skipped (missing stock network id)");
      return null;
    }
    shop.ensurePickupLink();
    CreateShopBlockEntity pickup = shop.getPickupBlockEntity();
    if (pickup == null) {
      DebugLog.info("[CreateShop] attemptResolve skipped (pickup block missing)");
      return null;
    }
    if (pickup.getLevel() == null) {
      DebugLog.info("[CreateShop] attemptResolve skipped (pickup level missing)");
      return null;
    }
    return new ShopParts(shop, tile, pickup);
  }

  /** Plans the rack part first; the network only counts while the shopkeeper works. */
  private StockPlan planStock(Attempt a) {
    CreateShopStockSnapshot snapshot =
        stockResolver.getAvailability(
            a.tile(), a.pickup(), a.deliverable(), a.reservedForOthers(), planning);
    int rackUsable = snapshot.rackUsable();
    int networkAvailable = a.workerWorking() ? snapshot.networkAvailable() : 0;
    int available = ShopStockAccounting.totalAvailable(networkAvailable, rackUsable, 0);
    int provide = Math.min(available, a.needed());
    if (provide <= 0) {
      return new StockPlan(
          rackUsable, networkAvailable, available, provide, List.of(), List.of(), 0, 0);
    }
    List<Tuple<ItemStack, BlockPos>> planned =
        planning.planFromRacksWithPositions(
            a.tile(), a.deliverable(), Math.min(provide, rackUsable));
    List<ItemStack> rackPlanned = planning.extractStacks(planned);
    int plannedCount = rackPlanned.stream().mapToInt(ItemStack::getCount).sum();
    // Planning can find less than the snapshot reported when stock moved in between; the gap then
    // counts as remaining, like any part the racks do not cover.
    int remaining = Math.max(0, provide - plannedCount);
    return new StockPlan(
        rackUsable,
        networkAvailable,
        available,
        provide,
        planned,
        rackPlanned,
        plannedCount,
        remaining);
  }

  private void waitForStock(Attempt a, StockPlan plan) {
    flowStateMachine.touch(a.request().getId(), a.now(), "attemptResolve:insufficient");
    DebugLog.info(
        "[CreateShop] attemptResolve aborted (available={}, reserved={}, needed={}) for {}",
        plan.available(),
        a.reservedForOthers(),
        a.needed(),
        a.deliverable());
    requestStateMutatorService.markOrderedWithPending(
        a.resolver(), a.level(), a.request().getId(), a.needed());
    diagnostics.recordPendingSource(a.request().getId(), "attemptResolve:insufficient");
  }

  /**
   * The racks cover everything the shop takes on: create the deliveries right away. Their stacks
   * are not reserved, the deliveries themselves hold them.
   */
  private List<IToken<?>> deliverFromRacks(Attempt a, StockPlan plan) {
    IRequestManager manager = a.manager();
    IRequest<? extends IDeliverable> request = a.request();
    logPlan(a, plan, null, plan.rackPlanned().size());
    transitionOrdered(a, plan.rackPlanned());
    if (CreateShopRequestResolver.unwrapStandardManager(manager) == null) {
      keepPending(a, "attemptResolve:defer-wrapped-manager");
      if (DebugLog.enabled()) {
        TheSettlerXCreate.LOGGER.info(
            "[CreateShop] attemptResolve defer delivery creation (wrapped manager) request={} needed={} rackUsable={}",
            (IToken<?>) request.getId(),
            a.needed(),
            plan.rackUsable());
      }
      return Lists.newArrayList();
    }
    ItemStack first = plan.rackPlanned().get(0);
    a.resolver()
        .transitionFlow(
            manager,
            request,
            CreateShopFlowState.ARRIVED_IN_SHOP_RACK,
            "attemptResolve:rack-usable",
            CreateShopStackMetrics.describeStack(first),
            CreateShopStackMetrics.countStackList(plan.rackPlanned()),
            "com.thesettler_x_create.message.createshop.flow_arrived");
    List<IToken<?>> created =
        deliveryManager.createDeliveriesFromStacks(manager, request, plan.planned(), a.pickup());
    if (DebugLog.enabled()) {
      TheSettlerXCreate.LOGGER.info(
          "[CreateShop] attemptResolve created deliveries parent={} manager={} tokens={}",
          (IToken<?>) request.getId(),
          manager.getClass().getName(),
          created);
    }
    if (!created.isEmpty()) {
      a.resolver()
          .transitionFlow(
              manager,
              request,
              CreateShopFlowState.DELIVERY_CREATED,
              "attemptResolve:delivery-created",
              CreateShopStackMetrics.describeStack(first),
              plan.plannedCount(),
              "com.thesettler_x_create.message.createshop.flow_delivery_created");
    }
    return created;
  }

  /**
   * The racks do not cover everything: order the rest from the network (only while the shopkeeper
   * works) and reserve the rack part for this request. What was ordered is tracked as on its way
   * and reserved when it arrives.
   */
  private void orderRemainder(Attempt a, StockPlan plan) {
    CreateShopNetworkOrderService.OrderResult networkOrder =
        a.workerWorking()
            ? networkOrderService.orderMissing(
                a.tile(),
                a.pickup(),
                a.deliverable(),
                a.requestId(),
                plan.remaining(),
                plan::networkAvailable,
                messaging.resolveRequesterName(a.manager(), a.request()))
            : null;
    List<ItemStack> networkOrdered = networkOrder == null ? List.of() : networkOrder.ordered();
    List<ItemStack> ordered = Lists.newArrayList(plan.rackPlanned());
    ordered.addAll(networkOrdered);
    logPlan(a, plan, networkOrder, ordered.size());

    if (ordered.isEmpty()) {
      // The rack part is part of `ordered`, so nothing planned also means nothing to reserve.
      if (networkOrder != null && networkOrder.somethingOnItsWay()) {
        keepPending(a, "attemptResolve:wait-existing-inflight");
      }
      return;
    }

    transitionOrdered(a, ordered);
    int orderedNow = networkOrder == null ? plan.remaining() : networkOrder.orderedCount();
    if (orderedNow <= 0) {
      keepPending(a, "attemptResolve:wait-existing-inflight");
    } else {
      keepPending(a, "attemptResolve:defer-network-arrival");
      messaging.sendShopChat(
          a.manager(), "com.thesettler_x_create.message.createshop.request_sent", networkOrdered);
    }

    // Only rack stock is reserved. What was ordered from the network is tracked as on its way and
    // reserved for this request when it arrives.
    CreateShopBlockEntity pickup = a.pickup();
    List<ItemStack> rackPlanned = plan.rackPlanned();
    for (ItemStack stack : rackPlanned) {
      if (!stack.isEmpty()) {
        pickup.reserve(a.requestId(), stack.copy(), stack.getCount());
      }
    }
  }

  /**
   * Leaves the request with the shop for the next tick: at least one item still counts as pending,
   * the reason is noted for diagnosis and the flow clock is touched so nothing times out.
   */
  private void keepPending(Attempt a, String reason) {
    requestStateMutatorService.markOrderedWithPendingAtLeastOne(
        a.resolver(), a.level(), a.request().getId(), a.needed());
    diagnostics.recordPendingSource(a.request().getId(), reason);
    flowStateMachine.touch(a.request().getId(), a.now(), reason);
  }

  private void transitionOrdered(Attempt a, List<ItemStack> ordered) {
    a.resolver()
        .transitionFlow(
            a.manager(),
            a.request(),
            CreateShopFlowState.ORDERED_FROM_NETWORK,
            "attemptResolve:order-created",
            CreateShopStackMetrics.describeStack(ordered.get(0)),
            CreateShopStackMetrics.countStackList(ordered),
            "com.thesettler_x_create.message.createshop.flow_ordered");
  }

  private void logPlan(
      Attempt a,
      StockPlan plan,
      CreateShopNetworkOrderService.OrderResult networkOrder,
      int orderedStacks) {
    if (!DebugLog.enabled()) {
      return;
    }
    TheSettlerXCreate.LOGGER.info(
        "[CreateShop] attemptResolve provide={} (available={}, reserved={}, needed={}, remaining={}, inflightRemaining={}, claimed={}, orderedNow={}) -> ordered {} stack(s)",
        plan.provide(),
        plan.available(),
        a.reservedForOthers(),
        a.needed(),
        plan.remaining(),
        networkOrder == null ? 0 : networkOrder.ownInflight(),
        networkOrder == null ? 0 : networkOrder.claimed(),
        networkOrder == null ? plan.remaining() : networkOrder.orderedCount(),
        orderedStacks);
  }
}
