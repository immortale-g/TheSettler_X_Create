package com.thesettler_x_create.minecolonies.tileentity;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.minecolonies.api.tileentities.AbstractTileEntityRack;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * The shop hut must never count as one of its own racks.
 *
 * <p>MineColonies has {@code AbstractTileEntityColonyBuilding extend TileEntityRack}, so the hut
 * passes an {@code instanceof AbstractTileEntityRack} test, and it is listed in its own containers.
 * Counting it made the hut buffer part of the rack stock. Since a pickup keeps rack stock, anything
 * that reached the buffer kept itself there: three leftover torches sat in the hut on 2026-09-18
 * while courier after courier was called and allowed to take nothing.
 */
class ShopRackAccessHutExclusionGuardTest {

  @Test
  void theHutIsSkippedWhenItsRacksAreCollected() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/tileentity/ShopRackAccess.java"));
    int start = source.indexOf("List<TileEntityCreateShop.LoadedRack> getLoadedRacks()");
    assertTrue(start > 0, "getLoadedRacks not found");
    String body = source.substring(start, source.indexOf("\n  boolean ", start));

    assertTrue(
        body.contains("if (pos.equals(owner.getBlockPos())) {"),
        "getLoadedRacks no longer skips the hut's own position, so the hut buffer counts as rack"
            + " stock again and keeps itself from being picked up");
  }

  @Test
  void theHutStillIsARackAsFarAsTheGameIsConcerned() {
    // If this ever stops holding, the skip above is no longer needed - but until then it is the
    // only thing standing between the hut buffer and the rack stock.
    assertTrue(
        AbstractTileEntityRack.class.isAssignableFrom(
            com.minecolonies.api.tileentities.AbstractTileEntityColonyBuilding.class),
        "MineColonies no longer derives colony buildings from TileEntityRack");
  }
}
