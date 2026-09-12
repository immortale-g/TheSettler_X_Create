package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class CreateShopOutstandingNeededServiceTest {
  @Test
  void withoutRecordedDeliveriesTheFullCountMinusReservationIsOutstanding() {
    assertEquals(16, CreateShopOutstandingNeededService.computeFrom(16, 0, 0, 0));
    assertEquals(4, CreateShopOutstandingNeededService.computeFrom(16, 0, 0, 12));
  }

  @Test
  void aFullyDeliveredRequestHasNothingOutstandingOnceItsReservationIsConsumed() {
    // The re-delivery loop: after the courier delivered, the reservation is consumed and the old
    // formula (count - reserved) claimed all 16 were still needed, so the shop ordered again.
    assertEquals(0, CreateShopOutstandingNeededService.computeFrom(16, 0, 16, 0));
  }

  @Test
  void aPartialDeliveryLeavesOnlyTheRemainderOutstanding() {
    assertEquals(12, CreateShopOutstandingNeededService.computeFrom(16, 0, 4, 0));
    assertEquals(7, CreateShopOutstandingNeededService.computeFrom(16, 0, 4, 5));
  }

  @Test
  void reservationAndDeliveredAmountsStackDuringTheInflightWindow() {
    // Between delivery creation and completion the stack is both reserved and recorded as
    // delivered; the outstanding amount must not go negative.
    assertEquals(0, CreateShopOutstandingNeededService.computeFrom(16, 0, 16, 16));
  }

  @Test
  void nonExhaustiveLeftoverStillCountsOnTopOfDeliveredAmounts() {
    assertEquals(6, CreateShopOutstandingNeededService.computeFrom(16, 4, 6, 0));
  }

  @Test
  void overDeliveryNeverReportsNegativeOutstanding() {
    assertEquals(0, CreateShopOutstandingNeededService.computeFrom(16, 0, 20, 0));
  }
}
