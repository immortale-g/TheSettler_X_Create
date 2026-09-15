package com.thesettler_x_create.minecolonies.building;

import java.util.EnumMap;
import java.util.Map;
import java.util.StringJoiner;

/** What a tracking reset removed, per scope, summed over any number of shops. */
public final class ShopTrackingResetReport {
  private final Map<ShopTrackingScope, Integer> removed = new EnumMap<>(ShopTrackingScope.class);
  private int shops;
  private int inflightBeforeReset;

  /** Records what one scope of one shop removed. */
  public void add(ShopTrackingScope scope, int count) {
    removed.merge(scope, Math.max(0, count), Integer::sum);
  }

  /** Records one more shop that was reset. */
  public void countShop() {
    shops++;
  }

  /** Remembers how many orders were still on their way when inflight tracking was cleared. */
  public void noteInflightBeforeReset(int entries) {
    inflightBeforeReset += Math.max(0, entries);
  }

  /** Adds another report, e.g. of the next colony. */
  public void addAll(ShopTrackingResetReport other) {
    if (other == null) {
      return;
    }
    other.removed.forEach(this::add);
    shops += other.shops;
    inflightBeforeReset += other.inflightBeforeReset;
  }

  public int removed(ShopTrackingScope scope) {
    return removed.getOrDefault(scope, 0);
  }

  public int shops() {
    return shops;
  }

  /**
   * Orders that were still on their way and are forgotten now; open requests will order them again.
   */
  public int forgottenInflight() {
    return inflightBeforeReset;
  }

  /** One line for the command feedback, e.g. {@code shops=1, reservations=3, inflight=2}. */
  public String summary() {
    StringJoiner joiner = new StringJoiner(", ");
    joiner.add("shops=" + shops);
    for (Map.Entry<ShopTrackingScope, Integer> entry : removed.entrySet()) {
      joiner.add(entry.getKey().id() + "=" + entry.getValue());
    }
    return joiner.toString();
  }
}
