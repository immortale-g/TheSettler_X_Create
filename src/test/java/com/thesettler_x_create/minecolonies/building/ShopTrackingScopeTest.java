package com.thesettler_x_create.minecolonies.building;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ShopTrackingScopeTest {

  @Test
  void parsesSingleScopesAndAll() {
    assertEquals(
        Optional.of(EnumSet.of(ShopTrackingScope.STOCK_AGES)),
        ShopTrackingScope.parse("stock-ages"));
    assertEquals(
        Optional.of(EnumSet.of(ShopTrackingScope.INFLIGHT)), ShopTrackingScope.parse(" Inflight "));
    assertEquals(
        Optional.of(EnumSet.allOf(ShopTrackingScope.class)), ShopTrackingScope.parse("all"));
  }

  @Test
  void rejectsUnknownArguments() {
    assertTrue(ShopTrackingScope.parse("settings").isEmpty());
    assertTrue(ShopTrackingScope.parse(null).isEmpty());
  }

  @Test
  void reportSumsShopsAndScopes() {
    ShopTrackingResetReport first = new ShopTrackingResetReport();
    first.countShop();
    first.add(ShopTrackingScope.RESERVATIONS, 3);
    first.noteInflightBeforeReset(2);
    ShopTrackingResetReport second = new ShopTrackingResetReport();
    second.countShop();
    second.add(ShopTrackingScope.RESERVATIONS, 1);
    second.add(ShopTrackingScope.INFLIGHT, -5);

    first.addAll(second);

    assertEquals(2, first.shops());
    assertEquals(4, first.removed(ShopTrackingScope.RESERVATIONS));
    assertEquals(0, first.removed(ShopTrackingScope.INFLIGHT));
    assertEquals(2, first.forgottenInflight());
    assertEquals("shops=2, reservations=4, inflight=0", first.summary());
  }

  @Test
  void settingsAreNoScope() {
    Set<String> ids = new java.util.HashSet<>();
    for (ShopTrackingScope scope : ShopTrackingScope.values()) {
      ids.add(scope.id());
    }
    assertEquals(
        Set.of("reservations", "inflight", "stock-ages", "flow-states", "gauge", "runtime"), ids);
  }
}
