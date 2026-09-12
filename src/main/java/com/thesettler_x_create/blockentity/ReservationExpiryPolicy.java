package com.thesettler_x_create.blockentity;

/**
 * Expiry rules for pickup reservations. The TTL only cleans up reservations nobody releases; a
 * reservation whose request is still active is extended before it can run out.
 */
final class ReservationExpiryPolicy {
  static final long RESERVATION_TTL = 20L * 60L * 5L;

  private ReservationExpiryPolicy() {}

  static long newExpiry(long now) {
    return now + RESERVATION_TTL;
  }

  static boolean isExpired(long expiresAtGameTime, long now) {
    return expiresAtGameTime <= now;
  }

  /**
   * Returns the expiry for a reservation of a still-active request. It is only extended once half
   * the TTL is used up, so an active request does not dirty the chunk on every tick.
   */
  static long keepAliveExpiry(long expiresAtGameTime, long now) {
    return expiresAtGameTime - now < RESERVATION_TTL / 2L ? newExpiry(now) : expiresAtGameTime;
  }

  /**
   * Returns the expiry for a reservation read from disk. The saved value can be older than the
   * saved game time, because the chunk is not re-saved on every refresh.
   */
  static long loadedExpiry(long savedExpiresAtGameTime, long now) {
    return Math.max(savedExpiresAtGameTime, newExpiry(now));
  }
}
