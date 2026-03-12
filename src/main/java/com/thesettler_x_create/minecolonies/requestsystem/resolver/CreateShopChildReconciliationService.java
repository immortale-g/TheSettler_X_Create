package com.thesettler_x_create.minecolonies.requestsystem.resolver;

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
import java.util.function.Function;
import net.minecraft.world.level.Level;

/** Reconciles parent child-request lifecycle for pending Create Shop requests. */
final class CreateShopChildReconciliationService {
  private final CreateShopDeliveryManager deliveryManager;
  private final CreateShopDeliveryChildLifecycleService deliveryChildLifecycleService;
  private final CreateShopDeliveryChildRecoveryService deliveryChildRecoveryService;
  private final CreateShopDeliveryRootCauseSnapshotService deliveryRootCauseSnapshotService;

  CreateShopChildReconciliationService(
      CreateShopDeliveryManager deliveryManager,
      CreateShopDeliveryChildLifecycleService deliveryChildLifecycleService,
      CreateShopDeliveryChildRecoveryService deliveryChildRecoveryService,
      CreateShopDeliveryRootCauseSnapshotService deliveryRootCauseSnapshotService) {
    this.deliveryManager = deliveryManager;
    this.deliveryChildLifecycleService = deliveryChildLifecycleService;
    this.deliveryChildRecoveryService = deliveryChildRecoveryService;
    this.deliveryRootCauseSnapshotService = deliveryRootCauseSnapshotService;
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
          IRequest<?> child = requestHandler.getRequest(childToken);
          if (child == null) {
            if (resolver.shouldDropMissingChild(level, childToken)) {
              request.removeChild(childToken);
              resolver.getMissingChildSince().remove(childToken);
              missing++;
              if (Config.DEBUG_LOGGING.getAsBoolean()) {
                TheSettlerXCreate.LOGGER.info(
                    "[CreateShop] tickPending: {} child {} missing -> dropped after grace",
                    requestIdLog,
                    childToken);
              }
            } else {
              hasActiveChildren = true;
              if (Config.DEBUG_LOGGING.getAsBoolean()) {
                TheSettlerXCreate.LOGGER.info(
                    "[CreateShop] tickPending: {} child {} missing -> keep (fail-open) grace",
                    requestIdLog,
                    childToken);
              }
            }
            continue;
          }
          resolver.getMissingChildSince().remove(childToken);
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
              resolver.getDeliveryChildActiveSince().remove(childToken);
              resolver.getMissingChildSince().remove(childToken);
              resolver.clearStaleRecoveryArm(request.getId());
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
              boolean enqueued = deliveryManager.tryEnqueueDelivery(standardManager, childToken);
              if (Config.DEBUG_LOGGING.getAsBoolean()) {
                TheSettlerXCreate.LOGGER.info(
                    "[CreateShop] tickPending: {} child {} unassigned delivery -> enqueue={}",
                    requestIdLog,
                    childToken,
                    enqueued ? "ok" : "none");
              }
            }
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
            if (deliveryChildLifecycleService.isStaleDeliveryChild(
                resolver, level, request.getId(), childToken, childState)) {
              if (!deliveryChildLifecycleService.isStaleRecoveryArmed(
                  resolver, level, standardManager, request.getId())) {
                hasActiveChildren = true;
                continue;
              }
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
                      "stale-child-recovery",
                      "[CreateShop] stale delivery-child recovery parent={} child={} stateUpdated={} item={} count={}");
              if (recovered) {
                missing++;
                continue;
              }
            } else {
              deliveryChildLifecycleService.clearStaleRecoveryArm(resolver, request.getId());
            }
            deliveryRootCauseSnapshotService.logSnapshot(
                resolver, standardManager, level, request, child, childToken, childAssigned);
            hasActiveChildren = true;
          } else {
            resolver.getDeliveryChildActiveSince().remove(childToken);
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
          if (resolver.shouldDropMissingChild(level, childToken)) {
            request.removeChild(childToken);
            resolver.getMissingChildSince().remove(childToken);
            missing++;
            if (Config.DEBUG_LOGGING.getAsBoolean()) {
              TheSettlerXCreate.LOGGER.info(
                  "[CreateShop] tickPending: {} child {} lookup failed -> dropped after grace: {}",
                  requestIdLog,
                  childToken,
                  ex.getMessage() == null ? "<null>" : ex.getMessage());
            }
          } else {
            hasActiveChildren = true;
            if (Config.DEBUG_LOGGING.getAsBoolean()) {
              TheSettlerXCreate.LOGGER.info(
                  "[CreateShop] tickPending: {} child {} lookup failed -> keep (fail-open): {}",
                  requestIdLog,
                  childToken,
                  ex.getMessage() == null ? "<null>" : ex.getMessage());
            }
          }
        }
      }
    }
    return new ChildReconcileResult(
        hasActiveChildren, missing, duplicateChildrenRemoved, children.isEmpty(), children.size());
  }

  record ChildReconcileResult(
      boolean hasActiveChildren,
      int missing,
      int duplicateChildrenRemoved,
      boolean childrenEmpty,
      int childrenCount) {}
}


