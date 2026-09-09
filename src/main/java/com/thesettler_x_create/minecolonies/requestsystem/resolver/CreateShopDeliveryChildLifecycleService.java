package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.core.colony.requestsystem.management.IStandardRequestManager;
import net.minecraft.world.level.Level;

/**
 * Lifecycle helpers for delivery child requests.
 *
 * <p>Stale-child detection and forced courier recovery have been removed: once DELIVERY_CREATED is
 * reached, MineColonies owns the delivery. The shop reacts to terminal callbacks rather than
 * polling courier progress. The only remaining active guard is the extra-active-child check which
 * cancels duplicate delivery children for the same parent (a programming-error guard, not a
 * timeout-based heuristic).
 */
final class CreateShopDeliveryChildLifecycleService {
  private final CreateShopRequestStateMutatorService requestStateMutatorService;

  CreateShopDeliveryChildLifecycleService(
      CreateShopRequestStateMutatorService requestStateMutatorService) {
    this.requestStateMutatorService = requestStateMutatorService;
  }

  /**
   * Returns true if the missing-child grace period has elapsed. Used only for the
   * immediate-pickup-confirmed recovery path.
   */
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

  void clearTrackedChildrenForParent(
      CreateShopRequestResolver resolver, IStandardRequestManager manager, IToken<?> parentToken) {
    if (parentToken == null) {
      return;
    }
    requestStateMutatorService.clearParentChildrenSnapshot(resolver, parentToken);
  }
}
