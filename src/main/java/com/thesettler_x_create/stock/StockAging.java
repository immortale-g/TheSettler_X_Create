package com.thesettler_x_create.stock;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.function.UnaryOperator;

/**
 * How long unreserved stock has been sitting in the shop, per item kind.
 *
 * <p>Racks do not remember when an item arrived, so the shop keeps batches per item kind: "this
 * many since game time T". When the unreserved amount grows, a new batch starts now; when it
 * shrinks (items carried away or reserved), the oldest batches go first. Only the total per item
 * kind is known, so which physical stack is old is not tracked; the amount that is old enough is.
 *
 * <p>Knows nothing about Minecraft; key comparison and normalization are passed in. Not
 * thread-safe; the caller keeps it on the server thread.
 */
public final class StockAging<K> {
  /** A saved batch: {@code amount} of {@code key} unreserved since {@code sinceGameTime}. */
  public record Batch<K>(K key, int amount, long sinceGameTime) {}

  private final BiPredicate<K, K> sameKey;
  private final UnaryOperator<K> normalizeKey;
  private final Map<K, Deque<MutableBatch>> batches = new LinkedHashMap<>();

  /**
   * @param sameKey whether two keys are the same item kind
   * @param normalizeKey returns the form a key is stored in (for item stacks: a copy of count one)
   */
  public StockAging(BiPredicate<K, K> sameKey, UnaryOperator<K> normalizeKey) {
    this.sameKey = sameKey;
    this.normalizeKey = normalizeKey;
  }

  /**
   * Brings the batches in line with the unreserved amounts counted right now. Item kinds missing
   * from {@code unreservedNow} have nothing unreserved anymore and are dropped.
   *
   * @return true when anything changed
   */
  public boolean update(List<StockAmount<K>> unreservedNow, long now) {
    boolean changed = false;
    List<K> seen = new ArrayList<>();
    for (StockAmount<K> counted : unreservedNow) {
      if (counted == null || counted.key() == null) {
        continue;
      }
      K key = findKey(counted.key());
      if (key == null) {
        if (counted.amount() <= 0) {
          continue;
        }
        key = normalizeKey.apply(counted.key());
        batches.put(key, new ArrayDeque<>());
      }
      seen.add(key);
      changed |= adjust(batches.get(key), Math.max(0, counted.amount()), now);
    }
    Iterator<Map.Entry<K, Deque<MutableBatch>>> entries = batches.entrySet().iterator();
    while (entries.hasNext()) {
      Map.Entry<K, Deque<MutableBatch>> entry = entries.next();
      if (entry.getValue().isEmpty() || !containsSame(seen, entry.getKey())) {
        entries.remove();
        changed = true;
      }
    }
    return changed;
  }

  /**
   * Books an amount that counts as having waited since {@code sinceGameTime}, ahead of every batch
   * this item kind already has. For stock that was never meant to sit here: the next {@link
   * #update} finds it in the total and starts no fresh clock for it, and because it goes in at the
   * front it is the first to leave when the amount shrinks again.
   *
   * @return true when anything was booked
   */
  public boolean addAged(K key, int amount, long sinceGameTime) {
    if (key == null || amount <= 0) {
      return false;
    }
    K stored = findKey(key);
    if (stored == null) {
      stored = normalizeKey.apply(key);
      batches.put(stored, new ArrayDeque<>());
    }
    batches.get(stored).addFirst(new MutableBatch(amount, sinceGameTime));
    return true;
  }

  /** How much of an item kind has been unreserved for at least {@code minAge} ticks. */
  public int agedAmount(K key, long now, long minAge) {
    K stored = key == null ? null : findKey(key);
    if (stored == null) {
      return 0;
    }
    int aged = 0;
    for (MutableBatch batch : batches.get(stored)) {
      if (now - batch.since >= minAge) {
        aged += batch.amount;
      }
    }
    return aged;
  }

  /** Every batch, oldest first per item kind, for saving. */
  public List<Batch<K>> stored() {
    List<Batch<K>> stored = new ArrayList<>();
    for (Map.Entry<K, Deque<MutableBatch>> entry : batches.entrySet()) {
      for (MutableBatch batch : entry.getValue()) {
        stored.add(new Batch<>(entry.getKey(), batch.amount, batch.since));
      }
    }
    return stored;
  }

  /** Replaces the state with saved batches. Game time is saved with the world, so ages carry on. */
  public void restore(List<Batch<K>> saved) {
    batches.clear();
    for (Batch<K> batch : saved) {
      if (batch == null || batch.key() == null || batch.amount() <= 0) {
        continue;
      }
      K key = findKey(batch.key());
      if (key == null) {
        key = normalizeKey.apply(batch.key());
        batches.put(key, new ArrayDeque<>());
      }
      batches.get(key).addLast(new MutableBatch(batch.amount(), batch.sinceGameTime()));
    }
  }

  /** Number of item kinds with unreserved stock. */
  public int keyCount() {
    return batches.size();
  }

  /**
   * Forgets every age; all unreserved stock starts a new clock on the next update.
   *
   * @return number of item kinds that were tracked
   */
  public int clear() {
    int count = batches.size();
    batches.clear();
    return count;
  }

  private static boolean adjust(Deque<MutableBatch> queue, int target, long now) {
    int total = 0;
    for (MutableBatch batch : queue) {
      total += batch.amount;
    }
    if (target == total) {
      return false;
    }
    if (target > total) {
      queue.addLast(new MutableBatch(target - total, now));
      return true;
    }
    int toRemove = total - target;
    while (toRemove > 0 && !queue.isEmpty()) {
      MutableBatch oldest = queue.peekFirst();
      int removed = Math.min(oldest.amount, toRemove);
      oldest.amount -= removed;
      toRemove -= removed;
      if (oldest.amount <= 0) {
        queue.removeFirst();
      }
    }
    return true;
  }

  private K findKey(K key) {
    for (K stored : batches.keySet()) {
      if (sameKey.test(stored, key)) {
        return stored;
      }
    }
    return null;
  }

  private boolean containsSame(List<K> keys, K key) {
    for (K candidate : keys) {
      if (sameKey.test(candidate, key)) {
        return true;
      }
    }
    return false;
  }

  private static final class MutableBatch {
    private int amount;
    private final long since;

    private MutableBatch(int amount, long since) {
      this.amount = amount;
      this.since = since;
    }
  }
}
