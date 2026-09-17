package com.thesettler_x_create.stock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
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
  void drawableFromNetworkKeepsTheShopsOwnMinimum() {
    assertEquals(36, ShopStockAccounting.drawableFromNetwork(100, 64));
    assertEquals(0, ShopStockAccounting.drawableFromNetwork(64, 64));
    assertEquals(0, ShopStockAccounting.drawableFromNetwork(10, 64));
    assertEquals(10, ShopStockAccounting.drawableFromNetwork(10, 0));
    assertEquals(10, ShopStockAccounting.drawableFromNetwork(10, -5));
  }

  @Test
  void totalAvailableAddsAllSourcesAndNeverGoesNegative() {
    assertEquals(100, ShopStockAccounting.totalAvailable(60, 30, 10));
    assertEquals(0, ShopStockAccounting.totalAvailable(-50, 10, 0));
  }

  @Test
  void canCloseShortOnlyWithTheMinimumAndNothingMoreToCome() {
    assertTrue(ShopStockAccounting.canCloseShort(20, 1, 0, 0, false));
    assertTrue(ShopStockAccounting.canCloseShort(32, 32, 0, 0, false));
    // Below the minimum the requester does not accept it yet.
    assertFalse(ShopStockAccounting.canCloseShort(16, 32, 0, 0, false));
    // Nothing delivered is never a finished request.
    assertFalse(ShopStockAccounting.canCloseShort(0, 0, 0, 0, false));
    // Anything still reserved, in the racks or on its way can still be delivered.
    assertFalse(ShopStockAccounting.canCloseShort(20, 1, 8, 0, false));
    assertFalse(ShopStockAccounting.canCloseShort(20, 1, 0, 8, false));
    assertFalse(ShopStockAccounting.canCloseShort(20, 1, 0, 0, true));
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
  void deliveryChunksAreAtMostOneStackEach() {
    assertEquals(List.of(64, 64, 64, 64), ShopStockAccounting.deliveryChunks(256, 64));
    assertEquals(List.of(64, 36), ShopStockAccounting.deliveryChunks(100, 64));
    assertEquals(List.of(16, 16, 1), ShopStockAccounting.deliveryChunks(33, 16));
    assertEquals(List.of(1, 1), ShopStockAccounting.deliveryChunks(2, 1));
    assertEquals(List.of(), ShopStockAccounting.deliveryChunks(0, 64));
    // A broken stack size must not loop forever.
    assertEquals(List.of(1, 1, 1), ShopStockAccounting.deliveryChunks(3, 0));
  }

  @Test
  void anArrivalIsReservedUpToTheFreeRackStock() {
    // 64 arrived, the rack holds 64, nothing else reserved.
    assertEquals(64, ShopStockAccounting.arrivalReservation(64, 64, 0));
    // A legacy reservation made when ordering already covers these goods.
    assertEquals(0, ShopStockAccounting.arrivalReservation(64, 64, 64));
    assertEquals(20, ShopStockAccounting.arrivalReservation(64, 84, 64));
    assertEquals(0, ShopStockAccounting.arrivalReservation(64, 10, 30));
  }

  @Test
  void rackStockIsNotReservedForGoodsTheRequestOrderedItself() {
    assertEquals(36, ShopStockAccounting.rackReservationNeed(100, 64));
    assertEquals(0, ShopStockAccounting.rackReservationNeed(64, 100));
    assertEquals(64, ShopStockAccounting.rackReservationNeed(64, -1));
  }

  @Test
  void pickupKeepsAllRackStockAndEveryReservedItem() {
    assertEquals(64, ShopStockAccounting.pickupKeepAmount(64, 16));
    // 16 reserved but only 10 in the racks: 6 reserved items in the hut buffer stay too.
    assertEquals(16, ShopStockAccounting.pickupKeepAmount(10, 16));
    assertEquals(0, ShopStockAccounting.pickupKeepAmount(0, -1));
  }

  @Test
  void pickupTakesOnlyWhatIsBeyondTheKeepAmount() {
    // Rack slot of 64 while 64 must stay: nothing.
    assertEquals(0, ShopStockAccounting.pickupTakeable(64, 64, 0));
    // Hut buffer slot after the racks kept all 64: everything.
    assertEquals(20, ShopStockAccounting.pickupTakeable(20, 64, 64));
    // Partly covered.
    assertEquals(12, ShopStockAccounting.pickupTakeable(20, 72, 64));
    assertEquals(20, ShopStockAccounting.pickupTakeable(20, 0, 0));
  }

  @Test
  void extractablePrefersTheReservationWhenThereIsOne() {
    assertEquals(16, ShopStockAccounting.extractable(64, 16, 128));
    assertEquals(64, ShopStockAccounting.extractable(64, 0, 128));
    assertEquals(20, ShopStockAccounting.extractable(64, 0, 20));
  }
}
