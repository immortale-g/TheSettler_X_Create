package com.thesettler_x_create.stock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ReservationBookTest {
  private static final long TTL = ReservationExpiryPolicy.RESERVATION_TTL;
  private static final UUID REQUEST_A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
  private static final UUID REQUEST_B = UUID.fromString("00000000-0000-0000-0000-00000000000b");

  private long now;
  private ReservationBook<String> book;

  @BeforeEach
  void setUp() {
    now = 1_000L;
    book = new ReservationBook<>(String::equals, String::trim, () -> now);
  }

  @Test
  void oneRequestKeepsSeveralItemKinds() {
    // A request for "any log" served with oak and birch used to keep only the last item kind.
    book.reserve(REQUEST_A, "oak_log", 32);
    book.reserve(REQUEST_A, "birch_log", 32);

    assertEquals(32, book.reservedFor("oak_log"));
    assertEquals(32, book.reservedFor("birch_log"));
    assertEquals(64, book.reservedForOwner(REQUEST_A));
  }

  @Test
  void reservingTheSameItemAgainAddsUp() {
    book.reserve(REQUEST_A, "brass_ingot", 64);
    book.reserve(REQUEST_A, "brass_ingot", 128);

    assertEquals(192, book.reservedFor("brass_ingot"));
    assertEquals(1, book.snapshot().size());
  }

  @Test
  void keysAreStoredNormalized() {
    book.reserve(REQUEST_A, " oak_log ", 8);

    assertEquals(8, book.reservedFor("oak_log"));
    assertEquals("oak_log", book.snapshot().get(0).key());
  }

  @Test
  void consumeOnlyTakesTheMatchingItemKind() {
    book.reserve(REQUEST_A, "oak_log", 32);
    book.reserve(REQUEST_A, "birch_log", 32);

    assertEquals(32, book.consume(REQUEST_A, "oak_log", 32));

    assertEquals(0, book.reservedFor("oak_log"));
    assertEquals(32, book.reservedFor("birch_log"));
    assertEquals(32, book.reservedForOwner(REQUEST_A));
  }

  @Test
  void consumeIsCappedAndDropsTheOwnerWhenEmpty() {
    book.reserve(REQUEST_A, "oak_log", 10);

    assertEquals(10, book.consume(REQUEST_A, "oak_log", 64));
    assertEquals(0, book.ownerCount());
    assertEquals(0, book.consume(REQUEST_A, "oak_log", 1));
  }

  @Test
  void consumeDoesNotTouchOtherOwners() {
    book.reserve(REQUEST_A, "oak_log", 10);
    book.reserve(REQUEST_B, "oak_log", 20);

    book.consume(REQUEST_A, "oak_log", 10);

    assertEquals(20, book.reservedFor("oak_log"));
    assertEquals(20, book.reservedForOwner(REQUEST_B));
  }

  @Test
  void releaseDropsAllItemKindsOfTheOwner() {
    book.reserve(REQUEST_A, "oak_log", 32);
    book.reserve(REQUEST_A, "birch_log", 32);
    book.reserve(REQUEST_B, "oak_log", 5);

    assertTrue(book.release(REQUEST_A));
    assertFalse(book.release(REQUEST_A));

    assertEquals(5, book.reservedFor("oak_log"));
    assertEquals(0, book.reservedFor("birch_log"));
  }

  @Test
  void reservedMatchingSumsEveryAcceptedKey() {
    book.reserve(REQUEST_A, "oak_log", 32);
    book.reserve(REQUEST_B, "birch_log", 16);
    book.reserve(REQUEST_B, "cobblestone", 64);

    assertEquals(48, book.reservedMatching(key -> key.endsWith("_log")));
  }

  @Test
  void unreleasedReservationsExpire() {
    book.reserve(REQUEST_A, "oak_log", 8);

    now += TTL - 1L;
    assertFalse(book.expire());
    now += 1L;
    assertTrue(book.expire());
    assertEquals(0, book.reservedFor("oak_log"));
  }

  @Test
  void reserveRestartsTheExpiryForAllItemKindsOfTheOwner() {
    book.reserve(REQUEST_A, "oak_log", 8);
    now += TTL - 10L;
    book.reserve(REQUEST_A, "birch_log", 8);
    now += 20L;

    assertFalse(book.expire());
    assertEquals(16, book.reservedForOwner(REQUEST_A));
  }

  @Test
  void refreshKeepsActiveOwnersAliveAndLetsOthersExpire() {
    book.reserve(REQUEST_A, "oak_log", 8);
    book.reserve(REQUEST_B, "oak_log", 8);

    for (int step = 0; step < 8; step++) {
      now += TTL / 4L;
      book.refresh(Set.of(REQUEST_A));
      book.expire();
    }

    assertEquals(8, book.reservedForOwner(REQUEST_A));
    assertEquals(0, book.reservedForOwner(REQUEST_B));
  }

  @Test
  void restoredReservationsGetAFreshExpiry() {
    long staleExpiry = 10L;
    book.restore(
        List.of(
            new StoredReservation<>(
                REQUEST_A,
                staleExpiry,
                List.of(
                    new ReservedAmount<>("oak_log", 4), new ReservedAmount<>("birch_log", 6)))));
    now = 50_000L;

    assertFalse(book.expire());
    assertEquals(10, book.reservedForOwner(REQUEST_A));
    now += TTL;
    assertTrue(book.expire());
  }

  @Test
  void restoreSkipsInvalidEntriesAndMergesDuplicates() {
    book.restore(
        List.of(
            new StoredReservation<>(
                REQUEST_A,
                5_000L,
                List.of(
                    new ReservedAmount<>("oak_log", 4),
                    new ReservedAmount<>(" oak_log", 6),
                    new ReservedAmount<>("birch_log", 0))),
            new StoredReservation<>(REQUEST_B, 5_000L, List.of())));

    assertEquals(1, book.ownerCount());
    assertEquals(10, book.reservedFor("oak_log"));
    assertEquals(0, book.reservedFor("birch_log"));
  }

  @Test
  void storedRoundTripKeepsOwnersItemsAndExpiry() {
    book.reserve(REQUEST_A, "oak_log", 32);
    book.reserve(REQUEST_A, "birch_log", 16);
    List<StoredReservation<String>> stored = book.stored();

    ReservationBook<String> reloaded =
        new ReservationBook<>(String::equals, String::trim, () -> now);
    reloaded.restore(stored);

    assertEquals(stored, reloaded.stored());
  }

  @Test
  void invalidInputIsIgnored() {
    assertFalse(book.reserve(null, "oak_log", 1));
    assertFalse(book.reserve(REQUEST_A, null, 1));
    assertFalse(book.reserve(REQUEST_A, "oak_log", 0));
    assertEquals(0, book.consume(REQUEST_A, "oak_log", -1));
    assertEquals(0, book.reservedFor(null));
    assertEquals(0, book.ownerCount());
  }
}
