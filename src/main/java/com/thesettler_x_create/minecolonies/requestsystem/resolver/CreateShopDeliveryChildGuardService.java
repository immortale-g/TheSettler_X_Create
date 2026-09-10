package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.core.colony.requestsystem.management.IStandardRequestManager;
import net.minecraft.world.level.Level;

/**
 * Guards against duplicate or stuck delivery-child requests for a parent.
 *
 * <p>Stale-child detection and forced courier recovery have been removed: once DELIVERY_CREATED is
 * reached, MineColonies owns the delivery. The shop reacts to terminal callbacks rather than
 * polling courier progress. The only remaining active guard is the extra-active-child check which
 * cancels duplicate delivery children for the same parent (a programming-error guard, not a
 * timeout-based heuristic).
 */
final class CreateShopDeliveryChildGuardService {
  /**
   * How long a child token may stay unresolved after an immediate-pickup confirmation before
   * {@link #shouldDropMissingChild} gives up on it.
   */
  private static final long MISSING_CHILD_GRACE_TICKS = 40L;

  private final CreateShopRequestStateMutatorService requestStateMutatorService;

  CreateShopDeliveryChildGuardService(
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
    return now - since >= MISSING_CHILD_GRACE_TICKS;
  }

  void clearTrackedChildrenForParent(
      CreateShopRequestResolver resolver, IStandardRequestManager manager, IToken<?> parentToken) {
    if (parentToken == null) {
      return;
    }
    requestStateMutatorService.clearParentChildrenSnapshot(resolver, parentToken);
  }
}
