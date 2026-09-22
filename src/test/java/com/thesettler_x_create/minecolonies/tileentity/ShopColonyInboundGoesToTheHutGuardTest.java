package com.thesettler_x_create.minecolonies.tileentity;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * What a courier brings to the shop belongs in the hut buffer.
 *
 * <p>Create always delivers into the racks. A rack slot a courier filled is capacity the stock
 * network cannot deliver into, so its goods stay inflight until something frees the rack up again;
 * with two racks that is quick to hit. The colony side therefore fills the hut buffer, and only
 * falls back to the racks when the buffer is full.
 *
 * <p>The wiring is pinned here rather than exercised, because the tile entity extends a
 * MineColonies engine type that does not come up in a unit test. That the wrapper honours the rule
 * is a real test in {@code ObservedHutItemHandlerInsertPolicyFmlTest}.
 */
class ShopColonyInboundGoesToTheHutGuardTest {
  private static final Path SOURCE =
      Path.of(
          "src/main/java/com/thesettler_x_create/minecolonies/tileentity/TileEntityCreateShop.java");

  @Test
  void theCapabilityIsHandedTheInsertionRule() throws Exception {
    String body = methodBody("public IItemHandler getItemHandlerCap(Direction side)");

    assertTrue(
        body.contains("this::mayColonyFill"),
        "the wrapper no longer gets the insertion rule, so a courier fills the racks again");
  }

  @Test
  void aRackSlotIsOnlyOfferedWhenTheHutBufferCannotTakeIt() throws Exception {
    String body = methodBody("private boolean mayColonyFill(int slot, ItemStack stack)");

    assertTrue(body.contains("if (!isRackSlot(slot)) {"), "the hut buffer must always be open");
    assertTrue(
        body.contains("return !ShopRackAccess.canInsertAtLeastOne(getInventory(), stack);"),
        "a rack must be the fallback for a full hut buffer, and nothing more");
  }

  @Test
  void goodsThatFellBackIntoARackAreBookedAsAlreadyWaited() throws Exception {
    String body = methodBody("private void noteColonyStockInRack(ItemStack key, int amount)");

    // Without this the shopkeeper leaves them there for housekeepingMinAgeTicks, five minutes by
    // default, and the rack stays shut to the stock network for that long.
    assertTrue(body.contains("Config.HOUSEKEEPING_MIN_AGE_TICKS.getAsLong()"));
    assertTrue(body.contains("stockAging.addAged(key, amount, level.getGameTime() - minAge)"));
  }

  @Test
  void onlyRealInboundIsBookedThatWay() throws Exception {
    String body = methodBody("public IItemHandler getItemHandlerCap(Direction side)");
    int rackCheck = body.indexOf("if (!isRackSlot(slot)) {");
    int booking = body.indexOf("noteColonyStockInRack(key, delta);");

    assertTrue(rackCheck > 0 && booking > rackCheck, "only a rack slot may be booked");
    assertTrue(
        body.contains("if (delta > 0) {"),
        "goods leaving a rack are not goods a courier misplaced");
  }

  private static String methodBody(String signature) throws Exception {
    String source = Files.readString(SOURCE);
    int start = source.indexOf(signature);
    assertTrue(start > 0, signature + " not found");
    int end = source.indexOf("\n  /**", start + signature.length());
    return source.substring(start, end > 0 ? end : source.length());
  }
}
