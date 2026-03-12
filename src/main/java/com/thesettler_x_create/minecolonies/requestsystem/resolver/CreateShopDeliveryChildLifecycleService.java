package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.request.RequestState;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.core.colony.requestsystem.management.IStandardRequestManager;
import net.minecraft.world.level.Level;

/** Owns stale-arm/timeout and tracked-child cleanup lifecycle for delivery children. */
final class CreateShopDeliveryChildLifecycleService {
  boolean isStaleRecoveryArmed(
      CreateShopRequestResolver resolver,
      Level level,
      IStandardRequestManager manager,
      IToken<?> parentToken) {
    if (level == null || manager == null || parentToken == null) {
      return false;
    }
    long now = level.getGameTime();
    Long armedAt = resolver.getParentStaleRecoveryArmedAtForOps().get(parentToken);
    if (armedAt == null) {
      resolver.getParentStaleRecoveryArmedAtForOps().put(parentToken, now);
      resolver.getRecheckForOps().scheduleParentChildRecheck(manager, parentToken);
      return false;
    }
    long staleRecheckDelay = 20L;
    if (now - armedAt < staleRecheckDelay) {
      return false;
    }
    return true;
  }

  void clearStaleRecoveryArm(CreateShopRequestResolver resolver, IToken<?> parentToken) {
    if (parentToken == null) {
      return;
    }
    resolver.getParentStaleRecoveryArmedAtForOps().remove(parentToken);
  }

  boolean isStaleDeliveryChild(
      CreateShopRequestResolver resolver,
      Level level,
      IToken<?> parentToken,
      IToken<?> childToken,
      RequestState state) {
    if (level == null || parentToken == null || childToken == null || state == null) {
      return false;
    }
    boolean activeState =
        state == RequestState.CREATED || state == RequestState.ASSIGNED || state == RequestState.IN_PROGRESS;
    if (!activeState) {
      resolver.getDeliveryChildActiveSinceForOps().remove(childToken);
      return false;
    }
    long now = level.getGameTime();
    Long since = resolver.getParentDeliveryActiveSinceForOps().putIfAbsent(parentToken, now);
    if (since == null) {
      resolver.getDeliveryChildActiveSinceForOps().put(childToken, now);
      return false;
    }
    resolver.getDeliveryChildActiveSinceForOps().put(childToken, since);
    long timeout =
        Math.max(
            CreateShopRequestResolver.getDeliveryChildStaleTimeoutFloorTicksForOps(),
            resolver.getInflightTimeoutTicksSafeForOps());
    return now - since >= timeout;
  }

  void clearTrackedChildrenForParent(
      CreateShopRequestResolver resolver, IStandardRequestManager manager, IToken<?> parentToken) {
    if (manager == null || parentToken == null) {
      return;
    }
    resolver.getParentDeliveryActiveSinceForOps().remove(parentToken);
    clearStaleRecoveryArm(resolver, parentToken);
    resolver.getParentLastKnownChildCountForOps().remove(parentToken);
    resolver.getParentLastKnownChildrenForOps().remove(parentToken);
    resolver.getParentChildDropLastLogTickForOps().remove(parentToken);
    if (resolver.getDeliveryChildActiveSinceForOps().isEmpty()) {
      return;
    }
    var handler = manager.getRequestHandler();
    if (handler == null) {
      return;
    }
    for (IToken<?> childToken :
        java.util.List.copyOf(resolver.getDeliveryChildActiveSinceForOps().keySet())) {
      try {
        IRequest<?> child = handler.getRequest(childToken);
        IToken<?> parent = child == null ? null : child.getParent();
        if (parentToken.equals(parent)) {
          resolver.getDeliveryChildActiveSinceForOps().remove(childToken);
        }
      } catch (Exception ignored) {
        resolver.getDeliveryChildActiveSinceForOps().remove(childToken);
      }
    }
  }
}
