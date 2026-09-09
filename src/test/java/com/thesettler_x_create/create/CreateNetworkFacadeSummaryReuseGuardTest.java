package com.thesettler_x_create.create;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Seam-audit finding s2-5: {@code requestItems} used to call {@code getSummary()} directly and then
 * call {@code planItems}, which fetched the network-wide summary again internally - two full scans
 * per call, with the perf logger's own same-tick cooldown hiding the duplicate.
 */
class CreateNetworkFacadeSummaryReuseGuardTest {

  @Test
  void requestItemsReusesItsAlreadyFetchedSummary() throws Exception {
    String source =
        Files.readString(
            Path.of("src/main/java/com/thesettler_x_create/create/CreateNetworkFacade.java"));

    int requestItems = source.indexOf("public List<ItemStack> requestItems(");
    int requestStacksMethod = source.indexOf("public List<ItemStack> requestStacks(");
    assertTrue(requestItems > 0 && requestStacksMethod > requestItems);
    String body = source.substring(requestItems, requestStacksMethod);

    assertTrue(body.contains("planItemsFromSummary(deliverable, amount, summary)"));
    // Must not call the summary-fetching public planItems from inside requestItems anymore.
    assertTrue(!body.contains("planItems(deliverable, amount)"));
  }

  @Test
  void publicPlanItemsStillFetchesItsOwnSummaryForStandaloneCallers() throws Exception {
    String source =
        Files.readString(
            Path.of("src/main/java/com/thesettler_x_create/create/CreateNetworkFacade.java"));

    int method =
        source.indexOf("public List<ItemStack> planItems(IDeliverable deliverable, int amount) {");
    // Bounded lookahead instead of a line-ending-sensitive "end of method" marker - this file is
    // checked out with CRLF line endings, which broke a "\n  }\n" search.
    String body = source.substring(method, Math.min(source.length(), method + 400));
    assertTrue(body.contains("InventorySummary summary = getSummary();"));
    assertTrue(body.contains("planItemsFromSummary(deliverable, amount, summary)"));
  }
}
