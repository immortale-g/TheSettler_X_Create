package com.thesettler_x_create.create;

import com.minecolonies.api.colony.requestsystem.requestable.IDeliverable;
import java.util.List;
import net.minecraft.world.item.ItemStack;

/** Abstraction for querying and ordering items from a Create stock network. */
public interface ICreateNetworkFacade {
  /** Returns total matching items available in the stock network. */
  int getAvailable(IDeliverable deliverable);

  /** Returns available count for a specific stack signature. */
  int getAvailable(ItemStack stack);

  /** Returns a snapshot list of available stacks in the network. */
  List<ItemStack> getAvailableStacks();

  /** Returns an extracted stack preview (no network mutation yet). */
  ItemStack extract(ItemStack stack, int amount, boolean simulate);

  /** Plans the network order stacks needed to satisfy a deliverable amount. */
  List<ItemStack> planItems(IDeliverable deliverable, int amount);

  /**
   * Broadcasts a stock network order and returns the ordered stacks.
   *
   * @param requesterName display name used for inflight tracking/notifications
   */
  List<ItemStack> requestItems(IDeliverable deliverable, int amount, String requesterName);

  /** Broadcasts explicit stack orders and returns the normalized ordered stacks. */
  List<ItemStack> requestStacks(List<ItemStack> requestedStacks, String requesterName);
}
