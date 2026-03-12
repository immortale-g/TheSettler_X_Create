package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import com.google.common.collect.Lists;
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

  CreateShopAttemptResolveService(
      CreateShopRequestStateMutatorService requestStateMutatorService,
      CreateShopResolverMessaging messaging,
      CreateShopDeliveryManager deliveryManager,
      CreateShopOutstandingNeededService outstandingNeededService) {
    this.requestStateMutatorService = requestStateMutatorService;
    this.messaging = messaging;
    this.deliveryManager = deliveryManager;
    this.outstandingNeededService = outstandingNeededService;
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
    if (request.getState() == com.minecolonies.api.colony.requestsystem.request.RequestState.CANCELLED) {
      resolver.getCancelledRequests().add(request.getId());
    } else {
      resolver.getCancelledRequests().remove(request.getId());
    }
    if (resolver.getCancelledRequests().contains(request.getId())) {
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
    if (resolver.getCooldown().isRequestOnCooldown(level, request.getId())) {
      if (Config.DEBUG_LOGGING.getAsBoolean()) {
        TheSettlerXCreate.LOGGER.info(
            "[CreateShop] attemptResolve skipped (request already ordered)");
      }
      return Lists.newArrayList();
    }
    if (request.hasChildren()) {
      resolver.getFlowStateMachine().touch(request.getId(), now, "attemptResolve:has-children");
      if (Config.DEBUG_LOGGING.getAsBoolean()) {
        TheSettlerXCreate.LOGGER.info(
            "[CreateShop] attemptResolve skipped (has active children) request={}",
            request.getId());
      }
      return Lists.newArrayList();
    }
    if (resolver.hasDeliveriesCreated(request.getId())) {
      resolver
          .getFlowStateMachine()
          .touch(request.getId(), now, "attemptResolve:deliveries-created");
      return Lists.newArrayList();
    }
    IDeliverable deliverable = request.getRequest();
    resolver.getChain().sanitizeRequestChain(manager, request);

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
      resolver
          .getDiagnostics()
          .recordPendingSource(request.getId(), "attemptResolve:block-auto-reorder-started");
      resolver
          .getFlowStateMachine()
          .touch(request.getId(), now, "attemptResolve:block-auto-reorder-started");
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
      resolver.getFlowStateMachine().touch(request.getId(), now, "attemptResolve:no-needed");
      if (Config.DEBUG_LOGGING.getAsBoolean()) {
        TheSettlerXCreate.LOGGER.info("[CreateShop] attemptResolve skipped (needed<=0)");
      }
      return Lists.newArrayList();
    }
    boolean workerWorking = shop.isWorkerWorking();

    CreateShopStockSnapshot snapshot =
        resolver
            .getStockResolver()
            .getAvailability(
                tile, pickup, deliverable, reservedForOthers, resolver.getPlanning());
    int rackUsable = snapshot.getRackUsable();
    int networkAvailable = workerWorking ? snapshot.getNetworkAvailable() : 0;
    int available = Math.max(0, networkAvailable + rackUsable);
    int provide = Math.min(available, needed);
    if (provide <= 0) {
      resolver.getFlowStateMachine().touch(request.getId(), now, "attemptResolve:insufficient");
      if (Config.DEBUG_LOGGING.getAsBoolean()) {
        TheSettlerXCreate.LOGGER.info(
            "[CreateShop] attemptResolve aborted (available={}, reserved={}, needed={}) for {}",
            available,
            reservedForOthers,
            needed,
            deliverable);
      }
      requestStateMutatorService.markOrderedWithPending(resolver, level, request.getId(), needed);
      resolver.getDiagnostics().recordPendingSource(request.getId(), "attemptResolve:insufficient");
      return Lists.newArrayList();
    }

    List<com.minecolonies.api.util.Tuple<ItemStack, BlockPos>> planned =
        resolver
            .getPlanning()
            .planFromRacksWithPositions(tile, deliverable, Math.min(provide, rackUsable));
    List<ItemStack> ordered = resolver.getPlanning().extractStacks(planned);
    int plannedCount = ordered.stream().mapToInt(ItemStack::getCount).sum();
    int remaining = Math.max(0, provide - plannedCount);
    if (remaining > 0 && workerWorking) {
      String requesterName = messaging.resolveRequesterName(manager, request);
      ordered.addAll(
          resolver
              .getStockResolver()
              .requestFromNetwork(tile, deliverable, remaining, requesterName));
    }
    if (Config.DEBUG_LOGGING.getAsBoolean()) {
      TheSettlerXCreate.LOGGER.info(
          "[CreateShop] attemptResolve provide={} (available={}, reserved={}, needed={}) -> ordered {} stack(s)",
          provide,
          available,
          reservedForOthers,
          needed,
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
        resolver
            .getDiagnostics()
            .recordPendingSource(request.getId(), "attemptResolve:defer-network-arrival");
        resolver
            .getFlowStateMachine()
            .touch(request.getId(), now, "attemptResolve:defer-network-arrival");
        messaging.sendShopChat(manager, "com.thesettler_x_create.message.createshop.request_sent", ordered);
      } else if (rackUsable > 0) {
        if (CreateShopRequestResolver.unwrapStandardManager(manager) == null) {
          requestStateMutatorService.markOrderedWithPendingAtLeastOne(
              resolver, level, request.getId(), needed);
          resolver
              .getDiagnostics()
              .recordPendingSource(request.getId(), "attemptResolve:defer-wrapped-manager");
          resolver
              .getFlowStateMachine()
              .touch(request.getId(), now, "attemptResolve:defer-wrapped-manager");
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
        resolver
            .getDiagnostics()
            .recordPendingSource(request.getId(), "attemptResolve:network-ordered");
        messaging.sendShopChat(manager, "com.thesettler_x_create.message.createshop.request_sent", ordered);
      }
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

