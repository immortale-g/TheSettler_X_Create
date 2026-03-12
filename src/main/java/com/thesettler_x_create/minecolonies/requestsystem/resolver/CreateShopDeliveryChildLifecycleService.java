package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.request.RequestState;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.core.colony.requestsystem.management.IStandardRequestManager;
import net.minecraft.world.level.Level;

/** Owns stale-arm/timeout and tracked-child cleanup lifecycle for delivery children. */
final class CreateShopDeliveryChildLifecycleService {
  private final CreateShopRequestStateMutatorService requestStateMutatorService;

  CreateShopDeliveryChildLifecycleService(
      CreateShopRequestStateMutatorService requestStateMutatorService) {
    this.requestStateMutatorService = requestStateMutatorService;
  }

  boolean isStaleRecoveryArmed(
      CreateShopRequestResolver resolver,
      Level level,
      IStandardRequestManager manager,
      IToken<?> parentToken) {
    if (level == null || manager == null || parentToken == null) {
      return false;
    }
    long now = level.getGameTime();
    Long armedAt = resolver.getParentStaleRecoveryArmedAt(parentToken);
    if (armedAt == null) {
      requestStateMutatorService.armStaleRecoveryIfMissing(resolver, parentToken, now);
      resolver.getRecheck().scheduleParentChildRecheck(manager, parentToken);
      return false;
    }
    long staleRecheckDelay = 20L;
    if (now - armedAt < staleRecheckDelay) {
      return false;
    }
    return true;
  }

  boolean shouldDropMissingChild(
      CreateShopRequestResolver resolver, Level level, IToken<?> childToken) {
    if (resolver == null || level == null || childToken == null) {
      return false;
    }
    long now = level.getGameTime();
    Long since = resolver.markMissingChildIfAbsent(childToken, now);
    if (since == null) {
      return false;
    }
    return now - since >= 40L;
  }

  void clearStaleRecoveryArm(CreateShopRequestResolver resolver, IToken<?> parentToken) {
    requestStateMutatorService.clearStaleRecoveryArm(resolver, parentToken);
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
      requestStateMutatorService.clearChildActive(resolver, childToken);
      return false;
    }
    long now = level.getGameTime();
    Long since = requestStateMutatorService.markParentDeliveryActiveIfAbsent(resolver, parentToken, now);
    if (since == null) {
      requestStateMutatorService.markChildActive(resolver, childToken, now);
      return false;
    }
    requestStateMutatorService.markChildActive(resolver, childToken, since);
    long timeout =
        Math.max(
            CreateShopRequestResolver.getDeliveryChildStaleTimeoutFloorTicks(),
            resolver.getInflightTimeoutTicksSafe());
    return now - since >= timeout;
  }

  void clearTrackedChildrenForParent(
      CreateShopRequestResolver resolver, IStandardRequestManager manager, IToken<?> parentToken) {
    if (manager == null || parentToken == null) {
      return;
    }
    requestStateMutatorService.clearParentDeliveryActive(resolver, parentToken);
    requestStateMutatorService.clearStaleRecoveryArm(resolver, parentToken);
    requestStateMutatorService.clearParentChildrenSnapshot(resolver, parentToken);
    if (!resolver.hasAnyActiveChild()) {
      return;
    }
    var handler = manager.getRequestHandler();
    if (handler == null) {
      return;
    }
    for (IToken<?> childToken : resolver.getActiveChildTokensSnapshot()) {
      try {
        IRequest<?> child = handler.getRequest(childToken);
        IToken<?> parent = child == null ? null : child.getParent();
        if (parentToken.equals(parent)) {
          requestStateMutatorService.clearChildActive(resolver, childToken);
        }
      } catch (Exception ignored) {
        requestStateMutatorService.clearChildActive(resolver, childToken);
      }
    }
  }
}


