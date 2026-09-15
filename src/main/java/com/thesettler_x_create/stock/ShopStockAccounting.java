package com.thesettler_x_create.stock;

import java.util.ArrayList;
import java.util.List;

/**
 * Stock arithmetic of the Create Shop in one place: how much is free in the racks, what can still
 * be reserved, how much has to be ordered from the Create network.
 *
 * <p>These formulas used to be written out, slightly differently, in the resolver services, the
 * shop tile and the virtual network item handler. They only work on counts, so they are tested
 * directly in {@code ShopStockAccountingTest}. Each method keeps the exact clamping of the code it
 * replaced; the results of the count methods never go below zero for non-negative requests.
 */
public final class ShopStockAccounting {
  private ShopStockAccounting() {}

  /** What other requests have reserved of an item, given the total and this request's share. */
  public static int reservedForOthers(int reservedForItem, int reservedForRequest) {
    return Math.max(0, reservedForItem - Math.max(0, reservedForRequest));
  }

  /** Rack stock a request may use: everything not reserved by other requests. */
  public static int usableRackStock(int rackStock, int reservedForOthers) {
    return Math.max(0, rackStock - Math.max(0, reservedForOthers));
  }

  /** Everything the shop can hand out for a request: network, usable rack and pickup stock. */
  public static int totalAvailable(int networkStock, int usableRackStock, int pickupStock) {
    return Math.max(0, networkStock + usableRackStock + pickupStock);
  }

  /**
   * Whether the shop can take a request: enough for the full need, or at least the minimum the
   * requester accepts.
   */
  public static boolean canCover(int available, int needed, int minimumCount) {
    return available >= minimumCount || available >= needed;
  }

  /**
   * How much of the rack stock can be reserved for a request right now: the unreserved rack stock,
   * capped at the part of the pending amount that is not reserved yet.
   */
  public static int reservableFromRack(
      int rackStock, int reservedForItem, int pendingCount, int reservedForRequest) {
    int rackUnreserved = Math.max(0, rackStock - Math.max(0, reservedForItem));
    int missingReservation = Math.max(0, pendingCount - Math.max(0, reservedForRequest));
    return Math.min(rackUnreserved, missingReservation);
  }

  /** What is still missing after this request's reservation and its usable rack stock. */
  public static int topupNeed(int pendingCount, int reservedForRequest, int usableRackStock) {
    return Math.max(
        0, pendingCount - Math.max(0, reservedForRequest) - Math.max(0, usableRackStock));
  }

  /** What still has to be ordered once the orders already on their way are counted. */
  public static int networkOrderAmount(int need, int inflightRemaining) {
    return Math.max(0, need - Math.max(0, inflightRemaining));
  }

  /**
   * The pending amount tracked for a request: its outstanding need, but never less than what it
   * already has reserved.
   */
  public static int pendingCount(int reservedForRequest, int outstanding) {
    return Math.max(0, Math.max(reservedForRequest, outstanding));
  }

  /** Stock left after reservations. */
  public static int unreservedStock(int stock, int reserved) {
    return Math.max(0, stock - reserved);
  }

  /**
   * Splits an amount into delivery sizes of at most one stack each, the way MineColonies' warehouse
   * hands out one delivery per stack. A delivery stack larger than the item's stack size would not
   * survive being saved.
   *
   * @return the chunk sizes in order; empty when there is nothing to deliver
   */
  public static List<Integer> deliveryChunks(int amount, int maxStackSize) {
    List<Integer> chunks = new ArrayList<>();
    int perChunk = Math.max(1, maxStackSize);
    for (int remaining = amount; remaining > 0; remaining -= perChunk) {
      chunks.add(Math.min(remaining, perChunk));
    }
    return chunks;
  }

  /**
   * What a warehouse pickup must leave in the shop of one item kind: everything in the racks (the
   * shopkeeper decides what leaves them) and at least everything reserved, even when part of it
   * sits in the hut buffer.
   */
  public static int pickupKeepAmount(int rackStock, int reserved) {
    return Math.max(0, Math.max(rackStock, reserved));
  }

  /**
   * How much of one slot a pickup may take. Slots are visited in order and {@code alreadyKept} is
   * what earlier slots of the same item kind already kept.
   */
  public static int pickupTakeable(int slotCount, int keepAmount, int alreadyKept) {
    int keepHere = Math.min(Math.max(0, slotCount), Math.max(0, keepAmount - alreadyKept));
    return Math.max(0, slotCount - keepHere);
  }

  /**
   * How much an extraction through the shop's item handler may take: reserved stock when there is a
   * reservation for the item, otherwise what the racks hold.
   */
  public static int extractable(int requested, int reserved, int available) {
    return reserved > 0 ? Math.min(requested, reserved) : Math.min(requested, available);
  }
}
