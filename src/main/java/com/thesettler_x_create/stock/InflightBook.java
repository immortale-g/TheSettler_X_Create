package com.thesettler_x_create.stock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import java.util.function.UnaryOperator;

/**
 * Orders on their way from the Create network to the shop racks, and the rack counts used to see
 * them arrive.
 *
 * <p>Every entry optionally belongs to a request (its owner). An entry without owner is stock that
 * is still coming but nobody waits for anymore; a new request for the item can claim it instead of
 * ordering again.
 *
 * <p>Arrivals are seen as rack stock growing past a baseline. Movements the shop knows about (a
 * courier gathering a delivery, housekeeping, packaging, handovers) are reported through {@link
 * #noteStockChange} and move the baseline with them, so what is left of a change is a real arrival
 * even when something else was taken out at the same time.
 *
 * <p>Knows nothing about Minecraft; key comparison, normalization and the item id used for notice
 * grouping are passed in. Not thread-safe; the caller keeps it on the server thread.
 */
public final class InflightBook<K> {
  /** Same request, item and address may keep at most this many open segments. */
  static final int MAX_OPEN_SEGMENTS_PER_TUPLE = 2;

  /** Stock of one item that arrived for one owner ({@code null}: nobody). */
  public record Arrival<K>(UUID owner, K key, int amount) {}

  /** An overdue order to ask the player about. */
  public record Notice<K>(
      K key,
      int remaining,
      long age,
      String requester,
      String address,
      long requestedAt,
      UUID owner) {}

  /** A saved entry. */
  public record StoredEntry<K>(
      K key,
      int remaining,
      long requestedAt,
      String requester,
      String address,
      UUID owner,
      boolean notified) {}

  /** A saved rack count baseline. */
  public record StoredBaseline<K>(K key, int count) {}

  private final BiPredicate<K, K> sameKey;
  private final BiPredicate<K, K> sameItem;
  private final UnaryOperator<K> normalizeKey;
  private final Function<K, String> itemId;
  private final List<Entry<K>> entries = new ArrayList<>();
  private final List<Baseline<K>> baselines = new ArrayList<>();

  /**
   * @param sameKey whether two keys are exactly the same item, components included
   * @param sameItem whether two keys are the same item type, components ignored
   * @param normalizeKey the form a key is stored in (for item stacks: a copy of count one)
   * @param itemId a stable id of the item type, used to group notices
   */
  public InflightBook(
      BiPredicate<K, K> sameKey,
      BiPredicate<K, K> sameItem,
      UnaryOperator<K> normalizeKey,
      Function<K, String> itemId) {
    this.sameKey = sameKey;
    this.sameItem = sameItem;
    this.normalizeKey = normalizeKey;
    this.itemId = itemId;
  }

  // ---------------------------------------------------------------------------------------------
  // Recording and arrival
  // ---------------------------------------------------------------------------------------------

  /** Item kinds with an open order. */
  public List<K> keys() {
    List<K> keys = new ArrayList<>();
    for (Entry<K> entry : entries) {
      if (indexOfSame(keys, entry.key) < 0) {
        keys.add(entry.key);
      }
    }
    return keys;
  }

  /**
   * Records an order. The rack count is taken as baseline when the item has none yet; an existing
   * baseline is kept, so an arrival that was not reconciled yet is not swallowed.
   *
   * @return true when something was recorded
   */
  public boolean record(
      K key, int amount, long now, String requester, String address, UUID owner, int currentStock) {
    if (key == null || amount <= 0) {
      return false;
    }
    K stored = normalizeKey.apply(key);
    if (findBaseline(stored) == null) {
      baselines.add(new Baseline<>(stored, Math.max(0, currentStock)));
    }
    entries.add(new Entry<>(stored, amount, now, clean(requester), clean(address), owner));
    compact();
    return true;
  }

  /**
   * Moves the baseline of an item along with a rack change the shop caused or observed itself.
   * Without an open order for the item there is nothing to adjust.
   */
  public void noteStockChange(K key, int delta) {
    if (key == null || delta == 0) {
      return;
    }
    Baseline<K> baseline = findBaseline(key);
    if (baseline != null) {
      baseline.count = Math.max(0, baseline.count + delta);
    }
  }

