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
}
