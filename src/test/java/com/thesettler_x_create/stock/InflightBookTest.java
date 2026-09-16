package com.thesettler_x_create.stock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InflightBookTest {
  private static final UUID REQUEST_A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
  private static final UUID REQUEST_B = UUID.fromString("00000000-0000-0000-0000-00000000000b");
  private static final long TIMEOUT = 6_000L;

  private final Map<String, Integer> rack = new HashMap<>();

  /** Keys like "book#sharpness": the part before '#' is the item type. */
  private static InflightBook<String> book() {
    return new InflightBook<>(
        String::equals,
        (a, b) -> itemType(a).equals(itemType(b)),
        key -> key,
        InflightBookTest::itemType);
  }

  private static String itemType(String key) {
    int hash = key.indexOf('#');
    return hash < 0 ? key : key.substring(0, hash);
  }

  private int stock(String key) {
    return rack.getOrDefault(key, 0);
  }

  private void record(InflightBook<String> book, String key, int amount, long now, UUID owner) {
    book.record(key, amount, now, "Bob", "shop", owner, stock(key));
  }

  @Test
  void arrivalIsBookedOnTheOldestOrderAndReportedPerOwner() {
    InflightBook<String> book = book();
    record(book, "iron", 64, 10L, REQUEST_A);
    record(book, "iron", 64, 20L, REQUEST_B);

    rack.put("iron", 100);
    List<InflightBook.Arrival<String>> arrivals = book.reconcile(this::stock);

    assertEquals(
        List.of(
            new InflightBook.Arrival<>(REQUEST_A, "iron", 64),
            new InflightBook.Arrival<>(REQUEST_B, "iron", 36)),
        arrivals);
    assertEquals(0, book.remainingFor(REQUEST_A, "iron"));
    assertEquals(28, book.remainingFor(REQUEST_B, "iron"));
  }

  @Test
  void knownRemovalsDoNotHideAnArrival() {
    InflightBook<String> book = book();
    rack.put("iron", 64);
    record(book, "iron", 64, 0L, REQUEST_A);

    // A courier takes the 64 that were there, then the order arrives before the next check.
    book.noteStockChange("iron", -64);
    rack.put("iron", 64);
    List<InflightBook.Arrival<String>> arrivals = book.reconcile(this::stock);

    assertEquals(List.of(new InflightBook.Arrival<>(REQUEST_A, "iron", 64)), arrivals);
  }

  @Test
  void knownInsertionsAreNoArrival() {
    InflightBook<String> book = book();
    record(book, "iron", 64, 0L, REQUEST_A);

    // A courier dumps 20 iron into the racks.
    book.noteStockChange("iron", 20);
    rack.put("iron", 20);

    assertTrue(book.reconcile(this::stock).isEmpty());
    assertEquals(64, book.remainingFor(REQUEST_A, "iron"));
  }

  @Test
  void unknownRemovalLowersTheBaselineWithoutArrival() {
    InflightBook<String> book = book();
    rack.put("iron", 30);
    record(book, "iron", 64, 0L, REQUEST_A);

    rack.put("iron", 10);
    assertTrue(book.reconcile(this::stock).isEmpty());
    rack.put("iron", 40);

    assertEquals(
        List.of(new InflightBook.Arrival<>(REQUEST_A, "iron", 30)), book.reconcile(this::stock));
  }

  @Test
  void recordingAgainKeepsTheBaselineSoPendingGrowthIsNotLost() {
    InflightBook<String> book = book();
    record(book, "iron", 64, 0L, REQUEST_A);
    rack.put("iron", 64);

    record(book, "iron", 16, 5L, REQUEST_B);

    assertEquals(
        List.of(new InflightBook.Arrival<>(REQUEST_A, "iron", 64)), book.reconcile(this::stock));
  }

  @Test
  void detachedStockCanBeClaimedByAnotherRequest() {
    InflightBook<String> book = book();
    record(book, "iron", 64, 0L, REQUEST_A);

    assertEquals(64, book.detach(REQUEST_A));
    assertEquals(64, book.freeRemaining("iron"));
    assertEquals(40, book.claimFree(REQUEST_B, "iron", 40));

    assertEquals(40, book.remainingFor(REQUEST_B, "iron"));
    assertEquals(24, book.freeRemaining("iron"));
    rack.put("iron", 64);
    assertEquals(
        List.of(
            new InflightBook.Arrival<>(null, "iron", 24),
            new InflightBook.Arrival<>(REQUEST_B, "iron", 40)),
        book.reconcile(this::stock));
  }

  @Test
  void claimingNeedsTheExactItem() {
    InflightBook<String> book = book();
    record(book, "book#sharpness", 1, 0L, null);

    assertEquals(0, book.claimFree(REQUEST_A, "book#fire", 1));
    assertEquals(1, book.claimFree(REQUEST_A, "book#sharpness", 5));
  }

  @Test
  void aRequestSeesItsOrdersEvenWhenComponentsDrifted() {
    InflightBook<String> book = book();
    record(book, "book#sharpness", 2, 0L, REQUEST_A);

    assertEquals(2, book.remainingFor(REQUEST_A, "book#fire"));
    assertEquals(0, book.remainingFor(REQUEST_B, "book#sharpness"));
    assertEquals(0, book.freeRemaining("book#sharpness"));
  }

  @Test
  void remainingForMatchingCountsEveryAcceptedItemOfTheRequest() {
    InflightBook<String> book = book();
    record(book, "log#oak", 32, 0L, REQUEST_A);
    record(book, "log#birch", 16, 1L, REQUEST_A);
    record(book, "log#spruce", 8, 2L, REQUEST_B);

    assertEquals(48, book.remainingForMatching(REQUEST_A, key -> key.startsWith("log#")));
    assertEquals(0, book.remainingForMatching(null, key -> true));
  }

  @Test
  void aRequestForAnyVariantClaimsEveryAcceptedFreeOrder() {
    InflightBook<String> book = book();
    record(book, "log#oak", 32, 0L, null);
    record(book, "log#birch", 32, 1L, null);
    record(book, "plank", 64, 2L, null);

    int claimed = book.claimFreeMatching(REQUEST_A, key -> key.startsWith("log#"), 48);

    assertEquals(48, claimed);
    assertEquals(48, book.remainingFor(REQUEST_A, "log#oak"));
    assertEquals(16, book.freeRemaining("log#birch"));
    assertEquals(64, book.freeRemaining("plank"));
  }

  @Test
  void cancelDropsOnlyThatRequest() {
    InflightBook<String> book = book();
    record(book, "iron", 64, 0L, REQUEST_A);
    record(book, "iron", 32, 1L, REQUEST_B);

    assertEquals(64, book.cancel(REQUEST_A));
    assertEquals(32, book.remainingFor(REQUEST_B, "iron"));
    assertEquals(1, book.entryCount());
  }

  @Test
  void cancelAnAmountTakesTheNewestFirst() {
    InflightBook<String> book = book();
    record(book, "iron", 64, 0L, REQUEST_A);
    record(book, "iron", 32, 1L, REQUEST_A);

    assertEquals(40, book.cancel(REQUEST_A, "iron", 40));
    assertEquals(56, book.remainingFor(REQUEST_A, "iron"));
  }

  @Test
  void unownedOverdueStockExpiresSilentlyOwnedStockIsAskedAbout() {
    InflightBook<String> book = book();
    record(book, "iron", 64, 0L, null);
    record(book, "gold", 8, 0L, REQUEST_A);

    List<InflightBook.StoredEntry<String>> expired = book.expireFree(TIMEOUT, TIMEOUT);
    List<InflightBook.Notice<String>> notices = book.consumeOverdueNotices(TIMEOUT, TIMEOUT);

    assertEquals(1, expired.size());
    assertEquals("iron", expired.get(0).key());
    assertEquals(1, notices.size());
    assertEquals("gold", notices.get(0).key());
    assertEquals(REQUEST_A, notices.get(0).owner());
    // Asked once only.
    assertTrue(book.consumeOverdueNotices(TIMEOUT + 1, TIMEOUT).isEmpty());
  }

  @Test
  void tupleLookupsFallBackToTheExactItem() {
    InflightBook<String> book = book();
    record(book, "book#sharpness", 3, 7L, REQUEST_A);

    assertEquals(3, book.remaining("book#fire", "Bob", "shop", 7L));
    assertEquals(0, book.remaining("book#fire", "", "", -1L));
    assertEquals(3, book.remaining("book#sharpness", "", "", -1L));
    // Renamed requester: nothing matches the tuple, the exact item still does.
    assertEquals(2, book.consume("book#sharpness", 2, "Alice", "elsewhere", -1L));
    assertEquals(1, book.cancel("book#sharpness", "Alice", "shop", -1L));
    assertEquals(0, book.entryCount());
  }

  @Test
  void partlyConsumedEntriesCanBeAskedAboutAgain() {
    InflightBook<String> book = book();
    record(book, "iron", 64, 0L, REQUEST_A);
    book.consumeOverdueNotices(TIMEOUT, TIMEOUT);

    book.consume("iron", 10, "Bob", "shop", 0L);

    assertEquals(1, book.consumeOverdueNotices(TIMEOUT, TIMEOUT).size());
  }

  @Test
  void anUnownedTupleKeepsOnlyItsTwoNewestSegments() {
    InflightBook<String> book = book();
    record(book, "iron", 1, 1L, null);
    record(book, "iron", 2, 2L, null);
    record(book, "iron", 3, 3L, null);

    assertEquals(2, book.entryCount());
    assertEquals(5, book.freeRemaining("iron"));
  }

  @Test
  void ordersOfARequestAreNeverMergedOrDropped() {
    InflightBook<String> book = book();
    record(book, "iron", 64, 1L, REQUEST_A);
    record(book, "iron", 64, 1L, REQUEST_A);
    record(book, "iron", 64, 2L, REQUEST_A);
    record(book, "iron", 16, 3L, REQUEST_B);

    assertEquals(4, book.entryCount());
    assertEquals(192, book.remainingFor(REQUEST_A, "iron"));
  }

  @Test
  void restoreKeepsEntriesAndBaselinesAndAsksAgain() {
    InflightBook<String> book = book();
    rack.put("iron", 5);
    record(book, "iron", 64, 0L, REQUEST_A);
    book.consumeOverdueNotices(TIMEOUT, TIMEOUT);

    InflightBook<String> restored = book();
    restored.restore(book.storedEntries(), book.storedBaselines());

    assertEquals(64, restored.remainingFor(REQUEST_A, "iron"));
    assertEquals(List.of(new InflightBook.StoredBaseline<>("iron", 5)), restored.storedBaselines());
    assertEquals(1, restored.consumeOverdueNotices(TIMEOUT, TIMEOUT).size());
  }

  @Test
  void peekOldestIgnoresAge() {
    InflightBook<String> book = book();
    assertNull(book.peekOldest(0L));
    record(book, "iron", 64, 50L, REQUEST_A);
    record(book, "gold", 1, 20L, REQUEST_B);

    assertEquals("gold", book.peekOldest(60L).key());
    assertEquals(40L, book.peekOldest(60L).age());
  }
}
