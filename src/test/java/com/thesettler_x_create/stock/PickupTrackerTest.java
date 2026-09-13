package com.thesettler_x_create.stock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PickupTrackerTest {
  private static final UUID REQUEST_A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
  private static final UUID REQUEST_B = UUID.fromString("00000000-0000-0000-0000-00000000000b");

  @Test
  void booksATakenStackOnTheFirstQueuedDelivery() {
    PickupTracker<String> tracker = new PickupTracker<>();

    var allocations =
        tracker.recordTaken(
            64,
            List.of(
                new PickupTracker.QueuedDelivery<>("d1", REQUEST_A, 64),
                new PickupTracker.QueuedDelivery<>("d2", REQUEST_A, 64)));

    assertEquals(List.of(new PickupTracker.Allocation<>("d1", REQUEST_A, 64)), allocations);
    assertEquals(64, tracker.takenFor("d1"));
    assertEquals(0, tracker.takenFor("d2"));
  }

  @Test
  void addsUpSeveralExtractionsForOneDelivery() {
    PickupTracker<String> tracker = new PickupTracker<>();
    var queue = List.of(new PickupTracker.QueuedDelivery<>("d1", REQUEST_A, 64));

    tracker.recordTaken(20, queue);
    var second = tracker.recordTaken(44, queue);

    assertEquals(List.of(new PickupTracker.Allocation<>("d1", REQUEST_A, 44)), second);
    assertEquals(64, tracker.takenFor("d1"));
  }

  @Test
  void spillsOverToTheNextDeliveryOnceOneIsFull() {
    PickupTracker<String> tracker = new PickupTracker<>();
    var queue =
        List.of(
            new PickupTracker.QueuedDelivery<>("d1", REQUEST_A, 16),
            new PickupTracker.QueuedDelivery<>("d2", REQUEST_B, 64));

    var allocations = tracker.recordTaken(40, queue);

    assertEquals(
        List.of(
            new PickupTracker.Allocation<>("d1", REQUEST_A, 16),
            new PickupTracker.Allocation<>("d2", REQUEST_B, 24)),
        allocations);
  }

  @Test
  void neverBooksMoreThanTheDeliveriesCarry() {
    PickupTracker<String> tracker = new PickupTracker<>();
    var queue = List.of(new PickupTracker.QueuedDelivery<>("d1", REQUEST_A, 10));

    tracker.recordTaken(10, queue);
    var extra = tracker.recordTaken(5, queue);

    assertTrue(extra.isEmpty());
    assertEquals(10, tracker.takenFor("d1"));
  }

  @Test
  void booksNothingWithoutQueuedDeliveries() {
    PickupTracker<String> tracker = new PickupTracker<>();

    assertTrue(tracker.recordTaken(64, List.of()).isEmpty());
    assertTrue(
        tracker
            .recordTaken(0, List.of(new PickupTracker.QueuedDelivery<>("d1", REQUEST_A, 64)))
            .isEmpty());
    assertEquals(0, tracker.trackedCount());
  }

  @Test
  void skipsIncompleteCandidates() {
    PickupTracker<String> tracker = new PickupTracker<>();

    var allocations =
        tracker.recordTaken(
            8,
            java.util.Arrays.asList(
                null,
                new PickupTracker.QueuedDelivery<>("d0", null, 8),
                new PickupTracker.QueuedDelivery<>("d1", REQUEST_A, 8)));

    assertEquals(List.of(new PickupTracker.Allocation<>("d1", REQUEST_A, 8)), allocations);
  }

  @Test
  void forgetsDeliveriesNoCourierHasQueuedAnymore() {
    PickupTracker<String> tracker = new PickupTracker<>();
    tracker.recordTaken(
        16,
        List.of(
            new PickupTracker.QueuedDelivery<>("d1", REQUEST_A, 8),
            new PickupTracker.QueuedDelivery<>("d2", REQUEST_A, 8)));

    tracker.retainOnly(Set.of("d2"));

    assertEquals(0, tracker.takenFor("d1"));
    assertEquals(8, tracker.takenFor("d2"));
    tracker.retainOnly(Set.of());
    assertEquals(0, tracker.trackedCount());
  }
}
