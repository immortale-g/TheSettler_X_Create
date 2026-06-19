package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import com.minecolonies.api.colony.requestsystem.manager.IRequestManager;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.requestable.IDeliverable;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.thesettler_x_create.Config;
import com.thesettler_x_create.TheSettlerXCreate;
import com.thesettler_x_create.blockentity.CreateShopBlockEntity;
import com.thesettler_x_create.minecolonies.tileentity.TileEntityCreateShop;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Handles pending rack-availability checks and delivery-child creation planning. */
final class CreateShopPendingDeliveryCreationService {
  private final CreateShopResolverPlanning planning;
  private final CreateShopDeliveryManager deliveryManager;
  private final CreateShopResolverPendingState pendingState;
  private final CreateShopResolverMessaging messaging;
  private final CreateShopResolverDiagnostics diagnostics;
  private final CreateShopRequestStateMachine flowStateMachine;

  CreateShopPendingDeliveryCreationService(
      CreateShopResolverPlanning planning,
      CreateShopDeliveryManager deliveryManager,
      CreateShopResolverPendingState pendingState,
      CreateShopResolverMessaging messaging,
      CreateShopResolverDiagnostics diagnostics,
      CreateShopRequestStateMachine flowStateMachine) {
    this.planning = planning;
    this.deliveryManager = deliveryManager;
    this.pendingState = pendingState;
    this.messaging = messaging;
    this.diagnostics = diagnostics;
    this.flowStateMachine = flowStateMachine;
  }

  DeliveryCreationResult process(
      IRequestManager manager,
      IRequest<?> request,
      Level level,
      TileEntityCreateShop tile,
      CreateShopBlockEntity pickup,
      IDeliverable deliverable,
      int pendingCount,
      int rackAvailableForRequest,
      String requestIdLog) {
    int totalAvailable = rackAvailableForRequest;
    if (totalAvailable <= 0) {
      flowStateMachine.touch(request.getId(), level.getGameTime(), "tickPending:waiting-arrival");
      diagnostics.logPendingReasonChange(
          request.getId(),
          "wait:available="
              + totalAvailable
              + " rack="
              + rackAvailableForRequest
              + " pending="
              + pendingCount);
      if (pendingState.shouldNotifyPending(level, request.getId())) {
        messaging.sendShopChat(
            manager,
            "com.thesettler_x_create.message.createshop.delivery_waiting",
            java.util.Collections.singletonList(deliverable.getResult()));
      }
      if (Config.DEBUG_LOGGING.getAsBoolean()) {
        TheSettlerXCreate.LOGGER.info(
            "[CreateShop] tickPending: {} waiting (available={}, rackAvailable={}, pendingCount={})",
            requestIdLog,
            totalAvailable,
            rackAvailableForRequest,
            pendingCount);
      }
      return DeliveryCreationResult.waiting();
    }

    int deliverCount = Math.min(totalAvailable, pendingCount);
    List<com.minecolonies.api.util.Tuple<ItemStack, net.minecraft.core.BlockPos>> stacks =
        planning.planFromRacksWithPositions(tile, deliverable, deliverCount);
    if (stacks.isEmpty()) {
      flowStateMachine.touch(request.getId(), level.getGameTime(), "tickPending:plan-empty");
      diagnostics.logPendingReasonChange(request.getId(), "wait:plan-empty");
      if (Config.DEBUG_LOGGING.getAsBoolean()) {
        TheSettlerXCreate.LOGGER.info(
            "[CreateShop] tickPending: {} skip (plan empty, rackAvailable={}, pendingCount={})",
            requestIdLog,
            rackAvailableForRequest,
            pendingCount);
      }
      return DeliveryCreationResult.waiting();
    }
    if (Config.DEBUG_LOGGING.getAsBoolean()) {
      TheSettlerXCreate.LOGGER.info(
          "[CreateShop] tickPending: {} creating deliveries (stacks={}, deliverCount={}, pendingCount={}, rackAvailable={})",
          requestIdLog,
          stacks.size(),
          deliverCount,
          pendingCount,
          rackAvailableForRequest);
    }
    List<IToken<?>> created =
        deliveryManager.createDeliveriesFromStacks(manager, request, stacks, pickup);
    if (created.isEmpty()) {
      flowStateMachine.touch(request.getId(), level.getGameTime(), "tickPending:create-failed");
      diagnostics.logPendingReasonChange(request.getId(), "create:failed");
      if (Config.DEBUG_LOGGING.getAsBoolean()) {
        TheSettlerXCreate.LOGGER.info(
            "[CreateShop] tickPending: {} create failed (no deliveries created)", requestIdLog);
      }
      return DeliveryCreationResult.waiting();
    }

    List<ItemStack> ordered = planning.extractStacks(stacks);
    int deliveredCount = planning.countPlanned(stacks);
    int remainingCount = Math.max(0, pendingCount - deliveredCount);
    return DeliveryCreationResult.created(ordered, remainingCount);
  }

