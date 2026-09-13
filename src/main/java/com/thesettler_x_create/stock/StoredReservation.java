package com.thesettler_x_create.stock;

import java.util.List;
import java.util.UUID;

/**
 * Everything one request has reserved, in the shape it is saved and loaded.
 *
 * @param owner the MineColonies request id
 * @param expiresAtGameTime game time after which nobody released it and it may be dropped
 * @param amounts one entry per item kind
 */
public record StoredReservation<K>(
    UUID owner, long expiresAtGameTime, List<ReservedAmount<K>> amounts) {

  public StoredReservation {
    amounts = List.copyOf(amounts);
  }
}
