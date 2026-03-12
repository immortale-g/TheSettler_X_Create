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
}
