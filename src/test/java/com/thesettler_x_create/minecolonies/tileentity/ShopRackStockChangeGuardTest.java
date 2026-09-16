package com.thesettler_x_create.minecolonies.tileentity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Every rack movement the shop causes or observes is reported to the inflight tracking. A missing
 * report either reads as an arrival (an insertion) or hides one (a removal); the baseline rules
 * themselves are tested in {@code InflightBookTest}.
 */
class ShopRackStockChangeGuardTest {
  private static final String MAIN = "src/main/java/com/thesettler_x_create/";

  private static long count(String source, String needle) {
    return Pattern.compile(Pattern.quote(needle)).matcher(source).results().count();
  }

  @Test
  void hutDoorReportsRackSlotsOnly() throws Exception {
    String tile =
        Files.readString(Path.of(MAIN + "minecolonies/tileentity/TileEntityCreateShop.java"));

    assertTrue(tile.contains("noteRackStockChange(taken, -taken.getCount());"));
    assertTrue(tile.contains("if (isRackSlot(slot)) {"));
    assertTrue(tile.contains("pickup.noteRackStockChange(key, delta);"));
  }

  @Test
  void shopkeeperMovesAreReported() throws Exception {
    String tile =
        Files.readString(Path.of(MAIN + "minecolonies/tileentity/TileEntityCreateShop.java"));
    String rackAccess =
        Files.readString(Path.of(MAIN + "minecolonies/tileentity/ShopRackAccess.java"));

    assertTrue(tile.contains("noteRackStockChange(extracted, -extracted.getCount());"));
    assertTrue(tile.contains("noteRackStockChange(extracted, -inserted);"));
    assertEquals(2, count(rackAccess, "owner.noteRackStockChange("));
  }

  @Test
  void packagingAndVirtualExtractionAreReported() throws Exception {
    String output =
        Files.readString(Path.of(MAIN + "blockentity/CreateShopOutputBlockEntity.java"));
    String virtual =
        Files.readString(Path.of(MAIN + "create/VirtualCreateNetworkItemHandler.java"));

    assertTrue(output.contains("shop.noteRackStockChange(extracted, -extracted.getCount());"));
    assertTrue(
        virtual.contains("shopBlockEntity.noteRackStockChange(extracted, -extracted.getCount());"));
  }
}
