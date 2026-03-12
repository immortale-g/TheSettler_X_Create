package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import com.minecolonies.api.colony.requestsystem.token.IToken;
import net.minecraft.world.level.Level;

/** Centralizes request pending/cooldown state mutations to avoid split write paths. */
final class CreateShopRequestStateMutatorService {
  void markOrderedWithPending(
      CreateShopRequestResolver resolver, Level level, IToken<?> requestToken, int pendingCount) {
    if (resolver == null || requestToken == null) {
      return;
    }
    if (level != null) {
      resolver.getCooldown().markRequestOrdered(level, requestToken);
    }
    resolver.getPendingTracker().setPendingCount(requestToken, Math.max(0, pendingCount));
  }

  void markOrderedWithPendingAtLeastOne(
      CreateShopRequestResolver resolver, Level level, IToken<?> requestToken, int pendingCount) {
    markOrderedWithPending(resolver, level, requestToken, Math.max(1, pendingCount));
  }

  void clearOrderedAndPending(CreateShopRequestResolver resolver, IToken<?> requestToken) {
    if (resolver == null || requestToken == null) {
      return;
    }
    resolver.getCooldown().clearRequestCooldown(requestToken);
    resolver.getPendingTracker().remove(requestToken);
  }

  void openDeliveryWindow(
      CreateShopRequestResolver resolver,
      Level level,
      IToken<?> parentToken,
      IToken<?> childToken,
      int pendingCount) {
    if (resolver == null || parentToken == null) {
      return;
    }
    markOrderedWithPendingAtLeastOne(resolver, level, parentToken, Math.max(1, pendingCount));
    resolver.markParentDeliveryActiveIfAbsent(parentToken, level == null ? 0L : level.getGameTime());
    clearStaleRecoveryArm(resolver, parentToken);
    if (childToken != null) {
      resolver.markChildActive(childToken, level == null ? 0L : level.getGameTime());
    }
  }

  void closeDeliveryWindow(
      CreateShopRequestResolver resolver, IToken<?> parentToken, IToken<?> childToken) {
    if (resolver == null) {
      return;
    }
    if (childToken != null) {
      resolver.clearChildActive(childToken);
    }
    if (parentToken != null) {
      resolver.clearParentDeliveryActive(parentToken);
      clearStaleRecoveryArm(resolver, parentToken);
      resolver.clearDeliveriesCreated(parentToken);
    }
  }

  boolean armStaleRecoveryIfMissing(
      CreateShopRequestResolver resolver, IToken<?> parentToken, long nowTick) {
    if (resolver == null || parentToken == null) {
      return false;
    }
    return resolver.armStaleRecoveryIfMissing(parentToken, nowTick);
  }

  void clearStaleRecoveryArm(CreateShopRequestResolver resolver, IToken<?> parentToken) {
    if (resolver == null || parentToken == null) {
      return;
    }
    resolver.clearParentStaleRecoveryArm(parentToken);
  }

  Long markParentDeliveryActiveIfAbsent(
      CreateShopRequestResolver resolver, IToken<?> parentToken, long nowTick) {
    if (resolver == null || parentToken == null) {
      return null;
    }
    return resolver.markParentDeliveryActiveIfAbsent(parentToken, nowTick);
  }

  void clearParentDeliveryActive(CreateShopRequestResolver resolver, IToken<?> parentToken) {
    if (resolver == null || parentToken == null) {
      return;
    }
    resolver.clearParentDeliveryActive(parentToken);
  }

  void markChildActive(CreateShopRequestResolver resolver, IToken<?> childToken, long sinceTick) {
    if (resolver == null || childToken == null) {
      return;
    }
    resolver.markChildActive(childToken, sinceTick);
  }

  void clearChildActive(CreateShopRequestResolver resolver, IToken<?> childToken) {
    if (resolver == null || childToken == null) {
      return;
    }
    resolver.clearChildActive(childToken);
  }

  void clearMissingChild(CreateShopRequestResolver resolver, IToken<?> childToken) {
    if (resolver == null || childToken == null) {
      return;
    }
    resolver.clearMissingChildSince(childToken);
  }

  void setParentChildrenSnapshot(
      CreateShopRequestResolver resolver, IToken<?> parentToken, int childCount, String childrenState) {
    if (resolver == null || parentToken == null) {
      return;
    }
    resolver.setParentChildrenSnapshot(parentToken, childCount, childrenState);
  }

  void clearParentChildrenSnapshot(CreateShopRequestResolver resolver, IToken<?> parentToken) {
    if (resolver == null || parentToken == null) {
      return;
    }
    resolver.clearParentChildrenSnapshot(parentToken);
  }

  void markParentChildDropLog(CreateShopRequestResolver resolver, IToken<?> parentToken, long nowTick) {
    if (resolver == null || parentToken == null) {
      return;
    }
    resolver.markParentChildDropLastLogTick(parentToken, nowTick);
  }

  void clearPendingTokenState(
      CreateShopRequestResolver resolver, IToken<?> token, boolean clearFlowState) {
    if (resolver == null || token == null) {
      return;
    }
    clearOrderedAndPending(resolver, token);
    resolver.clearDeliveriesCreated(token);
    resolver.clearParentDeliveryActive(token);
    resolver.clearParentStaleRecoveryArm(token);
    resolver.clearParentChildrenSnapshot(token);
    resolver.clearChildActive(token);
    resolver.clearMissingChildSince(token);
    resolver.clearRootCauseTracking(token);
    resolver.clearRetryingReassignAttempt(token);
    if (clearFlowState) {
      resolver.getFlowStateMachine().remove(token);
    }
  }
}
