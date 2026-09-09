package com.thesettler_x_create.minecolonies.building;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ShopPermaRequestManagerGuardTest {

  private static String source() throws Exception {
    return Files.readString(
        Path.of(
            "src/main/java/com/thesettler_x_create/minecolonies/building/ShopPermaRequestManager.java"));
  }

  @Test
  void loadPermaClearsBeforeRepopulatingFromNbt() throws Exception {
    String source = source();
    int loadStart = source.indexOf("void loadPerma(CompoundTag compound) {");
    int loadEnd = source.indexOf("void savePerma(");
    assertTrue(loadStart >= 0 && loadEnd > loadStart, "loadPerma method not found");
    String loadBody = source.substring(loadStart, loadEnd);

    // permaOres must be cleared before re-adding from NBT, otherwise a second load() call on the
    // same manager instance would leak entries from whatever was previously loaded.
    assertTrue(loadBody.indexOf("permaOres.clear();") < loadBody.indexOf("permaOres.add(id);"));
  }

  @Test
  void permaOresRoundTripUsesSymmetricTagConstants() throws Exception {
    String source = source();

    // loadPerma and savePerma must read/write the same two tag keys, or a save/load cycle
    // silently drops the perma-ore list or the wait-full-stack flag.
    assertTrue(source.contains("compound.contains(BuildingCreateShop.TAG_PERMA_ORES)"));
    assertTrue(source.contains("tag.put(BuildingCreateShop.TAG_PERMA_ORES, list)"));
    assertTrue(source.contains("compound.getBoolean(BuildingCreateShop.TAG_PERMA_WAIT_FULL)"));
    assertTrue(source.contains("tag.putBoolean(BuildingCreateShop.TAG_PERMA_WAIT_FULL, true)"));
  }

  @Test
  void clearPermaPendingRemovesExhaustedCountEntries() throws Exception {
    String source = source();
    int start = source.indexOf("void clearPermaPending(IRequest<?> request) {");
    int end = source.indexOf("void loadPerma(");
    assertTrue(start >= 0 && end > start, "clearPermaPending method not found");
    String body = source.substring(start, end);

    // Without removing exhausted entries, permaPendingCounts would grow with stale zero-count
    // keys forever, and a stale positive leftover would suppress future perma-requests for that
    // item even after every pending request for it has actually resolved.
    assertTrue(body.contains("permaPendingCounts.merge(pending.itemId, -pending.count"));
    assertTrue(body.contains("permaPendingCounts.remove(pending.itemId);"));
  }
}
