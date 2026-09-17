package com.thesettler_x_create.create;

import net.minecraft.world.item.ItemStack;

/**
 * How much of what the stock network holds a colony request may have. The shop's own flows ask
 * without a policy; only what the colony draws goes through one, so a minimum kept for the player's
 * own production cannot be eaten by a citizen's request.
 */
@FunctionalInterface
public interface ShopSupplyPolicy {
  /** Everything in the network counts. Used by every flow that is not the colony drawing stock. */
  ShopSupplyPolicy ALLOW_EVERYTHING = (kind, inNetwork) -> inNetwork;

  /**
   * @param kind one item kind as the network holds it
   * @param inNetwork how many of it the network holds
   * @return how many of them the colony may have, between 0 and {@code inNetwork}
   */
  int allowanceFor(ItemStack kind, int inNetwork);
}
