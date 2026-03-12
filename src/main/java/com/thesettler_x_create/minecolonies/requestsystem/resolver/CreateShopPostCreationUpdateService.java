package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import com.minecolonies.api.colony.requestsystem.manager.IRequestManager;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.thesettler_x_create.Config;
import com.thesettler_x_create.TheSettlerXCreate;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

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
