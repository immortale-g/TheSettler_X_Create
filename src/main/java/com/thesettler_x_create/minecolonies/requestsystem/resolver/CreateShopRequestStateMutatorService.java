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
    resolver.getParentDeliveryActiveSince().put(parentToken, level == null ? 0L : level.getGameTime());
    resolver.clearStaleRecoveryArm(parentToken);
    if (childToken != null) {
      resolver
          .getDeliveryChildActiveSince()
          .put(childToken, level == null ? 0L : level.getGameTime());
    }
  }

  void closeDeliveryWindow(
      CreateShopRequestResolver resolver, IToken<?> parentToken, IToken<?> childToken) {
    if (resolver == null) {
      return;
    }
    if (childToken != null) {
      resolver.getDeliveryChildActiveSince().remove(childToken);
    }
    if (parentToken != null) {
      resolver.getParentDeliveryActiveSince().remove(parentToken);
      resolver.clearStaleRecoveryArm(parentToken);
      resolver.clearDeliveriesCreated(parentToken);
    }
  }

  boolean armStaleRecoveryIfMissing(
      CreateShopRequestResolver resolver, IToken<?> parentToken, long nowTick) {
    if (resolver == null || parentToken == null) {
      return false;
    }
    return resolver.getParentStaleRecoveryArmedAt().putIfAbsent(parentToken, nowTick) == null;
  }

  void clearStaleRecoveryArm(CreateShopRequestResolver resolver, IToken<?> parentToken) {
    if (resolver == null || parentToken == null) {
      return;
    }
    resolver.getParentStaleRecoveryArmedAt().remove(parentToken);
  }

  Long markParentDeliveryActiveIfAbsent(
      CreateShopRequestResolver resolver, IToken<?> parentToken, long nowTick) {
    if (resolver == null || parentToken == null) {
      return null;
    }
    return resolver.getParentDeliveryActiveSince().putIfAbsent(parentToken, nowTick);
  }

  void clearParentDeliveryActive(CreateShopRequestResolver resolver, IToken<?> parentToken) {
    if (resolver == null || parentToken == null) {
      return;
    }
    resolver.getParentDeliveryActiveSince().remove(parentToken);
  }

  void markChildActive(CreateShopRequestResolver resolver, IToken<?> childToken, long sinceTick) {
    if (resolver == null || childToken == null) {
      return;
    }
    resolver.getDeliveryChildActiveSince().put(childToken, sinceTick);
  }

  void clearChildActive(CreateShopRequestResolver resolver, IToken<?> childToken) {
    if (resolver == null || childToken == null) {
      return;
    }
    resolver.getDeliveryChildActiveSince().remove(childToken);
  }

  void clearMissingChild(CreateShopRequestResolver resolver, IToken<?> childToken) {
    if (resolver == null || childToken == null) {
      return;
    }
    resolver.getMissingChildSince().remove(childToken);
  }

  void setParentChildrenSnapshot(
      CreateShopRequestResolver resolver, IToken<?> parentToken, int childCount, String childrenState) {
    if (resolver == null || parentToken == null) {
      return;
    }
    resolver.getParentLastKnownChildCount().put(parentToken, Math.max(0, childCount));
    resolver.getParentLastKnownChildren().put(parentToken, childrenState == null ? "[]" : childrenState);
  }

  void clearParentChildrenSnapshot(CreateShopRequestResolver resolver, IToken<?> parentToken) {
    if (resolver == null || parentToken == null) {
      return;
    }
    resolver.getParentLastKnownChildCount().remove(parentToken);
    resolver.getParentLastKnownChildren().remove(parentToken);
    resolver.getParentChildDropLastLogTick().remove(parentToken);
  }

  void markParentChildDropLog(CreateShopRequestResolver resolver, IToken<?> parentToken, long nowTick) {
    if (resolver == null || parentToken == null) {
      return;
    }
    resolver.getParentChildDropLastLogTick().put(parentToken, nowTick);
  }
}
