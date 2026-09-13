package com.thesettler_x_create.stock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class StockAgingTest {
  private static final long FIVE_MINUTES = 6_000L;

  private static StockAging<String> aging() {
    return new StockAging<>(String::equals, key -> key);
  }

  private static List<ReservedAmount<String>> counts(Object... keyAndAmount) {
    java.util.ArrayList<ReservedAmount<String>> list = new java.util.ArrayList<>();
    for (int i = 0; i < keyAndAmount.length; i += 2) {
      list.add(new ReservedAmount<>((String) keyAndAmount[i], (Integer) keyAndAmount[i + 1]));
    }
    return list;
  }

  @Test
  void stockBecomesAgedAfterTheMinimumAge() {
    StockAging<String> aging = aging();
    aging.update(counts("iron", 64), 1_000L);

    assertEquals(0, aging.agedAmount("iron", 1_000L + FIVE_MINUTES - 1, FIVE_MINUTES));
    assertEquals(64, aging.agedAmount("iron", 1_000L + FIVE_MINUTES, FIVE_MINUTES));
  }

  @Test
  void newArrivalsStartTheirOwnClock() {
    StockAging<String> aging = aging();
    aging.update(counts("iron", 64), 0L);
    aging.update(counts("iron", 100), 3_000L);

    assertEquals(64, aging.agedAmount("iron", FIVE_MINUTES, FIVE_MINUTES));
    assertEquals(100, aging.agedAmount("iron", 3_000L + FIVE_MINUTES, FIVE_MINUTES));
  }

  @Test
  void shrinkingStockRemovesTheOldestFirst() {
    StockAging<String> aging = aging();
    aging.update(counts("iron", 64), 0L);
    aging.update(counts("iron", 100), 3_000L);

    // The shopkeeper carried 40 old ones away.
    assertTrue(aging.update(counts("iron", 60), 6_000L));

    assertEquals(24, aging.agedAmount("iron", 6_000L, FIVE_MINUTES));
    assertEquals(60, aging.agedAmount("iron", 9_000L, FIVE_MINUTES));
  }

  @Test
  void itemKindsWithoutUnreservedStockAreForgotten() {
    StockAging<String> aging = aging();
    aging.update(counts("iron", 64, "gold", 8), 0L);

    aging.update(counts("gold", 8), 100L);
    assertEquals(0, aging.agedAmount("iron", FIVE_MINUTES, FIVE_MINUTES));
    assertEquals(1, aging.keyCount());

    aging.update(counts("gold", 0), 200L);
    assertEquals(0, aging.keyCount());
  }

  @Test
  void unchangedCountsChangeNothing() {
    StockAging<String> aging = aging();
    aging.update(counts("iron", 64), 0L);

    assertFalse(aging.update(counts("iron", 64), 500L));
    assertEquals(64, aging.agedAmount("iron", FIVE_MINUTES, FIVE_MINUTES));
  }

  @Test
  void keysAreComparedAndStoredThroughTheGivenFunctions() {
    StockAging<String> aging =
        new StockAging<>(String::equalsIgnoreCase, key -> key.toLowerCase(java.util.Locale.ROOT));
    aging.update(counts("IRON", 10), 0L);
    aging.update(counts("iron", 10), 10L);

    assertEquals(1, aging.keyCount());
    assertEquals(10, aging.agedAmount("Iron", FIVE_MINUTES, FIVE_MINUTES));
    assertEquals("iron", aging.stored().get(0).key());
  }

  @Test
  void restoreKeepsTheSavedAges() {
    StockAging<String> aging = aging();
    aging.update(counts("iron", 64), 0L);
    aging.update(counts("iron", 100), 3_000L);

    StockAging<String> restored = aging();
    restored.restore(aging.stored());

    assertEquals(aging.stored(), restored.stored());
    assertEquals(64, restored.agedAmount("iron", FIVE_MINUTES, FIVE_MINUTES));
  }

  @Test
  void restoreSkipsBrokenBatches() {
    StockAging<String> aging = aging();
    aging.restore(
        java.util.Arrays.asList(
            null, new StockAging.Batch<>(null, 5, 0L), new StockAging.Batch<>("iron", 0, 0L)));

    assertEquals(0, aging.keyCount());
  }
}