  record DeliveryCreationResult(boolean created, List<ItemStack> ordered, int remainingCount) {
    static DeliveryCreationResult waiting() {
      return new DeliveryCreationResult(false, java.util.Collections.emptyList(), 0);
    }

    static DeliveryCreationResult created(List<ItemStack> ordered, int remainingCount) {
      return new DeliveryCreationResult(true, ordered, remainingCount);
    }
  }
}

final class CreateShopResolverPendingState {
  private final java.util.Map<IToken<?>, Long> pendingNotices =
      new java.util.concurrent.ConcurrentHashMap<>();

  CreateShopResolverPendingState() {}

  boolean shouldNotifyPending(Level level, IToken<?> token) {
    if (level == null || token == null) {
      return false;
    }
    Long next = pendingNotices.get(token);
    long now = level.getGameTime();
    if (next != null && now < next) {
      return false;
    }
    pendingNotices.put(token, now + Config.PENDING_NOTICE_COOLDOWN.getAsLong());
    return true;
  }
}

/** Applies post-delivery-creation flow transitions and pending/cooldown state updates. */
final class CreateShopPostCreationUpdateService {
  private final CreateShopRequestStateMutatorService requestStateMutatorService;
  private final CreateShopResolverMessaging messaging;
  private final CreateShopResolverDiagnostics diagnostics;

  CreateShopPostCreationUpdateService(
      CreateShopRequestStateMutatorService requestStateMutatorService,
      CreateShopResolverMessaging messaging,
      CreateShopResolverDiagnostics diagnostics) {
    this.requestStateMutatorService = requestStateMutatorService;
    this.messaging = messaging;
    this.diagnostics = diagnostics;
  }

  void apply(
      CreateShopRequestResolver resolver,
      IRequestManager manager,
      IRequest<?> request,
      Level level,
      CreateShopPendingDeliveryCreationService.DeliveryCreationResult creationResult,
      String requestIdLog) {
    if (resolver == null
        || manager == null
        || request == null
        || level == null
        || creationResult == null
        || !creationResult.created()) {
      return;
    }
    List<ItemStack> ordered = creationResult.ordered();
    ItemStack first = ordered.isEmpty() ? ItemStack.EMPTY : ordered.get(0);
    int orderedCount = CreateShopStackMetrics.countStackList(ordered);
    resolver.transitionFlow(
        manager,
        request,
        CreateShopFlowState.ARRIVED_IN_SHOP_RACK,
        "tickPending:rack-arrived",
        CreateShopStackMetrics.describeStack(first),
        orderedCount,
        "com.thesettler_x_create.message.createshop.flow_arrived");
    messaging.sendShopChat(
        manager, "com.thesettler_x_create.message.createshop.delivery_created", ordered);
    resolver.transitionFlow(
        manager,
        request,
        CreateShopFlowState.DELIVERY_CREATED,
        "tickPending:delivery-created",
        CreateShopStackMetrics.describeStack(first),
        orderedCount,
        "com.thesettler_x_create.message.createshop.flow_delivery_created");
    diagnostics.logPendingReasonChange(request.getId(), "create:delivery");

    int remainingCount = Math.max(0, creationResult.remainingCount());
    if (remainingCount != creationResult.remainingCount()) {
      diagnostics.logPendingReasonChange(request.getId(), "normalize:remaining<0");
      if (Config.DEBUG_LOGGING.getAsBoolean()) {
        TheSettlerXCreate.LOGGER.info(
            "[CreateShop] tickPending: {} normalized remainingCount {} -> 0",
            requestIdLog,
            creationResult.remainingCount());
      }
    }

    if (remainingCount > 0) {
      requestStateMutatorService.markOrderedWithPending(
          resolver, level, request.getId(), remainingCount);
      diagnostics.recordPendingSource(request.getId(), "tickPending:partial");
    } else {
      // Keep tracking while child delivery is active; do not drop cooldown yet.
      requestStateMutatorService.markOrderedWithPending(resolver, level, request.getId(), 0);
      diagnostics.recordPendingSource(request.getId(), "tickPending:await-child-complete");
    }

    if (Config.DEBUG_LOGGING.getAsBoolean()) {
      TheSettlerXCreate.LOGGER.info(
          "[CreateShop] tickPending: {} skip assignRequest (delivery created)", requestIdLog);
    }
  }
}
