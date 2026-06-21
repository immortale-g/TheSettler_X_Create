package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import com.minecolonies.api.colony.requestsystem.location.ILocation;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.request.RequestState;
import com.minecolonies.api.colony.requestsystem.requestable.deliveryman.Delivery;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.core.colony.requestsystem.management.IStandardRequestManager;
import com.thesettler_x_create.Config;
import com.thesettler_x_create.TheSettlerXCreate;
import com.thesettler_x_create.blockentity.CreateShopBlockEntity;
import com.thesettler_x_create.minecolonies.building.BuildingCreateShop;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/** Reconciles parent child-request lifecycle for pending Create Shop requests. */
final class CreateShopChildReconciliationService {
  private final CreateShopDeliveryManager deliveryManager;
  private final CreateShopDeliveryChildRecoveryService deliveryChildRecoveryService;
  private final CreateShopDeliveryRootCauseSnapshotService deliveryRootCauseSnapshotService;
  private final CreateShopRequestStateMutatorService requestStateMutatorService;

  CreateShopChildReconciliationService(
      CreateShopDeliveryManager deliveryManager,
      CreateShopDeliveryChildRecoveryService deliveryChildRecoveryService,
      CreateShopDeliveryRootCauseSnapshotService deliveryRootCauseSnapshotService,
      CreateShopRequestStateMutatorService requestStateMutatorService) {
    this.deliveryManager = deliveryManager;
    this.deliveryChildRecoveryService = deliveryChildRecoveryService;
    this.deliveryRootCauseSnapshotService = deliveryRootCauseSnapshotService;
    this.requestStateMutatorService = requestStateMutatorService;
  }