  /**
   * Compares the rack counts with the baselines and books growth on the oldest matching orders.
   *
   * @param currentStock rack count per item
   * @return the arrivals per owner and item; empty when nothing arrived
   */
  public List<Arrival<K>> reconcile(ToIntFunction<K> currentStock) {
    List<Arrival<K>> arrivals = new ArrayList<>();
    if (entries.isEmpty()) {
      return arrivals;
    }
    for (Entry<K> entry : entries) {
      if (findBaseline(entry.key) == null) {
        baselines.add(new Baseline<>(entry.key, Math.max(0, currentStock.applyAsInt(entry.key))));
      }
    }
    Map<UUID, Integer> byOwner = new LinkedHashMap<>();
    for (Baseline<K> baseline : baselines) {
      int current = Math.max(0, currentStock.applyAsInt(baseline.key));
      int delta = current - baseline.count;
      baseline.count = current;
      if (delta <= 0) {
        continue;
      }
      byOwner.clear();
      int remaining = delta;
      Iterator<Entry<K>> iterator = entries.iterator();
      while (iterator.hasNext() && remaining > 0) {
        Entry<K> entry = iterator.next();
        if (!sameKey.test(entry.key, baseline.key)) {
          continue;
        }
        int applied = Math.min(remaining, entry.remaining);
        entry.remaining -= applied;
        remaining -= applied;
        if (applied > 0) {
          byOwner.merge(entry.owner, applied, Integer::sum);
        }
        if (entry.remaining <= 0) {
          iterator.remove();
        }
      }
      for (Map.Entry<UUID, Integer> arrived : byOwner.entrySet()) {
        arrivals.add(new Arrival<>(arrived.getKey(), baseline.key, arrived.getValue()));
      }
    }
    pruneBaselines();
    return arrivals;
  }

  // ---------------------------------------------------------------------------------------------
  // Owners
  // ---------------------------------------------------------------------------------------------

  /** What is still coming for a request, matching the item type loosely. */
  public int remainingFor(UUID owner, K key) {
    if (owner == null || key == null) {
      return 0;
    }
    int remaining = 0;
    for (Entry<K> entry : entries) {
      if (owner.equals(entry.owner) && matchesLoosely(entry.key, key)) {
        remaining += entry.remaining;
      }
    }
    return remaining;
  }

  /** What is still coming for a request, for every item it accepts. */
  public int remainingForMatching(UUID owner, Predicate<K> accepts) {
    if (owner == null || accepts == null) {
      return 0;
    }
    int remaining = 0;
    for (Entry<K> entry : entries) {
      if (owner.equals(entry.owner) && accepts.test(entry.key)) {
        remaining += entry.remaining;
      }
    }
    return remaining;
  }

  /** What is still coming for nobody, exactly this item. */
  public int freeRemaining(K key) {
    if (key == null) {
      return 0;
    }
    int remaining = 0;
    for (Entry<K> entry : entries) {
      if (entry.owner == null && sameKey.test(entry.key, key)) {
        remaining += entry.remaining;
      }
    }
    return remaining;
  }

  /**
   * Hands up to {@code amount} of unowned incoming stock of exactly this item to a request, oldest
   * first. A partly claimed entry is split.
   *
   * @return the amount claimed
   */
  public int claimFree(UUID owner, K key, int amount) {
    if (key == null) {
      return 0;
    }
    return claimFreeMatching(owner, candidate -> sameKey.test(candidate, key), amount);
  }

  /**
   * Like {@link #claimFree(UUID, Object, int)} for any item a request accepts, e.g. every log for a
   * request for logs.
   *
   * @return the amount claimed
   */
  public int claimFreeMatching(UUID owner, Predicate<K> accepts, int amount) {
    if (owner == null || accepts == null || amount <= 0) {
      return 0;
    }
    int claimed = 0;
    for (int i = 0; i < entries.size() && claimed < amount; i++) {
      Entry<K> entry = entries.get(i);
      if (entry.owner != null || !accepts.test(entry.key)) {
        continue;
      }
      int take = Math.min(entry.remaining, amount - claimed);
      if (take == entry.remaining) {
        entry.owner = owner;
      } else {
        entry.remaining -= take;
        Entry<K> split =
            new Entry<>(entry.key, take, entry.requestedAt, entry.requester, entry.address, owner);
        entries.add(i + 1, split);
        i++;
      }
      claimed += take;
    }
    return claimed;
  }

  /**
   * Removes the owner from every entry of a request; the stock keeps coming for nobody.
   *
   * @return the amount that is now unowned
   */
  public int detach(UUID owner) {
    if (owner == null) {
      return 0;
    }
    int detached = 0;
    for (Entry<K> entry : entries) {
      if (owner.equals(entry.owner)) {
        entry.owner = null;
        detached += entry.remaining;
      }
    }
    return detached;
  }

