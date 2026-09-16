package com.thesettler_x_create.stock;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class OpenDeliveryPlanTest {

  @Test
  void firstWaveOnItsWayWhileTheRestIsStillComing() {
    // 256 ordered, 99 arrived and handed out, not picked up yet, 157 still coming.
    OpenDeliveryPlan plan = OpenDeliveryPlan.of(256, 99, 99, 99, 157, 0);

    assertEquals(new OpenDeliveryPlan(0, 58, 0), plan);
    // 58 goes to the order service, which subtracts the 157 on their way: nothing is ordered.
  }

  @Test
  void secondWaveGoesOutAsSoonAsItArrives() {
    // The courier picked the first 99 up (reservation consumed), the other 157 arrived.
    OpenDeliveryPlan plan = OpenDeliveryPlan.of(256, 157, 99, 0, 0, 0);

    assertEquals(new OpenDeliveryPlan(0, 0, 157), plan);
  }

  @Test
  void afterAReloadNothingCountsAsPickedUpSoFewerDeliveriesGoOut() {
    OpenDeliveryPlan plan = OpenDeliveryPlan.of(256, 157, 99, 99, 0, 0);

    assertEquals(new OpenDeliveryPlan(0, 0, 58), plan);
  }

  @Test
  void networkShortageIsOrderedOnceTheOpenDeliveryIsPickedUp() {
    // 256 needed, the network only had 100: they arrived and are out for delivery.
    OpenDeliveryPlan notPickedUp = OpenDeliveryPlan.of(256, 100, 100, 100, 0, 0);
    OpenDeliveryPlan pickedUp = OpenDeliveryPlan.of(256, 0, 100, 0, 0, 0);

    // Counted twice while the reservation still stands: less is ordered, never more.
    assertEquals(56, notPickedUp.missingBeforeInflight());
    assertEquals(156, pickedUp.missingBeforeInflight());
  }

  @Test
  void freeRackStockIsOnlyReservedForWhatNothingCoversYet() {
    // 64 needed, 32 in delivery, 16 on the way, 100 free in the rack.
    OpenDeliveryPlan plan = OpenDeliveryPlan.of(64, 0, 32, 32, 16, 100);

    assertEquals(16, plan.reserveFromRack());
    assertEquals(16, plan.missingBeforeInflight());
    // The open delivery is not confirmed as picked up, so it is assumed to still hold 32 of the
    // reservations: the 16 just reserved wait until it is picked up.
    assertEquals(0, plan.deliverNow());
  }

  @Test
  void everythingCoveredMeansNothingToDo() {
    assertEquals(new OpenDeliveryPlan(0, 0, 0), OpenDeliveryPlan.of(64, 0, 64, 0, 0, 500));
  }
}
