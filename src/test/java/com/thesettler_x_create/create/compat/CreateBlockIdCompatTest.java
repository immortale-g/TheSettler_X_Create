package com.thesettler_x_create.create.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/**
 * Holds the eleven Create ids the placement handlers address by name against the Create jar.
 *
 * <p>Nothing here is a compile-time reference: the handlers build {@link ResourceLocation}s from
 * strings, because the composite casings have no item of their own and the belt is not a single
 * block. If Create renames one of them, the handler still registers, still compiles, and simply
 * never matches - the colony builder then stands in front of a block it cannot place and says
 * nothing about it.
 *
 * <p>The ids are read out of the production classes rather than repeated here, so this cannot end
 * up checking its own copy of a name that the handler has since changed.
 */
class CreateBlockIdCompatTest {
  @Test
  void everyCompositeCasingAndTheItemItCostsStillExistsInCreate() {
    Map<ResourceLocation, ResourceLocation> remap =
        CreatePlacementHandlers.compositeBlockRequiredItems();

    assertEquals(
        6,
        remap.size(),
        "the composite casing remap no longer covers six blocks; this test was written for the"
            + " andesite and brass casings of shaft, cogwheel and large cogwheel");

    Set<ResourceLocation> ids = new LinkedHashSet<>(remap.keySet());
    ids.addAll(remap.values());
    for (ResourceLocation id : ids) {
      assertCreateStillShips(id);
    }
  }

  @Test
  void theThreeIdsTheBeltHandlerUsesStillExistInCreate() {
    assertCreateStillShips(CreateBeltPlacementHandler.BELT_ID);
    assertCreateStillShips(CreateBeltPlacementHandler.SHAFT_ID);
    assertCreateStillShips(CreateBeltPlacementHandler.BELT_CONNECTOR_ID);
  }

  /**
   * A block or item exists in Create when the jar carries its blockstate or its item model; belts
   * and casings are blocks, {@code belt_connector} is an item, and both kinds are addressed the
   * same way by the handlers.
   */
  private static void assertCreateStillShips(ResourceLocation id) {
    assertEquals(
        "create",
        id.getNamespace(),
        id + " is not a Create id, so this test cannot say whether it exists");

    boolean known =
        onClasspath("assets/create/blockstates/" + id.getPath() + ".json")
            || onClasspath("assets/create/models/item/" + id.getPath() + ".json");

    assertTrue(
        known,
        "Create no longer ships "
            + id
            + " (neither a blockstate nor an item model). Either the block was renamed upstream -"
            + " the placement handler that names it then quietly stops working - or the Create jar"
            + " is missing from the test classpath.");
  }

  private static boolean onClasspath(String resource) {
    return CreateBlockIdCompatTest.class.getClassLoader().getResource(resource) != null;
  }
}
