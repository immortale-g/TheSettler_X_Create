package com.thesettler_x_create.blockentity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ReservationExpiryPolicyTest {
  private static final long TTL = ReservationExpiryPolicy.RESERVATION_TTL;

  @Test
  void activeRequestReservationSurvivesLongerThanTheTtl() {
    // A slow delivery: reserved at tick 0, request open for 20 minutes, resolver ticks every
    // second.
    long expires = ReservationExpiryPolicy.newExpiry(0L);
    for (long now = 0L; now <= TTL * 4L; now += 20L) {
      assertFalse(ReservationExpiryPolicy.isExpired(expires, now), "expired at tick " + now);
      expires = ReservationExpiryPolicy.keepAliveExpiry(expires, now);
    }
  }

  @Test
  void reservationWithoutKeepAliveStillExpires() {
    long expires = ReservationExpiryPolicy.newExpiry(0L);
    assertFalse(ReservationExpiryPolicy.isExpired(expires, TTL - 1L));
    assertTrue(ReservationExpiryPolicy.isExpired(expires, TTL));
  }

  @Test
  void keepAliveOnlyExtendsOnceHalfTheTtlIsUsed() {
    long expires = ReservationExpiryPolicy.newExpiry(0L);
    assertEquals(expires, ReservationExpiryPolicy.keepAliveExpiry(expires, TTL / 2L));
    assertEquals(
        TTL / 2L + 1L + TTL, ReservationExpiryPolicy.keepAliveExpiry(expires, TTL / 2L + 1L));
  }

  @Test
  void loadedReservationGetsAFreshTtlEvenWhenTheSavedExpiryIsStale() {
    long staleExpiry = 1_000L;
    long now = 50_000L;
    long expires = ReservationExpiryPolicy.loadedExpiry(staleExpiry, now);
    assertFalse(ReservationExpiryPolicy.isExpired(expires, now + TTL - 1L));
    assertEquals(
        now + TTL + 10_000L, ReservationExpiryPolicy.loadedExpiry(now + TTL + 10_000L, now));
  }
}