  ChildReconcileResult reconcile(
      CreateShopRequestResolver resolver,
      IStandardRequestManager standardManager,
      Level level,
      IRequest<?> request,
      com.minecolonies.api.colony.requestsystem.management.IRequestHandler requestHandler,
      Function<IToken<?>, IToken<?>> assignmentLookup,
      BuildingCreateShop shop,
      CreateShopBlockEntity pickup,
      String requestIdLog) {
    Collection<IToken<?>> children = Objects.requireNonNull(request.getChildren(), "children");
    int missing = 0;
    int duplicateChildrenRemoved = 0;
    boolean hasActiveChildren = false;
    boolean recoveredParent = false;
    IToken<?> activeLocalDeliveryChild = null;
    if (!children.isEmpty()) {
      java.util.Set<IToken<?>> seenChildren = new HashSet<>();
      for (IToken<?> childToken : List.copyOf(children)) {
        if (!seenChildren.add(childToken)) {
          request.removeChild(childToken);
          duplicateChildrenRemoved++;
          if (Config.DEBUG_LOGGING.getAsBoolean()) {
            TheSettlerXCreate.LOGGER.info(
                "[CreateShop] tickPending: {} child {} duplicate -> removed",
                requestIdLog,
                childToken);
          }
          continue;
        }
        try {
          IRequest<?> child = lookupChildRequest(standardManager, requestHandler, childToken);
          if (child == null) {
            long nowTick = level == null ? 0L : level.getGameTime();
            resolver.markMissingChildIfAbsent(childToken, nowTick);
            resolver.observeDeliveryChildMissing(
                level, request.getId(), childToken, "poll-missing", "handler lookup returned null");
            hasActiveChildren = true;
            missing++;
            if (Config.DEBUG_LOGGING.getAsBoolean()) {
              TheSettlerXCreate.LOGGER.info(
                  "[CreateShop] tickPending: {} child {} missing -> hold (no drop without terminal proof)",
                  requestIdLog,
                  childToken);
            }
            if (tryImmediateMissingAfterPickupRecovery(
                resolver, standardManager, level, request, childToken, requestIdLog)) {
              recoveredParent = true;
              break;
            }
            continue;
          }
          requestStateMutatorService.clearMissingChild(resolver, childToken);
          if (child.getRequest() instanceof Delivery) {
            RequestState childState = child.getState();
            boolean terminalChild =
                childState == RequestState.COMPLETED
                    || childState == RequestState.CANCELLED
                    || childState == RequestState.FAILED
                    || childState == RequestState.RESOLVED
                    || childState == RequestState.RECEIVED;
            if (terminalChild) {
              request.removeChild(childToken);
              requestStateMutatorService.clearMissingChild(resolver, childToken);
              requestStateMutatorService.completeDeliveryWindow(
                  resolver, request.getId(), childToken);
              resolver.markParentChildCompletedSeen(
                  request.getId(), level == null ? 0L : level.getGameTime());
              continue;
            }
            if (!CreateShopDeliveryOriginMatcher.isLocalShopDeliveryChild(child, shop, pickup)) {
              hasActiveChildren = true;
              if (Config.DEBUG_LOGGING.getAsBoolean()) {
                TheSettlerXCreate.LOGGER.info(
                    "[CreateShop] tickPending: {} child {} skip (non-local delivery child)",
                    requestIdLog,
                    childToken);
              }
              continue;
            }
            IToken<?> childAssigned = assignmentLookup.apply(childToken);
            if (childAssigned == null) {
              if (child.getState() == RequestState.CREATED) {
                try {
                  standardManager.assignRequest(childToken);
                } catch (Exception ignored) {
                  // Best-effort kick so native delivery resolver can pick up CREATED children.
                }
                childAssigned = assignmentLookup.apply(childToken);
                if (Config.DEBUG_LOGGING.getAsBoolean()) {
                  TheSettlerXCreate.LOGGER.info(
                      "[CreateShop] tickPending: {} child {} CREATED assignKick={}",
                      requestIdLog,
                      childToken,
                      childAssigned == null ? "none" : "ok");
                }
              }
            }
            if (childAssigned == null) {
              boolean assigned = deliveryManager.assignDeliveryRequest(standardManager, childToken);
              boolean queued = deliveryManager.isQueuedInWarehouse(standardManager, childToken);
              if (Config.DEBUG_LOGGING.getAsBoolean()) {
                TheSettlerXCreate.LOGGER.info(
                    "[CreateShop] tickPending: {} child {} unassigned delivery -> assign={} queue={}",
                    requestIdLog,
                    childToken,
                    assigned ? "ok" : "none",
                    queued ? "present" : "missing");
              }
            }
            resolver.observeDeliveryChildLifecycle(
                standardManager, level, request.getId(), childToken, child, childAssigned, "poll");
            if (activeLocalDeliveryChild != null && !activeLocalDeliveryChild.equals(childToken)) {
              boolean recovered =
                  deliveryChildRecoveryService.recover(
                      resolver,
                      standardManager,
                      level,
                      request,
                      childToken,
                      child,
                      shop,
                      pickup,
                      "extra-active-child-recovery",
                      "[CreateShop] extra active delivery-child recovery parent={} child={} stateUpdated={} item={} count={}");
              if (recovered) {
                missing++;
                continue;
              }
            } else {
              activeLocalDeliveryChild = childToken;
            }
            deliveryRootCauseSnapshotService.logSnapshot(
                resolver, standardManager, level, request, child, childToken, childAssigned);
            hasActiveChildren = true;
          }
          if (Config.DEBUG_LOGGING.getAsBoolean()) {
            String childType = child.getRequest().getClass().getName();
            String childState = child.getState().toString();
            TheSettlerXCreate.LOGGER.info(
                "[CreateShop] tickPending: {} child {} type={} state={}",
                requestIdLog,
                childToken,
                childType,
                childState);
          }
        } catch (Exception ex) {
          long nowTick = level == null ? 0L : level.getGameTime();
          resolver.markMissingChildIfAbsent(childToken, nowTick);
          resolver.observeDeliveryChildMissing(
              level,
              request.getId(),
              childToken,
              "poll-exception",
              ex.getMessage() == null ? "<null>" : ex.getMessage());
          hasActiveChildren = true;
          missing++;
          if (Config.DEBUG_LOGGING.getAsBoolean()) {
            TheSettlerXCreate.LOGGER.info(
                "[CreateShop] tickPending: {} child {} lookup failed -> hold (no drop): {}",
                requestIdLog,
                childToken,
                ex.getMessage() == null ? "<null>" : ex.getMessage());
          }
          if (tryImmediateMissingAfterPickupRecovery(
              resolver, standardManager, level, request, childToken, requestIdLog)) {
            recoveredParent = true;
            break;
          }
        }
      }
    }
    return new ChildReconcileResult(
        hasActiveChildren,
        missing,
        duplicateChildrenRemoved,
        children.isEmpty(),
        children.size(),
        recoveredParent);
  }

  record ChildReconcileResult(
      boolean hasActiveChildren,
      int missing,
      int duplicateChildrenRemoved,
      boolean childrenEmpty,
      int childrenCount,
      boolean recoveredParent) {}

  private IRequest<?> lookupChildRequest(
      IStandardRequestManager standardManager,
      com.minecolonies.api.colony.requestsystem.management.IRequestHandler requestHandler,
      IToken<?> childToken) {
    IRequest<?> child = null;
    try {
      child = requestHandler.getRequest(childToken);
    } catch (Exception ignored) {
      child = null;
    }
    if (child != null || standardManager == null) {
      return child;
    }
    try {
      return standardManager.getRequestForToken(childToken);
    } catch (Exception ignored) {
      return null;
    }
  }

