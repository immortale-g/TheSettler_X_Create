package com.thesettler_x_create.stock;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiPredicate;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

/**
 * Per-request reservations: "this much of this item is spoken for by request X". One request can
 * hold several item kinds (a request for any log can be served with oak and birch).
 *
 * <p>Knows nothing about Minecraft. The item kind is a type parameter; how two keys are compared,
 * how a key is normalized and where the game time comes from are passed in. Not thread-safe; the
 * caller keeps it on the server thread.
 *
 * <p>Expiry works per request. {@link #expire()} drops requests nobody released within {@link
 * ReservationExpiryPolicy#RESERVATION_TTL}; {@link #refresh(Set)} keeps active requests alive. The
 * caller decides when to expire, so reads that happen off the server thread stay side-effect free.
 */
public final class ReservationBook<K> {
  private final BiPredicate<K, K> sameKey;
  private final UnaryOperator<K> normalizeKey;
  private final LongSupplier clock;
  private final Map<UUID, OwnerReservations<K>> owners = new LinkedHashMap<>();

  // Saved expiry times can be older than the saved game time (the chunk is not re-saved on every
  // refresh), so restored reservations get a fresh TTL on the next expire or refresh.
  private boolean rebaseRestoredExpiry;

  /**
   * @param sameKey whether two keys are the same item kind
   * @param normalizeKey returns the form a key is stored in (for item stacks: a copy of count one)
   * @param clock current game time
   */
  public ReservationBook(
      BiPredicate<K, K> sameKey, UnaryOperator<K> normalizeKey, LongSupplier clock) {
    this.sameKey = sameKey;
    this.normalizeKey = normalizeKey;
    this.clock = clock;
  }

  /**
   * Adds {@code amount} of {@code key} to the reservations of {@code owner} and restarts its
   * expiry.
   *
   * @return true when something was reserved
   */
  public boolean reserve(UUID owner, K key, int amount) {
    if (owner == null || key == null || amount <= 0) {
      return false;
    }
    long expiresAt = ReservationExpiryPolicy.newExpiry(clock.getAsLong());
    OwnerReservations<K> reservations =
        owners.computeIfAbsent(owner, id -> new OwnerReservations<>(expiresAt));
    MutableAmount<K> existing = reservations.find(key, sameKey);
    if (existing == null) {
      reservations.amounts.add(new MutableAmount<>(normalizeKey.apply(key), amount));
    } else {
      existing.amount += amount;
    }
    reservations.expiresAtGameTime = expiresAt;
    return true;
  }

  /**
   * Drops everything {@code owner} has reserved.
   *
   * @return true when the owner had reservations
   */
  public boolean release(UUID owner) {
    return owner != null && owners.remove(owner) != null;
  }

  /**
   * Takes up to {@code amount} of {@code key} out of the reservations of {@code owner}. Other item
   * kinds of the same owner stay reserved.
   *
   * @return the amount actually taken
   */
  public int consume(UUID owner, K key, int amount) {
    if (owner == null || key == null || amount <= 0) {
      return 0;
    }
    OwnerReservations<K> reservations = owners.get(owner);
    if (reservations == null) {
      return 0;
    }
    int remaining = amount;
    Iterator<MutableAmount<K>> iterator = reservations.amounts.iterator();
    while (iterator.hasNext() && remaining > 0) {
      MutableAmount<K> entry = iterator.next();
      if (!sameKey.test(entry.key, key)) {
        continue;
      }
      int taken = Math.min(remaining, entry.amount);
      entry.amount -= taken;
      remaining -= taken;
      if (entry.amount <= 0) {
        iterator.remove();
      }
    }
    if (reservations.amounts.isEmpty()) {
      owners.remove(owner);
    }
    return amount - remaining;
  }

  /**
   * Extends the expiry of the given still-active owners.
   *
   * @return number of owners whose expiry changed
   */
  public int refresh(Set<UUID> activeOwners) {
    rebaseRestoredExpiryIfNeeded();
    if (activeOwners == null || activeOwners.isEmpty() || owners.isEmpty()) {
      return 0;
    }
    long now = clock.getAsLong();
    int refreshed = 0;
    for (Map.Entry<UUID, OwnerReservations<K>> entry : owners.entrySet()) {
      if (!activeOwners.contains(entry.getKey())) {
        continue;
      }
      OwnerReservations<K> reservations = entry.getValue();
      long expires = ReservationExpiryPolicy.keepAliveExpiry(reservations.expiresAtGameTime, now);
      if (expires != reservations.expiresAtGameTime) {
        reservations.expiresAtGameTime = expires;
        refreshed++;
      }
    }
    return refreshed;
  }

