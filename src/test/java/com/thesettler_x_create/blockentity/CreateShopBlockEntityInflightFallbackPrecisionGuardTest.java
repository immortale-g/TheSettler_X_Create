package com.thesettler_x_create.blockentity;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Seam-audit finding s1-5: the drift-recovery fallback in {@code getInflightRemaining}/{@code
 * consumeInflightMatches}/{@code cancelInflightMatches} (used when a citizen rename/reassignment
 * makes the recorded requester/address stop matching) blanks the requester and address filters, but
 * kept using item-loose matching ({@code isSameItem}, ignoring components) on top of that - meaning
 * once requester/address are both blank, item type was the only signal left, so two unrelated
 * requests for component-different variants of the same item (e.g. differently enchanted books)
 * could consume/cancel each other's inflight entries. Fixed by requiring an exact item+component
 * match specifically when both requester and address are blank.
 */
class CreateShopBlockEntityInflightFallbackPrecisionGuardTest {

  @Test
  void exactItemMatchIsRequiredWhenRequesterAndAddressAreBothBlank() throws Exception {
    String source =
        Files.readString(
            Path.of("src/main/java/com/thesettler_x_create/blockentity/ShopInflightLedger.java"));

    int helper = source.indexOf("private static boolean matchesForInflightLookup(");
    assertTrue(helper > 0);
    String helperBody = source.substring(helper, Math.min(source.length(), helper + 400));
    assertTrue(helperBody.contains("if (requireExactItemMatch) {"));
    assertTrue(helperBody.contains("return matches(entryStack, stackKey);"));
    assertTrue(helperBody.contains("return matchesForInflightRecovery(entryStack, stackKey);"));

    // All three matching loops must derive strictness from requester/destination being blank, and
    // must route through the new lookup helper rather than the raw loose-matching function.
    long requireExactDerivations =
        source
            .lines()
            .filter(
                line ->
                    line.contains(
                        "requireExactItemMatch = requester.isEmpty() && destination.isEmpty();"))
            .count();
    assertTrue(
        requireExactDerivations == 3,
        "expected requireExactItemMatch derived in getInflightRemaining, consumeInflightMatches"
            + " and cancelInflightMatches, found "
            + requireExactDerivations);

    long lookupCalls =
        source
            .lines()
            .filter(line -> line.contains("matchesForInflightLookup(entry.stackKey, stackKey"))
            .count();
    assertTrue(lookupCalls == 3, "expected 3 call sites routed through the new lookup helper");
  }
}