  private boolean tryImmediateMissingAfterPickupRecovery(
      CreateShopRequestResolver resolver,
      IStandardRequestManager standardManager,
      Level level,
      IRequest<?> parentRequest,
      IToken<?> childToken,
      String requestIdLog) {
    if (resolver == null
        || standardManager == null
        || parentRequest == null
        || childToken == null
        || level == null) {
      return false;
    }
    CreateShopDeliveryChildLedgerEntry ledger = resolver.getDeliveryChildLedgerEntry(childToken);
    if (ledger == null || ledger.pickupConfirmedAtTick < 0L || ledger.terminalSeenAtTick >= 0L) {
      return false;
    }
    if (!parentRequest.getId().equals(ledger.parentToken)) {
      return false;
    }
    BuildingCreateShop shop = resolver.getShop(standardManager);
    CreateShopBlockEntity pickup = shop == null ? null : shop.getPickupBlockEntity();
    if (pickup != null) {
      UUID parentRequestId = CreateShopRequestResolver.toRequestId(parentRequest.getId());
      int reservedForRequest = pickup.getReservedForRequest(parentRequestId);
      if (reservedForRequest > 0) {
        if (resolver.isDebugLoggingEnabled()) {
          TheSettlerXCreate.LOGGER.info(
              "[CreateShop] tickPending: {} missing child {} held by reservation={} -> no orphan recovery",
              requestIdLog,
              childToken,
              reservedForRequest);
        }
        return false;
      }
    }
    parentRequest.removeChild(childToken);
    resolver.markParentChildCompletedSeen(parentRequest.getId(), level.getGameTime());
    resolver.observeDeliveryChildCallbackTerminal(
        level, parentRequest.getId(), childToken, "immediate-missing-after-pickup");
    requestStateMutatorService.finalizeOrphanDeliveryChild(
        resolver, standardManager, childToken, "immediate-missing-after-pickup");
    requestStateMutatorService.completeDeliveryWindow(resolver, parentRequest.getId(), childToken);
    try {
      standardManager.updateRequestState(parentRequest.getId(), RequestState.RESOLVED);
    } catch (Exception ignored) {
      // Best effort; local state cleanup still prevents resolver-side reorders.
    }
    requestStateMutatorService.clearPendingTokenState(
        resolver, standardManager, parentRequest.getId(), true);
    if (resolver.isDebugLoggingEnabled()) {
      TheSettlerXCreate.LOGGER.info(
          "[CreateShop] tickPending: {} immediate recovery (missing+pickupConfirmed) parent={} child={} -> resolved",
          requestIdLog,
          parentRequest.getId(),
          childToken);
    }
    return true;
  }
}

/** Utility matcher for deciding whether deliveries originate from the local Create Shop. */
final class CreateShopDeliveryOriginMatcher {
  private CreateShopDeliveryOriginMatcher() {}

  static boolean isLocalShopDeliveryChild(
      IRequest<?> childRequest, BuildingCreateShop shop, CreateShopBlockEntity pickup) {
    if (childRequest == null || shop == null || pickup == null) {
      return false;
    }
    if (!(childRequest.getRequest() instanceof Delivery delivery)) {
      return false;
    }
    ILocation start = delivery.getStart();
    net.minecraft.world.level.Level level = pickup.getLevel();
    if (start == null || level == null || start.getDimension() == null) {
      return false;
    }
    if (!level.dimension().equals(start.getDimension())) {
      return false;
    }
    BlockPos startPos = start.getInDimensionLocation();
    if (startPos == null) {
      return false;
    }
    if (pickup.getBlockPos().equals(startPos)) {
      return true;
    }
    return shop.hasContainerPosition(startPos);
  }

  static boolean isDeliveryFromLocalShopStart(
      Delivery delivery, BuildingCreateShop shop, CreateShopBlockEntity pickup) {
    if (delivery == null || pickup == null || pickup.getLevel() == null) {
      return false;
    }
    ILocation start = delivery.getStart();
    if (start == null || start.getDimension() == null) {
      return false;
    }
    if (!pickup.getLevel().dimension().equals(start.getDimension())) {
      return false;
    }
    BlockPos startPos = start.getInDimensionLocation();
    if (startPos == null) {
      return false;
    }
    if (pickup.getBlockPos().equals(startPos)) {
      return true;
    }
    return shop != null && shop.hasContainerPosition(startPos);
  }
}