  /**
   * Drops every entry of a request, for orders that were never sent.
   *
   * @return the amount removed
   */
  public int cancel(UUID owner) {
    if (owner == null) {
      return 0;
    }
    int removed = 0;
    Iterator<Entry<K>> iterator = entries.iterator();
    while (iterator.hasNext()) {
      Entry<K> entry = iterator.next();
      if (owner.equals(entry.owner)) {
        removed += entry.remaining;
        iterator.remove();
      }
    }
    if (removed > 0) {
      pruneBaselines();
    }
    return removed;
  }

  /**
   * Drops up to {@code amount} of exactly this item from a request's entries, newest first, for an
   * order that was dropped before it was sent.
   *
   * @return the amount removed
   */
  public int cancel(UUID owner, K key, int amount) {
    if (owner == null || key == null || amount <= 0) {
      return 0;
    }
    int removed = 0;
    for (int i = entries.size() - 1; i >= 0 && removed < amount; i--) {
      Entry<K> entry = entries.get(i);
      if (!owner.equals(entry.owner) || !sameKey.test(entry.key, key)) {
        continue;
      }
      int take = Math.min(entry.remaining, amount - removed);
      entry.remaining -= take;
      removed += take;
      if (entry.remaining <= 0) {
        entries.remove(i);
      }
    }
    if (removed > 0) {
      pruneBaselines();
    }
    return removed;
  }

  // ---------------------------------------------------------------------------------------------
  // Overdue orders
  // ---------------------------------------------------------------------------------------------

  /**
   * Drops unowned entries older than {@code timeout}: nobody waits for them, so there is nothing to
   * ask the player about.
   *
   * @return the dropped entries
   */
  public List<StoredEntry<K>> expireFree(long now, long timeout) {
    List<StoredEntry<K>> expired = new ArrayList<>();
    if (timeout <= 0L) {
      return expired;
    }
    Iterator<Entry<K>> iterator = entries.iterator();
    while (iterator.hasNext()) {
      Entry<K> entry = iterator.next();
      if (entry.owner == null && now - entry.requestedAt >= timeout) {
        expired.add(entry.toStored());
        iterator.remove();
      }
    }
    if (!expired.isEmpty()) {
      pruneBaselines();
    }
    return expired;
  }

  /**
   * Picks the oldest overdue order not asked about yet and marks it as asked. At most one notice
   * per call, grouped by item and address.
   */
  public List<Notice<K>> consumeOverdueNotices(long now, long timeout) {
    if (timeout <= 0L || entries.isEmpty()) {
      return Collections.emptyList();
    }
    Entry<K> selected = null;
    for (Entry<K> entry : entries) {
      if (entry.remaining <= 0 || entry.notified || now - entry.requestedAt < timeout) {
        continue;
      }
      if (selected == null || entry.requestedAt < selected.requestedAt) {
        selected = entry;
      }
    }
    if (selected == null) {
      return Collections.emptyList();
    }
    selected.notified = true;
    return List.of(selected.toNotice(now));
  }

  /** The oldest open order regardless of age, for the test harness. */
  public Notice<K> peekOldest(long now) {
    Entry<K> selected = null;
    for (Entry<K> entry : entries) {
      if (entry.remaining > 0 && (selected == null || entry.requestedAt < selected.requestedAt)) {
        selected = entry;
      }
    }
    return selected == null ? null : selected.toNotice(now);
  }

  // ---------------------------------------------------------------------------------------------
  // Lost-package flow (requester/address tuples)
  // ---------------------------------------------------------------------------------------------

  /**
   * What is still coming for a requester/address tuple. An empty requester and address match any
   * entry but then require the exact item; {@code requestedAt <= 0} matches any time.
   */
  public int remaining(K key, String requester, String address, long requestedAt) {
    if (key == null) {
      return 0;
    }
    String requesterName = clean(requester);
    String destination = clean(address);
    boolean exact = requesterName.isEmpty() && destination.isEmpty();
    int remaining = 0;
    for (Entry<K> entry : entries) {
      if (matchesLookup(entry.key, key, exact)
          && matchesTuple(entry, requesterName, destination, requestedAt)) {
        remaining += entry.remaining;
      }
    }
    return remaining;
  }

