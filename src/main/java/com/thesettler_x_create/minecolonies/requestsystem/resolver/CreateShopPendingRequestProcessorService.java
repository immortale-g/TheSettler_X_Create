package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import com.minecolonies.api.colony.requestsystem.management.IRequestHandler;
import com.minecolonies.api.colony.requestsystem.manager.IRequestManager;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.request.RequestState;
import com.minecolonies.api.colony.requestsystem.requestable.IDeliverable;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.core.colony.requestsystem.management.IStandardRequestManager;
import com.thesettler_x_create.Config;
import com.thesettler_x_create.TheSettlerXCreate;
import com.thesettler_x_create.blockentity.CreateShopBlockEntity;
import com.thesettler_x_create.minecolonies.building.BuildingCreateShop;
import com.thesettler_x_create.minecolonies.tileentity.TileEntityCreateShop;
import java.util.UUID;
import java.util.function.Function;
import net.minecraft.world.level.Level;

/** Processes one pending request token during resolver tick-pending orchestration. */
final class CreateShopPendingRequestProcessorService {
  private final CreateShopPendingRequestGateService pendingRequestGateService;
  private final CreateShopChildReconciliationService childReconciliationService;
  private final CreateShopPendingStateDecisionService pendingStateDecisionService;
  private final CreateShopReservationSyncService reservationSyncService;
  private final CreateShopPendingTopupService pendingTopupService;
  private final CreateShopPendingDeliveryCreationService pendingDeliveryCreationService;
  private final CreateShopPostCreationUpdateService postCreationUpdateService;
  private final CreateShopResolverDiagnostics diagnostics;
  private final CreateShopRequestStateMutatorService requestStateMutatorService;

  CreateShopPendingRequestProcessorService(
      CreateShopPendingRequestGateService pendingRequestGateService,
      CreateShopChildReconciliationService childReconciliationService,
      CreateShopPendingStateDecisionService pendingStateDecisionService,
      CreateShopReservationSyncService reservationSyncService,
      CreateShopPendingTopupService pendingTopupService,
      CreateShopPendingDeliveryCreationService pendingDeliveryCreationService,
      CreateShopPostCreationUpdateService postCreationUpdateService,
      CreateShopResolverDiagnostics diagnostics,
      CreateShopRequestStateMutatorService requestStateMutatorService) {
    this.pendingRequestGateService = pendingRequestGateService;
    this.childReconciliationService = childReconciliationService;
    this.pendingStateDecisionService = pendingStateDecisionService;
    this.reservationSyncService = reservationSyncService;
    this.pendingTopupService = pendingTopupService;
    this.pendingDeliveryCreationService = pendingDeliveryCreationService;
    this.postCreationUpdateService = postCreationUpdateService;
    this.diagnostics = diagnostics;
    this.requestStateMutatorService = requestStateMutatorService;
  }

