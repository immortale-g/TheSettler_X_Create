package com.thesettler_x_create.stock;

/**
 * An amount of one item kind.
 *
 * @param key the item kind, normalized by the owning {@link ReservationBook}
 * @param amount the reserved quantity, always positive
 */
public record ReservedAmount<K>(K key, int amount) {}
