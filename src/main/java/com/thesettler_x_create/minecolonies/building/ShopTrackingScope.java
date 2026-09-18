package com.thesettler_x_create.minecolonies.building;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * The kinds of tracking a Create Shop keeps on its own, each resettable by an operator command.
 * Settings (stock network, shop address, linked blocks) are not tracking and are never reset.
 */
public enum ShopTrackingScope {
  /** Per-request pickup reservations on rack stock. */
  RESERVATIONS("reservations"),
  /** Orders on their way from the Create network, including arrival baselines. */
  INFLIGHT("inflight"),
  /** How long unreserved rack stock has been waiting for housekeeping. */
  STOCK_AGES("stock-ages"),
  /** Saved per-request flow states. */
  FLOW_STATES("flow-states"),
  /** Colony Factory Gauge requests and the packaging queue. */
  GAUGE("gauge"),
  /**
   * In-memory resolver state: order cooldowns, pending amounts, delivery ledgers, queued orders.
   */
  RUNTIME("runtime");

  /** Argument that selects every scope. */
  public static final String ALL = "all";

  private final String id;

  ShopTrackingScope(String id) {
    this.id = id;
  }

  public String id() {
    return id;
  }

  /**
   * Parses a command argument: one scope id or {@link #ALL}, case-insensitive.
   *
   * @return the selected scopes, or empty for an unknown argument
   */
  public static Optional<Set<ShopTrackingScope>> parse(String argument) {
    if (argument == null) {
      return Optional.empty();
    }
    String normalized = argument.trim().toLowerCase(Locale.ROOT);
    if (normalized.equals(ALL)) {
      return Optional.of(EnumSet.allOf(ShopTrackingScope.class));
    }
    for (ShopTrackingScope scope : values()) {
      if (scope.id.equals(normalized)) {
        return Optional.of(EnumSet.of(scope));
      }
    }
    return Optional.empty();
  }
}
