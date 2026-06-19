package com.thesettler_x_create.blockentity;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Guard tests for the InflightEntry NBT save/load roundtrip.
 *
 * <p>InflightEntry persists per-delivery inflight state across world reloads. Any field that is
 * saved but not loaded (or vice-versa) creates silent state drift — the block entity would report
 * different inflight counts after reload, causing spurious lost-package dialogs.
 */
class CreateShopInflightEntrySerializationGuardTest {
  private static final String SOURCE =
      "src/main/java/com/thesettler_x_create/blockentity/CreateShopBlockEntity.java";

  @Test
  void inflightEntrySavesAllMutableFields() throws Exception {
    String source = Files.readString(Path.of(SOURCE));

    // Each field written during save.
    assertTrue(source.contains("data.put(\"stack\", entry.stackKey.save(registries))"));
    assertTrue(source.contains("data.putInt(\"remaining\", entry.remaining)"));
    assertTrue(source.contains("data.putLong(\"requestedAt\", entry.requestedAt)"));
    // notified is conditionally written (only true case to save space):
    assertTrue(source.contains("data.putBoolean(\"notified\", true)"));
    assertTrue(source.contains("data.putString(\"requester\", entry.requesterName)"));
    assertTrue(source.contains("data.putString(\"address\", entry.address)"));
  }

  @Test
  void inflightEntryLoadsAllPersistedFields() throws Exception {
    String source = Files.readString(Path.of(SOURCE));

    // Each field read during load.
    assertTrue(source.contains("entry.getCompound(\"stack\")"));
    assertTrue(source.contains("entry.getInt(\"remaining\")"));
    assertTrue(source.contains("entry.getLong(\"requestedAt\")"));
    assertTrue(source.contains("entry.getString(\"requester\")"));
    assertTrue(source.contains("entry.getString(\"address\")"));
  }

  @Test
  void notifiedIsResetToFalseOnLoad() throws Exception {
    String source = Files.readString(Path.of(SOURCE));

    // notified is intentionally not reloaded — interactions are re-armed after world load.
    // If this invariant breaks, the overdue-notice system would not fire for still-open
    // inflight entries after reload.
    assertTrue(source.contains("inflight.notified = false"));
    // Verify it is NOT read back from NBT (load path must not call getBoolean("notified")).
    // We check the load section specifically by verifying the pattern is absent near the load loop.
    int loadStart = source.indexOf("inflightEntries.clear()");
    int loadEnd = source.indexOf("inflightBaselines.clear()", loadStart);
    String loadSection = source.substring(loadStart, loadEnd);
    assertFalse(loadSection.contains("getBoolean(\"notified\")"));
  }

  @Test
  void inflightEntryClassHasExpectedFields() throws Exception {
    String source = Files.readString(Path.of(SOURCE));

    // Structural check: InflightEntry must have exactly these public fields.
    assertTrue(source.contains("public final ItemStack stackKey;"));
    assertTrue(source.contains("public int remaining;"));
    assertTrue(source.contains("public final long requestedAt;"));
    assertTrue(source.contains("public final String requesterName;"));
    assertTrue(source.contains("public final String address;"));
    assertTrue(source.contains("public boolean notified;"));
  }

  @Test
  void inflightEntriesAreSkippedIfStackIsEmptyOrRemainingIsZero() throws Exception {
    String source = Files.readString(Path.of(SOURCE));

    // Guard: malformed entries (empty stack or zero remaining) must be dropped during load to
    // prevent phantom inflight state that can never be consumed.
    assertTrue(source.contains("!stack.isEmpty() && remaining > 0"));
  }
}
