package com.thesettler_x_create.minecolonies.tileentity;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * The rack a Create packager unpacks into is the shop's inbound bottleneck, and the shopkeeper
 * keeps it clear.
 *
 * <p>A packager unpacks into exactly one block. Once that rack is full nothing else arrives from
 * the network, however much room the shop's other racks have, and the goods sit in the network as
 * in-flight. Spreading them over the other racks is free in bookkeeping terms: every count the shop
 * keeps is over all racks together, so no number changes and the courier gathering a delivery reads
 * them all through the hut block either way.
 */
class ShopArrivalRackDrainGuardTest {
  private static final String MAIN = "src/main/java/com/thesettler_x_create/";
  private static final Path TILE =
      Path.of(MAIN + "minecolonies/tileentity/TileEntityCreateShop.java");

  @Test
  void onlyARackAPackagerUnpacksIntoIsDrained() throws Exception {
    String body = methodBody(TILE, "public Tuple<BlockPos, ItemStack> findNextArrivalRackItem()");

    assertTrue(
        body.contains(
            "CreatePackagerBridge.isPackagerUnpackingInto(getLevel(), rack.getBlockPos())"),
        "without this every rack would be drained, which is carrying for its own sake");
    assertTrue(
        body.contains("rack.getFreeSlots() >= minFree"),
        "the shopkeeper only carries once the arrival rack gets tight");
    assertTrue(
        body.contains("Config.ARRIVAL_RACK_MIN_FREE_SLOTS.get()") && body.contains("minFree <= 0"),
        "the threshold must be configurable and 0 must turn the whole thing off");
    assertTrue(body.contains("racks.size() < 2"), "with one rack there is nowhere to spread to");
  }

  @Test
  void theGoodsNeverLandBackInTheRackTheyCameFrom() throws Exception {
    String access = Files.readString(Path.of(MAIN + "minecolonies/tileentity/ShopRackAccess.java"));

    assertTrue(access.contains("List<ItemStack> insertIntoRacksExcept("));
    assertTrue(
        access.contains(
            "AbstractTileEntityRack getRackForStack(ItemStack stack, @Nullable BlockPos excluded)"),
        "the exclusion has to reach the rack choice itself, not just the entry point");
    assertTrue(
        access.contains("return excluded != null && excluded.equals(loaded.pos());"),
        "all three rack searches share one exclusion check");
  }

  @Test
  void aStallLetsUnreservedRackStockMoveWithoutTheWait() throws Exception {
    String body = methodBody(TILE, "private List<RackStackBudget> collectMovableRackBudgets(");

    // Five minutes of waiting on top of "the network could not deliver" keeps the racks shut over
    // goods nobody asked to keep there, and the pickup only ever sees what reached the hut buffer.
    assertTrue(body.contains("hasCapacityStall()"));
    assertTrue(body.contains("? 0L"));
  }

  private static String methodBody(Path source, String signature) throws Exception {
    String text = Files.readString(source);
    int start = text.indexOf(signature);
    assertTrue(start > 0, signature + " not found");
    int end = text.indexOf("\n  /**", start + signature.length());
    return text.substring(start, end > 0 ? end : text.length());
  }
}