  /**
   * Books {@code amount} of a tuple as handled (handed over by hand, re-ordered). Falls back to the
   * item alone when the tuple matches nothing, since requester labels drift after renames.
   *
   * @return the amount consumed
   */
  public int consume(K key, int amount, String requester, String address, long requestedAt) {
    if (key == null || amount <= 0 || entries.isEmpty()) {
      return 0;
    }
    String requesterName = clean(requester);
    String destination = clean(address);
    int remaining = consumeMatches(key, amount, requesterName, destination, requestedAt);
    int consumed = amount - remaining;
    if (consumed <= 0 && (!requesterName.isEmpty() || !destination.isEmpty())) {
      consumed = amount - consumeMatches(key, amount, "", "", requestedAt);
    }
    if (consumed > 0) {
      pruneBaselines();
    }
    return consumed;
  }

  /**
   * Drops the entries of a tuple. Falls back to item and address when the requester matches
   * nothing.
   *
   * @return the amount removed
   */
  public int cancel(K key, String requester, String address, long requestedAt) {
    if (key == null || entries.isEmpty()) {
      return 0;
    }
    String requesterName = clean(requester);
    String destination = clean(address);
    int removed = cancelMatches(key, requesterName, destination, requestedAt);
    if (removed <= 0 && !requesterName.isEmpty()) {
      removed = cancelMatches(key, "", destination, requestedAt);
    }
    if (removed > 0) {
      pruneBaselines();
    }
    return removed;
  }

  // ---------------------------------------------------------------------------------------------
  // State
  // ---------------------------------------------------------------------------------------------

  public int entryCount() {
    return entries.size();
  }

  /** Entries plus baselines, i.e. whether anything is stored at all. */
  public int size() {
    return entries.size() + baselines.size();
  }

  public void clear() {
    entries.clear();
    baselines.clear();
  }

  public List<StoredEntry<K>> storedEntries() {
    List<StoredEntry<K>> stored = new ArrayList<>(entries.size());
    for (Entry<K> entry : entries) {
      stored.add(entry.toStored());
    }
    return stored;
  }

  public List<StoredBaseline<K>> storedBaselines() {
    List<StoredBaseline<K>> stored = new ArrayList<>(baselines.size());
    for (Baseline<K> baseline : baselines) {
      stored.add(new StoredBaseline<>(baseline.key, baseline.count));
    }
    return stored;
  }

  /**
   * Replaces the state with saved entries and baselines. Notices are asked again after a load,
   * since interactions do not survive it.
   */
  public void restore(List<StoredEntry<K>> savedEntries, List<StoredBaseline<K>> savedBaselines) {
    entries.clear();
    baselines.clear();
    for (StoredEntry<K> saved : savedEntries) {
      if (saved == null || saved.key() == null || saved.remaining() <= 0) {
        continue;
      }
      entries.add(
          new Entry<>(
              normalizeKey.apply(saved.key()),
              saved.remaining(),
              saved.requestedAt(),
              clean(saved.requester()),
              clean(saved.address()),
              saved.owner()));
    }
    compact();
    for (StoredBaseline<K> saved : savedBaselines) {
      if (saved != null && saved.key() != null && findBaseline(saved.key()) == null) {
        baselines.add(new Baseline<>(normalizeKey.apply(saved.key()), Math.max(0, saved.count())));
      }
    }
  }

  // ---------------------------------------------------------------------------------------------
  // Internals
  // ---------------------------------------------------------------------------------------------

  private int consumeMatches(
      K key, int amount, String requester, String destination, long requestedAt) {
    boolean exact = requester.isEmpty() && destination.isEmpty();
    int remaining = amount;
    Iterator<Entry<K>> iterator = entries.iterator();
    while (iterator.hasNext() && remaining > 0) {
      Entry<K> entry = iterator.next();
      if (!matchesLookup(entry.key, key, exact)
          || !matchesTuple(entry, requester, destination, requestedAt)) {
        continue;
      }
      int used = Math.min(remaining, entry.remaining);
      entry.remaining -= used;
      remaining -= used;
      if (entry.remaining <= 0) {
        iterator.remove();
      } else if (used > 0) {
        // The unresolved remainder must be promptable again.
        entry.notified = false;
      }
    }
    return remaining;
  }

  private int cancelMatches(K key, String requester, String destination, long requestedAt) {
    boolean exact = requester.isEmpty() && destination.isEmpty();
    int removed = 0;
    Iterator<Entry<K>> iterator = entries.iterator();
    while (iterator.hasNext()) {
      Entry<K> entry = iterator.next();
      if (matchesLookup(entry.key, key, exact)
          && matchesTuple(entry, requester, destination, requestedAt)) {
        removed += entry.remaining;
        iterator.remove();
      }
    }
    return removed;
  }

  private boolean matchesLoosely(K a, K b) {
    return sameKey.test(a, b) || sameItem.test(a, b);
  }

