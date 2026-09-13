package com.thesettler_x_create.stock;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Remembers how much of each delivery a courier has already taken out of the shop, and splits a
 * newly taken amount over the deliveries that are still waiting for their items.
 *
 * <p>A courier gathers one delivery in several extractions (one per rack slot), so the tracker
 * keeps the running total per delivery and never books more for a delivery than it carries. The
 * delivery id is a type parameter; this class knows nothing about Minecraft. Not thread-safe; the
 * caller keeps it on the server thread.
 *
 * <p>The progress is not saved. After a reload a courier that was interrupted dumps its inventory
 * and gathers the delivery again from the start, so starting from zero matches what happens.
 */
public final class PickupTracker<D> {
  /** A delivery a courier has queued: which request it serves and how many items it carries. */
  public record QueuedDelivery<D>(D delivery, UUID owner, int amount) {}

  /** The part of a taken amount booked for one delivery. */
  public record Allocation<D>(D delivery, UUID owner, int amount) {}

  private final Map<D, Integer> taken = new HashMap<>();

  /**
   * Books {@code amount} taken items over the queued deliveries of that item, in the given order.
   * Each delivery gets at most what it still misses; whatever no delivery claims is not booked.
   *
   * @param amount items that just left the shop
   * @param candidates the queued deliveries for exactly this item, in courier queue order
   * @return what was booked per delivery; empty when nothing matched
   */
  public List<Allocation<D>> recordTaken(int amount, List<QueuedDelivery<D>> candidates) {
    List<Allocation<D>> allocations = new ArrayList<>();
    if (amount <= 0 || candidates == null) {
      return allocations;
    }
    int remaining = amount;
    for (QueuedDelivery<D> candidate : candidates) {
      if (remaining <= 0) {
        break;
      }
      if (candidate == null || candidate.delivery() == null || candidate.owner() == null) {
        continue;
      }
      int alreadyTaken = taken.getOrDefault(candidate.delivery(), 0);
      int open = candidate.amount() - alreadyTaken;
      if (open <= 0) {
        continue;
      }
      int booked = Math.min(open, remaining);
      taken.put(candidate.delivery(), alreadyTaken + booked);
      allocations.add(new Allocation<>(candidate.delivery(), candidate.owner(), booked));
      remaining -= booked;
    }
    return allocations;
  }

  /** How much was taken for a delivery so far. */
  public int takenFor(D delivery) {
    return taken.getOrDefault(delivery, 0);
  }

  /** Drops the progress of deliveries that are no longer queued by any courier. */
  public void retainOnly(Collection<D> queuedDeliveries) {
    if (queuedDeliveries == null || queuedDeliveries.isEmpty()) {
      taken.clear();
      return;
    }
    taken.keySet().retainAll(queuedDeliveries);
  }

  /** Number of deliveries with recorded progress. */
  public int trackedCount() {
    return taken.size();
  }
}
