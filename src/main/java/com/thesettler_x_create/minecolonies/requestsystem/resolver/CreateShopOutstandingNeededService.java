package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.requestable.IDeliverable;
import com.minecolonies.api.colony.requestsystem.requestable.INonExhaustiveDeliverable;
import net.minecraft.world.item.ItemStack;

/**
 * Computes the outstanding required amount for a request after subtracting what was already
 * delivered, what is still reserved for it, and the non-exhaustive leftover.
 */
final class CreateShopOutstandingNeededService {
  int compute(IRequest<?> request, IDeliverable deliverable, int reservedForRequest) {
    if (request == null || deliverable == null) {
      return 0;
    }
    int leftOver =
        deliverable instanceof INonExhaustiveDeliverable nonExhaustive
            ? nonExhaustive.getLeftOver()
            : 0;
    return computeFrom(
        deliverable.getCount(),
        leftOver,
        countAlreadyDelivered(request, deliverable),
        reservedForRequest);
  }

  /**
   * The outstanding arithmetic, split out so it stays testable without a Minecraft bootstrap.
   *
   * <p>{@code alreadyDelivered} is the part that used to be missing: without it the outstanding
   * amount snaps back to the full request count the moment a completed delivery's reservation is
   * consumed, so the resolver re-orders from the Create network and hands the same stack to a
   * courier over and over.
   */
  static int computeFrom(
      int requestedCount, int leftOver, int alreadyDelivered, int reservedForRequest) {
    int needed = requestedCount - leftOver - Math.max(0, alreadyDelivered);
    return Math.max(0, needed - Math.max(0, reservedForRequest));
  }

  /**
   * Sums what MineColonies already recorded as delivered for this request. Every delivery child we
   * create adds its stack via {@code IRequest#addDelivery}, and MineColonies resets that list when
   * a child is cancelled, so the record tracks real hand-offs and survives a reload.
   */
  private int countAlreadyDelivered(IRequest<?> request, IDeliverable deliverable) {
    int delivered = 0;
    try {
      for (ItemStack stack : request.getDeliveries()) {
        if (stack == null || stack.isEmpty() || !deliverable.matches(stack)) {
          continue;
        }
        delivered += stack.getCount();
      }
    } catch (Exception ignored) {
      // Best effort: an unreadable delivery record must not block outstanding computation.
      return 0;
    }
    return Math.max(0, delivered);
  }
}
