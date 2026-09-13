package com.thesettler_x_create.stock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ShopStockAccountingTest {

  @Test
  void reservedForOthersSubtractsTheOwnShare() {
    assertEquals(40, ShopStockAccounting.reservedForOthers(64, 24));
    assertEquals(0, ShopStockAccounting.reservedForOthers(24, 64));
    assertEquals(64, ShopStockAccounting.reservedForOthers(64, -5));
  }

  @Test
  void usableRackStockExcludesOtherRequestsReservations() {
    assertEquals(24, ShopStockAccounting.usableRackStock(64, 40));
    assertEquals(0, ShopStockAccounting.usableRackStock(32, 40));
    assertEquals(32, ShopStockAccounting.usableRackStock(32, -1));
  }

  @Test
  void totalAvailableAddsAllSourcesAndNeverGoesNegative() {
    assertEquals(100, ShopStockAccounting.totalAvailable(60, 30, 10));
    assertEquals(0, ShopStockAccounting.totalAvailable(-50, 10, 0));
  }

  @Test
  void canCoverAcceptsTheFullNeedOrTheRequestersMinimum() {
    assertTrue(ShopStockAccounting.canCover(256, 256, 256));
    assertTrue(ShopStockAccounting.canCover(100, 256, 64));
    assertFalse(ShopStockAccounting.canCover(100, 256, 256));
    // A minimum above the need still accepts once the need itself is covered.
    assertTrue(ShopStockAccounting.canCover(16, 16, 32));
  }

  @Test
  void reservableFromRackIsCappedByFreeStockAndMissingReservation() {
    // 64 in the rack, 40 reserved for the item, request needs 32 and has 10 reserved.
    assertEquals(22, ShopStockAccounting.reservableFromRack(64, 40, 32, 10));
    // Free stock is the limit.
    assertEquals(4, ShopStockAccounting.reservableFromRack(44, 40, 32, 0));
    // Already fully reserved.
    assertEquals(0, ShopStockAccounting.reservableFromRack(64, 0, 32, 32));
  }

  @Test
  void topupNeedIsWhatReservationAndRackDoNotCover() {
    assertEquals(192, ShopStockAccounting.topupNeed(256, 0, 64));
    assertEquals(0, ShopStockAccounting.topupNeed(256, 200, 64));
    assertEquals(256, ShopStockAccounting.topupNeed(256, -1, -1));
  }

  @Test
  void networkOrderAmountCountsOrdersOnTheirWay() {
    assertEquals(64, ShopStockAccounting.networkOrderAmount(192, 128));
    assertEquals(0, ShopStockAccounting.networkOrderAmount(64, 128));
    assertEquals(64, ShopStockAccounting.networkOrderAmount(64, -1));
  }

  @Test
  void pendingCountNeverDropsBelowTheReservation() {
    assertEquals(192, ShopStockAccounting.pendingCount(64, 192));
    assertEquals(64, ShopStockAccounting.pendingCount(64, 0));
    assertEquals(0, ShopStockAccounting.pendingCount(-3, -1));
  }

  @Test
  void unreservedStockNeverGoesNegative() {
    assertEquals(24, ShopStockAccounting.unreservedStock(64, 40));
    assertEquals(0, ShopStockAccounting.unreservedStock(32, 40));
  }

  @Test
  void extractablePrefersTheReservationWhenThereIsOne() {
    assertEquals(16, ShopStockAccounting.extractable(64, 16, 128));
    assertEquals(64, ShopStockAccounting.extractable(64, 0, 128));
    assertEquals(20, ShopStockAccounting.extractable(64, 0, 20));
  }
}
