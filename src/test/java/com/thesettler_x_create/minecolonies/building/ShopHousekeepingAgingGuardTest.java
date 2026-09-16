package com.thesettler_x_create.minecolonies.building;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class ShopHousekeepingAgingGuardTest {
  private static final Path TILE =
      Path.of(
          "src/main/java/com/thesettler_x_create/minecolonies/tileentity/TileEntityCreateShop.java");
  private static final Path BUILDING =
      Path.of(
          "src/main/java/com/thesettler_x_create/minecolonies/building/BuildingCreateShop.java");

  @Test
  void shopkeeperOnlyMovesStockThatSatUnreservedLongEnough() throws Exception {
    String source = Files.readString(TILE);

    // hasUnreservedRackItems, findNextUnreservedRackItem, extractFromRack and
    // moveUnreservedRackStacksToHut all read the aged budgets.
    assertEquals(
        4,
        Pattern.compile(
                "= collectMovableRackBudgets\\(pickup\\)|: collectMovableRackBudgets\\(pickup\\)")
            .matcher(source)
            .results()
            .count());
    assertTrue(source.contains("stockAging.update(unreserved, now)"));
    assertTrue(source.contains("Config.HOUSEKEEPING_MIN_AGE_TICKS.getAsLong()"));
    assertTrue(source.contains("stockAging.save(tag, registries);"));
    assertTrue(source.contains("stockAging.load(tag, registries);"));
  }

  @Test
  void courierPickupNeverTakesRackStockOrReservedItems() throws Exception {
    String source = Files.readString(BUILDING);

    assertTrue(source.contains("public int buildingRequiresCertainAmountOfItem("));
    assertTrue(
        source.contains("return pickupKeepPolicy.takeableForPickup(stack, localAlreadyKept);"));
  }

  @Test
  void hutBufferExtractionsAreNotBookedAsDeliveryPickups() throws Exception {
    String source = Files.readString(TILE);

    assertTrue(source.contains("if (!isRackSlot(slot)) {"));
    assertTrue(source.contains("slot < observedHut.getSlots() - hutBuffer.getSlots()"));
  }
}
