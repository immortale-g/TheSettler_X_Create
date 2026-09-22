package com.thesettler_x_create.minecolonies.building;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Nothing already stored in the shop may be handed to a courier to make room for a delivery.
 *
 * <p>On a building it cannot fill, {@code InventoryUtils.forceItemStackToItemHandler} falls back to
 * pulling a stack that {@code isItemStackInRequest} says nobody needs and putting the delivery in
 * its place; the pulled stack goes back to the courier. For this shop there is nothing to give
 * away: every rack stack is Create stock, every hut stack is either gauge goods or surplus waiting
 * for a pickup. Worse, since the colony side may only fill a rack when the hut buffer is full, that
 * fallback could pull a rack stack and then fail to put anything back into the slot it emptied.
 *
 * <p>Sorting is off for the same reason: it empties every slot and lays the items out again from
 * the front, which would push everything at the hut buffer and drop what no longer fits.
 *
 * <p>Both overrides are pinned by source. That they still override something is checked by the
 * compiler through {@code @Override}: if MineColonies renames either method or changes its
 * signature, this module stops building rather than silently losing the rule.
 */
class ShopKeepsWhatItStoresGuardTest {
  private static final Path SOURCE =
      Path.of(
          "src/main/java/com/thesettler_x_create/minecolonies/building/BuildingCreateShop.java");

  @Test
  void everyStoredStackCountsAsSpokenFor() throws Exception {
    String source = Files.readString(SOURCE);
    int start = source.indexOf("public boolean isItemStackInRequest(");
    assertTrue(start > 0, "the isItemStackInRequest override is gone");
    String body = source.substring(start, source.indexOf("\n  /**", start));

    assertTrue(
        body.contains("return stack != null && !stack.isEmpty();"),
        "the shop no longer claims all of its stock, so a courier may swap a stack out again");
  }

  @Test
  void sortingIsOff() throws Exception {
    String source = Files.readString(SOURCE);
    int start = source.indexOf("public void sort(");
    assertTrue(start > 0, "the sort override is gone");
    String body = source.substring(start, source.indexOf("\n  /**", start));

    assertTrue(
        !body.contains("SortingUtils") && !body.contains("super.sort("),
        "sorting a half-writable inventory drops what the hut buffer cannot hold");
  }
}
