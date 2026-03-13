package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import com.google.common.collect.Lists;
import com.minecolonies.api.colony.requestsystem.manager.IRequestManager;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.requestable.IDeliverable;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.thesettler_x_create.Config;
import com.thesettler_x_create.TheSettlerXCreate;
import com.thesettler_x_create.blockentity.CreateShopBlockEntity;
import com.thesettler_x_create.minecolonies.building.BuildingCreateShop;
import com.thesettler_x_create.minecolonies.tileentity.TileEntityCreateShop;
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
      CreateShopRequestStateMachine flowStateMachine) {
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
      if (Config.DEBUG_LOGGING.getAsBoolean()) {
        TheSettlerXCreate.LOGGER.info(
            "[CreateShop] attemptResolve skipped (request cancelled) {}", request.getId());
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
    if (resolver.hasDeliveriesCreated(request.getId())) {
      flowStateMachine.touch(request.getId(), now, "attemptResolve:deliveries-created");
      return Lists.newArrayList();
    }
    IDeliverable deliverable = request.getRequest();
    chain.sanitizeRequestChain(manager, request);

    BuildingCreateShop shop = resolver.getShop(manager);
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

    UUID requestId = CreateShopRequestResolver.toRequestId(request.getId());
    int reservedForRequest = pickup.getReservedForRequest(requestId);
    int needed = outstandingNeededService.compute(request, deliverable, reservedForRequest);
    if (needed > 0 && resolver.getPendingTracker().hasDeliveryStarted(request.getId())) {
      requestStateMutatorService.markOrderedWithPendingAtLeastOne(
          resolver, level, request.getId(), needed);
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
      requestStateMutatorService.markOrderedWithPending(resolver, level, request.getId(), needed);
      diagnostics.recordPendingSource(request.getId(), "attemptResolve:insufficient");
      return Lists.newArrayList();
    }

    List<com.minecolonies.api.util.Tuple<ItemStack, BlockPos>> planned =
        planning.planFromRacksWithPositions(tile, deliverable, Math.min(provide, rackUsable));
    List<ItemStack> ordered = planning.extractStacks(planned);
    List<ItemStack> networkOrdered = Lists.newArrayList();
    int plannedCount = ordered.stream().mapToInt(ItemStack::getCount).sum();
    int remaining = Math.max(0, provide - plannedCount);
    String requesterName = null;
    int inflightRemaining = 0;
    int effectiveNetworkNeeded = remaining;
    if (remaining > 0 && workerWorking) {
      requesterName = messaging.resolveRequesterName(manager, request);
      inflightRemaining =
          pickup.getInflightRemaining(
              deliverable.getResult(), requesterName, tile.getShopAddress());
      effectiveNetworkNeeded = Math.max(0, remaining - Math.max(0, inflightRemaining));
      if (effectiveNetworkNeeded > 0) {
        networkOrdered.addAll(
            stockResolver.requestFromNetwork(
                tile, deliverable, effectiveNetworkNeeded, requesterName));
        ordered.addAll(networkOrdered);
      }
    }
    if (Config.DEBUG_LOGGING.getAsBoolean()) {
      TheSettlerXCreate.LOGGER.info(
          "[CreateShop] attemptResolve provide={} (available={}, reserved={}, needed={}, remaining={}, inflightRemaining={}, effectiveNetworkNeeded={}) -> ordered {} stack(s)",
          provide,
          available,
          reservedForOthers,
          needed,
          remaining,
          inflightRemaining,
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
          if (Config.DEBUG_LOGGING.getAsBoolean()) {
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
        if (Config.DEBUG_LOGGING.getAsBoolean()) {
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

    if (ordered.isEmpty() && remaining > 0 && workerWorking && effectiveNetworkNeeded <= 0) {
      requestStateMutatorService.markOrderedWithPendingAtLeastOne(
          resolver, level, request.getId(), needed);
      diagnostics.recordPendingSource(request.getId(), "attemptResolve:wait-existing-inflight");
      flowStateMachine.touch(request.getId(), now, "attemptResolve:wait-existing-inflight");
      return Lists.newArrayList();
    }

    if (!ordered.isEmpty()) {
      for (ItemStack stack : ordered) {
        if (stack.isEmpty()) {
          continue;
        }
        pickup.reserve(requestId, stack.copy(), stack.getCount());
      }
    }

    return Lists.newArrayList();
  }
}
