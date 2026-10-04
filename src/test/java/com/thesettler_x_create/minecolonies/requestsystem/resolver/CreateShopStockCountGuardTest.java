package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * The pickup block's handler is a view of the racks, so adding it to the rack count counts every
 * item twice. The hut buffer is the colony side and is not rack stock either.
 */
class CreateShopStockCountGuardTest {
  private static final String DIR =
      "src/main/java/com/thesettler_x_create/minecolonies/requestsystem/resolver/";

  @Test
  void pickupViewIsNotCountedOnTopOfTheRacks() throws Exception {
    String stock = Files.readString(Path.of(DIR + "CreateShopStockResolver.java"));
    String planning = Files.readString(Path.of(DIR + "CreateShopResolverPlanning.java"));

    assertFalse(stock.contains("getAvailableFromPickup"));
    assertFalse(planning.contains("getAvailableFromPickup"));
  }

  @Test
  void rackCountSkipsTheHutBufferAndOnlyOwesWhatSpilledOver() throws Exception {
    String planning = Files.readString(Path.of(DIR + "CreateShopResolverPlanning.java"));

    assertTrue(planning.contains("tile.getLoadedRacks()"));
    assertFalse(planning.contains("for (BlockPos pos : tile.getBuilding().getContainers())"));
    assertTrue(planning.contains("owedToGauges - inHutBuffer"));
  }
}