  void processToken(
      CreateShopRequestResolver resolver,
      IRequestManager manager,
      IStandardRequestManager standardManager,
      IRequestHandler requestHandler,
      Function<IToken<?>, IToken<?>> assignmentLookup,
      IToken<?> token,
      Level level,
      BuildingCreateShop shop,
      TileEntityCreateShop tile,
      CreateShopBlockEntity pickup,
      boolean workerWorking) {
    IRequest<?> request;
    try {
      request = requestHandler.getRequest(token);
    } catch (IllegalArgumentException ex) {
      requestStateMutatorService.clearPendingTokenState(resolver, standardManager, token, false);
      return;
    }
    if (pendingRequestGateService.shouldSkipForPendingProcessing(
        resolver, manager, standardManager, request, token)) {
      return;
    }
    if (CreateShopRequestResolver.isTerminalRequestState(request.getState())) {
      requestStateMutatorService.clearPendingTokenState(
          resolver, standardManager, request.getId(), true);
      diagnostics.logPendingReasonChange(request.getId(), "skip:terminal-state");
      if (Config.DEBUG_LOGGING.getAsBoolean()) {
        TheSettlerXCreate.LOGGER.info(
            "[CreateShop] tickPending: {} skip (terminal state={})",
            request.getId(),
            request.getState());
      }
      return;
    }
    diagnostics.logRequestStateChange(standardManager, token, "tickPending");
    IDeliverable deliverable = (IDeliverable) request.getRequest();
    String requestIdLog = request.getId().toString();
    UUID requestId = CreateShopRequestResolver.toRequestId(request.getId());
    int reservedForRequest = pickup.getReservedForRequest(requestId);
    boolean onCooldown = resolver.getCooldown().isRequestOnCooldown(level, request.getId());
    boolean deliveryStarted = resolver.getPendingTracker().hasDeliveryStarted(request.getId());
    boolean completionSeen = resolver.hasParentChildCompletedSeen(request.getId());
    if (request.hasChildren()) {
      diagnostics.logPendingReasonChange(request.getId(), "skip:has-children");
      java.util.Collection<IToken<?>> children =
          java.util.Objects.requireNonNull(request.getChildren(), "children");
      requestStateMutatorService.setParentChildrenSnapshot(
          resolver, request.getId(), children.size(), children.toString());
      var childResult =
          childReconciliationService.reconcile(
              resolver,
              standardManager,
              level,
              request,
              requestHandler,
              assignmentLookup,
              shop,
              pickup,
              requestIdLog);
      if (Config.DEBUG_LOGGING.getAsBoolean()) {
        TheSettlerXCreate.LOGGER.info(
            "[CreateShop] tickPending: {} skip (has children)", requestIdLog);
        diagnostics.logParentChildrenState(standardManager, request.getId(), "tickPending");
        if (childResult.childrenEmpty()) {
          TheSettlerXCreate.LOGGER.info(
              "[CreateShop] tickPending: {} children list empty despite hasChildren", requestIdLog);
        } else if (childResult.missing() > 0) {
          TheSettlerXCreate.LOGGER.info(
              "[CreateShop] tickPending: {} missing children={} total={}",
              requestIdLog,
              childResult.missing(),
              childResult.childrenCount());
        } else if (childResult.duplicateChildrenRemoved() > 0) {
          TheSettlerXCreate.LOGGER.info(
              "[CreateShop] tickPending: {} duplicate children removed={} total={}",
              requestIdLog,
              childResult.duplicateChildrenRemoved(),
              childResult.childrenCount());
        }
      }
      if (childResult.recoveredParent()) {
        return;
      }
      if (childResult.missing() > 0) {
        return;
      }
      if (!childResult.hasActiveChildren()) {
        requestStateMutatorService.clearParentDeliveryActive(resolver, request.getId());
        requestStateMutatorService.clearStaleRecoveryArm(resolver, request.getId());
      }
      if (childResult.hasActiveChildren() || request.hasChildren()) {
        return;
      }
    }
    Integer previousChildCount = resolver.getParentLastKnownChildCount(request.getId());
    if (previousChildCount != null && previousChildCount > 0 && !request.hasChildren()) {
      long now = level.getGameTime();
      Long lastDropLog = resolver.getParentChildDropLastLogTick(request.getId());
      if (lastDropLog == null || now - lastDropLog >= 100L) {
        requestStateMutatorService.markParentChildDropLog(resolver, request.getId(), now);
        if (Config.DEBUG_LOGGING.getAsBoolean()) {
          String previousChildren = resolver.getParentLastKnownChildren(request.getId());
          if (previousChildren == null) {
            previousChildren = "[]";
          }
          TheSettlerXCreate.LOGGER.info(
              "[CreateShop] root-cause parent-child-drop parent={} state={} prevChildCount={} prevChildren={} reservedForRequest={} pending={} cooldown={}",
              request.getId(),
              request.getState(),
              previousChildCount,
              previousChildren,
              reservedForRequest,
              resolver.getPendingTracker().getPendingCount(request.getId()),
              resolver.getCooldown().isRequestOnCooldown(level, request.getId()));
        }
      }
    }
    if (previousChildCount != null
        && previousChildCount > 0
        && !request.hasChildren()
        && deliveryStarted
        && !completionSeen) {
      IToken<?> pickedUpOrphanChild = resolver.findPickedUpOrphanChildForParent(request.getId());
      if (pickedUpOrphanChild != null) {
        resolver.markParentChildCompletedSeen(request.getId(), level.getGameTime());
        resolver.observeDeliveryChildCallbackTerminal(
            level, request.getId(), pickedUpOrphanChild, "orphan-pickedup-recovery");
        requestStateMutatorService.finalizeOrphanDeliveryChild(
            resolver, standardManager, pickedUpOrphanChild, "orphan-pickedup-recovery");
        requestStateMutatorService.completeDeliveryWindow(
            resolver, request.getId(), pickedUpOrphanChild);
        requestStateMutatorService.clearOrderedAndPending(resolver, request.getId());
        resolver.clearDeliveriesCreated(request.getId());
        diagnostics.logPendingReasonChange(request.getId(), "recover:orphan-pickedup-child");
        resolver.touchFlow(
            request.getId(), level.getGameTime(), "tickPending:recover-orphan-pickedup");
        try {
          standardManager.updateRequestState(request.getId(), RequestState.RESOLVED);
        } catch (Exception ignored) {
          // Best effort; local cleanup already prevents drift.
        }
        requestStateMutatorService.clearPendingTokenState(
            resolver, standardManager, request.getId(), true);
        if (Config.DEBUG_LOGGING.getAsBoolean()) {
          TheSettlerXCreate.LOGGER.info(
              "[CreateShop] tickPending: {} recovered orphan picked-up child={} -> parent resolved",
              requestIdLog,
              pickedUpOrphanChild);
        }
        return;
      }
      int heldPending = Math.max(1, resolver.getPendingTracker().getPendingCount(request.getId()));
      requestStateMutatorService.markOrderedWithPendingAtLeastOne(
          resolver, level, request.getId(), heldPending);
      diagnostics.logPendingReasonChange(request.getId(), "wait:child-dropped-without-callback");
      resolver.touchFlow(request.getId(), level.getGameTime(), "tickPending:wait-child-callback");
      if (Config.DEBUG_LOGGING.getAsBoolean()) {
        TheSettlerXCreate.LOGGER.info(
            "[CreateShop] tickPending: {} waiting (child dropped without callback, prevChildCount={}, pending={})",
            requestIdLog,
            previousChildCount,
            heldPending);
      }
      return;
    }
    requestStateMutatorService.setParentChildrenSnapshot(resolver, request.getId(), 0, "[]");
    requestStateMutatorService.clearParentDeliveryActive(resolver, request.getId());
    requestStateMutatorService.clearStaleRecoveryArm(resolver, request.getId());
    if (resolver.hasDeliveriesCreated(request.getId())) {
      if (completionSeen) {
        resolver.clearDeliveriesCreated(request.getId());
        diagnostics.logPendingReasonChange(
            request.getId(), "recover:delivery-created-after-completion");
        resolver.touchFlow(
            request.getId(), level.getGameTime(), "tickPending:recover-delivery-after-completion");
        if (Config.DEBUG_LOGGING.getAsBoolean()) {
          TheSettlerXCreate.LOGGER.info(
              "[CreateShop] tickPending: {} recover (deliveryCreated latched after completionSeen, pending={})",
              requestIdLog,
              resolver.getPendingTracker().getPendingCount(request.getId()));
        }
      } else if (!request.hasChildren() && deliveryStarted && !completionSeen) {
        resolver.clearDeliveriesCreated(request.getId());
        int heldPending =
            Math.max(1, resolver.getPendingTracker().getPendingCount(request.getId()));
        requestStateMutatorService.markOrderedWithPendingAtLeastOne(
            resolver, level, request.getId(), heldPending);
        diagnostics.logPendingReasonChange(
            request.getId(), "recover:delivery-created-without-child");
        resolver.touchFlow(
            request.getId(), level.getGameTime(), "tickPending:recover-delivery-created");
        if (Config.DEBUG_LOGGING.getAsBoolean()) {
          TheSettlerXCreate.LOGGER.info(
              "[CreateShop] tickPending: {} recover (deliveryCreated without child, pending={})",
              requestIdLog,
              heldPending);
        }
      } else {
        diagnostics.logPendingReasonChange(request.getId(), "wait:delivery-in-progress");
        resolver.touchFlow(
            request.getId(), level.getGameTime(), "tickPending:delivery-in-progress");
        if (Config.DEBUG_LOGGING.getAsBoolean()) {
          TheSettlerXCreate.LOGGER.info(
              "[CreateShop] tickPending: {} waiting (delivery in progress, topup blocked)",
              requestIdLog);
        }
        return;
      }
    }

    if (!onCooldown && Config.DEBUG_LOGGING.getAsBoolean()) {
      TheSettlerXCreate.LOGGER.info(
          "[CreateShop] tickPending: {} proceed (cooldown cleared, reservedForRequest={})",
          requestIdLog,
          reservedForRequest);
    }
    var pendingDecision =
        pendingStateDecisionService.decide(
            resolver,
            request,
            level,
            deliverable,
            onCooldown,
            reservedForRequest,
            workerWorking,
            requestIdLog);
    if (pendingDecision.shouldSkip()) {
      return;
    }
    int pendingCount = pendingDecision.pendingCount();
    int rackAvailable = resolver.getPlanning().getAvailableFromRacks(tile, deliverable);
    int reservedForDeliverable = pickup.getReservedForDeliverable(deliverable);
    int rackAvailableForRequest =
        Math.max(
            0,
            rackAvailable - Math.max(0, reservedForDeliverable - Math.max(0, reservedForRequest)));
    int reservedSynced =
        reservationSyncService.syncReservationsFromRack(
            resolver,
            tile,
            pickup,
            requestId,
            request.getId(),
            deliverable,
            pendingCount,
            reservedForRequest,
            rackAvailableForRequest,
            level.getGameTime());
    if (reservedSynced > 0) {
      reservedForRequest += reservedSynced;
      reservedForDeliverable += reservedSynced;
      rackAvailableForRequest =
          Math.max(
              0,
              rackAvailable
                  - Math.max(0, reservedForDeliverable - Math.max(0, reservedForRequest)));
    }
    pendingTopupService.handleTopup(
        resolver,
        manager,
        request,
        level,
        tile,
        pickup,
        deliverable,
        workerWorking,
        pendingCount,
        reservedForRequest,
        rackAvailableForRequest,
        requestIdLog);
    var creationResult =
        pendingDeliveryCreationService.process(
            manager,
            request,
            level,
            tile,
            pickup,
            deliverable,
            pendingCount,
            rackAvailableForRequest,
            requestIdLog);
    if (!creationResult.created()) {
      return;
    }
    postCreationUpdateService.apply(
        resolver, manager, request, level, creationResult, requestIdLog);
  }
}
