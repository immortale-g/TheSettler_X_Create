package com.thesettler_x_create.blockentity;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * The inflight rules (per-tuple cap, exact match without a tuple, item-type fallback, notice dedup,
 * re-asking after load, owner handling) are tested directly in {@code InflightBookTest} and the
 * saved format in {@code InflightNbtTest}. This pins that the block entity's ledger uses them with
 * the item stack comparisons those tests stand for.
 */
class ShopInflightLedgerDelegationGuardTest {
  private static final Path LEDGER =
      Path.of("src/main/java/com/thesettler_x_create/blockentity/ShopInflightLedger.java");

  @Test
  void ledgerDelegatesToTheInflightBookWithItemStackMatching() throws Exception {
    String source = Files.readString(LEDGER);

    assertTrue(source.contains("private final InflightBook<ItemStack> book ="));
    assertTrue(source.contains("ItemStack::isSameItemSameComponents,"));
    assertTrue(source.contains("ItemStack::isSameItem,"));
    assertTrue(source.contains("return book.remainingFor(requestUuid, stackKey);"));
    assertTrue(source.contains("int removed = book.cancel(requestUuid);"));
  }

  @Test
  void ledgerSavesThroughInflightNbtUnderTheExistingTags() throws Exception {
    String source = Files.readString(LEDGER);

    assertTrue(source.contains("private static final String TAG_INFLIGHT = \"Inflight\";"));
    assertTrue(
        source.contains(
            "private static final String TAG_INFLIGHT_BASELINES = \"InflightBaselines\";"));
    assertTrue(source.contains("InflightNbt.writeEntries(book.storedEntries()"));
    assertTrue(source.contains("InflightNbt.readEntries("));
    assertTrue(source.contains("InflightNbt.readBaselines("));
  }
}