  /**
   * Drops owners whose expiry has passed.
   *
   * @return true when something was dropped
   */
  public boolean expire() {
    rebaseRestoredExpiryIfNeeded();
    long now = clock.getAsLong();
    return owners
        .values()
        .removeIf(
            reservations -> ReservationExpiryPolicy.isExpired(reservations.expiresAtGameTime, now));
  }

  /** Total reserved for {@code key} across all owners. */
  public int reservedFor(K key) {
    if (key == null) {
      return 0;
    }
    return reservedMatching(candidate -> sameKey.test(candidate, key));
  }

  /** Total reserved across all owners for every key the filter accepts. */
  public int reservedMatching(Predicate<K> filter) {
    if (filter == null) {
      return 0;
    }
    int total = 0;
    for (OwnerReservations<K> reservations : owners.values()) {
      for (MutableAmount<K> entry : reservations.amounts) {
        if (filter.test(entry.key)) {
          total += entry.amount;
        }
      }
    }
    return total;
  }

  /** Total reserved by {@code owner}, all item kinds together. */
  public int reservedForOwner(UUID owner) {
    OwnerReservations<K> reservations = owner == null ? null : owners.get(owner);
    if (reservations == null) {
      return 0;
    }
    int total = 0;
    for (MutableAmount<K> entry : reservations.amounts) {
      total += entry.amount;
    }
    return total;
  }

  /** One entry per owner and item kind. */
  public List<ReservedAmount<K>> snapshot() {
    List<ReservedAmount<K>> snapshot = new ArrayList<>();
    for (OwnerReservations<K> reservations : owners.values()) {
      for (MutableAmount<K> entry : reservations.amounts) {
        snapshot.add(new ReservedAmount<>(entry.key, entry.amount));
      }
    }
    return snapshot;
  }

  /** Number of owners holding reservations. */
  public int ownerCount() {
    return owners.size();
  }

  public void clear() {
    owners.clear();
    rebaseRestoredExpiry = false;
  }

  /** Current state in its saved form. */
  public List<StoredReservation<K>> stored() {
    List<StoredReservation<K>> stored = new ArrayList<>();
    for (Map.Entry<UUID, OwnerReservations<K>> entry : owners.entrySet()) {
      List<ReservedAmount<K>> amounts = new ArrayList<>();
      for (MutableAmount<K> amount : entry.getValue().amounts) {
        amounts.add(new ReservedAmount<>(amount.key, amount.amount));
      }
      stored.add(
          new StoredReservation<>(entry.getKey(), entry.getValue().expiresAtGameTime, amounts));
    }
    return stored;
  }

  /**
   * Replaces the current state with saved reservations. Entries without a key or amount are
   * skipped; the expiry is rebased on the next {@link #expire()} or {@link #refresh(Set)}.
   */
  public void restore(List<StoredReservation<K>> stored) {
    owners.clear();
    if (stored != null) {
      for (StoredReservation<K> reservation : stored) {
        if (reservation == null || reservation.owner() == null) {
          continue;
        }
        OwnerReservations<K> reservations =
            new OwnerReservations<>(reservation.expiresAtGameTime());
        for (ReservedAmount<K> amount : reservation.amounts()) {
          if (amount == null || amount.key() == null || amount.amount() <= 0) {
            continue;
          }
          MutableAmount<K> existing = reservations.find(amount.key(), sameKey);
          if (existing == null) {
            reservations.amounts.add(
                new MutableAmount<>(normalizeKey.apply(amount.key()), amount.amount()));
          } else {
            existing.amount += amount.amount();
          }
        }
        if (!reservations.amounts.isEmpty()) {
          owners.put(reservation.owner(), reservations);
        }
      }
    }
    rebaseRestoredExpiry = !owners.isEmpty();
  }

  private void rebaseRestoredExpiryIfNeeded() {
    if (!rebaseRestoredExpiry) {
      return;
    }
    rebaseRestoredExpiry = false;
    long now = clock.getAsLong();
    for (OwnerReservations<K> reservations : owners.values()) {
      reservations.expiresAtGameTime =
          ReservationExpiryPolicy.loadedExpiry(reservations.expiresAtGameTime, now);
    }
  }

  private static final class OwnerReservations<K> {
    private final List<MutableAmount<K>> amounts = new ArrayList<>();
    private long expiresAtGameTime;

    private OwnerReservations(long expiresAtGameTime) {
      this.expiresAtGameTime = expiresAtGameTime;
    }

    private MutableAmount<K> find(K key, BiPredicate<K, K> sameKey) {
      for (MutableAmount<K> entry : amounts) {
        if (sameKey.test(entry.key, key)) {
          return entry;
        }
      }
      return null;
    }
  }

  private static final class MutableAmount<K> {
    private final K key;
    private int amount;

    private MutableAmount(K key, int amount) {
      this.key = key;
      this.amount = amount;
    }
  }
}