  /**
   * Matching by item type alone is only trusted while the requester/address tuple confirms which
   * request an entry belongs to. Without it, two component variants of the same item (differently
   * enchanted books) would consume each other's entries.
   */
  private boolean matchesLookup(K entryKey, K key, boolean requireExact) {
    return requireExact ? sameKey.test(entryKey, key) : matchesLoosely(entryKey, key);
  }

  private static boolean matchesTuple(
      Entry<?> entry, String requester, String destination, long requestedAt) {
    if (!requester.isEmpty() && !requester.equals(entry.requester)) {
      return false;
    }
    if (!destination.isEmpty() && !destination.equals(entry.address)) {
      return false;
    }
    return requestedAt <= 0L || entry.requestedAt == requestedAt;
  }

  /**
   * Keeps notice prompts stable for unowned entries: identical segments collapse into one, and a
   * requester/address tuple keeps only its {@link #MAX_OPEN_SEGMENTS_PER_TUPLE} newest segments.
   * Entries of a request are never merged or dropped; each of them is an order the request counts
   * on, and losing one makes the request order again.
   */
  private void compact() {
    entries.removeIf(entry -> entry.remaining <= 0);
    if (entries.size() <= 1) {
      return;
    }
    Map<String, Entry<K>> unique = new LinkedHashMap<>();
    List<Entry<K>> owned = new ArrayList<>();
    for (Entry<K> entry : entries) {
      if (entry.owner != null) {
        owned.add(entry);
        continue;
      }
      String segment =
          itemId.apply(entry.key)
              + "|"
              + entry.requester
              + "|"
              + entry.address
              + "|"
              + entry.requestedAt
              + "|"
              + entry.remaining;
      Entry<K> existing = unique.get(segment);
      if (existing == null) {
        unique.put(segment, entry);
      } else {
        existing.notified = existing.notified || entry.notified;
      }
    }
    List<Entry<K>> newestFirst = new ArrayList<>(unique.values());
    newestFirst.sort((left, right) -> Long.compare(right.requestedAt, left.requestedAt));
    Map<String, Integer> keptPerTuple = new HashMap<>();
    List<Entry<K>> kept = new ArrayList<>(newestFirst.size());
    for (Entry<K> entry : newestFirst) {
      String tuple = itemId.apply(entry.key) + "|" + entry.requester + "|" + entry.address;
      int count = keptPerTuple.getOrDefault(tuple, 0);
      if (count >= MAX_OPEN_SEGMENTS_PER_TUPLE) {
        continue;
      }
      keptPerTuple.put(tuple, count + 1);
      kept.add(entry);
    }
    kept.addAll(owned);
    // Stable sort: entries of the same tick keep their recording order.
    kept.sort((left, right) -> Long.compare(left.requestedAt, right.requestedAt));
    entries.clear();
    entries.addAll(kept);
  }

  private void pruneBaselines() {
    baselines.removeIf(baseline -> !hasEntryFor(baseline.key));
  }

  private boolean hasEntryFor(K key) {
    for (Entry<K> entry : entries) {
      if (sameKey.test(entry.key, key)) {
        return true;
      }
    }
    return false;
  }

  private Baseline<K> findBaseline(K key) {
    for (Baseline<K> baseline : baselines) {
      if (sameKey.test(baseline.key, key)) {
        return baseline;
      }
    }
    return null;
  }

  private int indexOfSame(List<K> keys, K key) {
    for (int i = 0; i < keys.size(); i++) {
      if (sameKey.test(keys.get(i), key)) {
        return i;
      }
    }
    return -1;
  }

  private static String clean(String value) {
    return value == null ? "" : value;
  }

  private static final class Entry<K> {
    private final K key;
    private int remaining;
    private final long requestedAt;
    private final String requester;
    private final String address;
    private UUID owner;
    private boolean notified;

    private Entry(
        K key, int remaining, long requestedAt, String requester, String address, UUID owner) {
      this.key = key;
      this.remaining = remaining;
      this.requestedAt = requestedAt;
      this.requester = requester;
      this.address = address;
      this.owner = owner;
    }

    private StoredEntry<K> toStored() {
      return new StoredEntry<>(key, remaining, requestedAt, requester, address, owner, notified);
    }

    private Notice<K> toNotice(long now) {
      return new Notice<>(
          key, remaining, now - requestedAt, requester, address, requestedAt, owner);
    }
  }

  private static final class Baseline<K> {
    private final K key;
    private int count;

    private Baseline(K key, int count) {
      this.key = Objects.requireNonNull(key);
      this.count = count;
    }
  }
}
