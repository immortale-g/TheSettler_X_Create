package com.thesettler_x_create.create;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Guards the fix for the endless-reorder bug: a broadcast that Create refused must never be
 * recorded as inflight, and no call site may talk to LogisticsManager directly again.
 */
class CreateLogisticsBridgeOutcomeGuardTest {

  private static String read(String path) throws Exception {
    return Files.readString(Path.of(path));
  }

  @Test
  void bridgeSeparatesNoPackagerFromBusyPackager() throws Exception {
    String source = read("src/main/java/com/thesettler_x_create/create/CreateLogisticsBridge.java");
    // Create's own broadcastPackageRequest returns true for an empty packager map, so the bridge
    // has to run find/busy/perform itself to tell the two apart.
    assertTrue(source.contains("LogisticsManager.findPackagersForRequest("));
    assertTrue(source.contains("LogisticsManager.performPackageRequests("));
    assertTrue(source.contains("return Outcome.NO_PACKAGER;"));
    assertTrue(source.contains("isTooBusyFor("));
    assertTrue(source.contains("return Outcome.PACKAGER_BUSY;"));
  }

  @Test
  void bridgePrefersFactoryLogisticsWhenPresent() throws Exception {
    String source = read("src/main/java/com/thesettler_x_create/create/CreateLogisticsBridge.java");
    assertTrue(source.contains("CreateFactoryLogisticsCompat.isAvailable()"));
    assertTrue(source.contains("broadcastThroughFactoryLogistics("));
  }

  @Test
  void facadeOnlyRecordsInflightAfterDispatch() throws Exception {
    String source = read("src/main/java/com/thesettler_x_create/create/CreateNetworkFacade.java");
    assertTrue(source.contains("CreateLogisticsBridge.broadcastPackageRequest("));
    assertTrue(source.contains("if (!outcome.dispatched()) {"));
    // The refusal branch must return before reaching recordInflight.
    int refusal = source.indexOf("if (!outcome.dispatched()) {");
    int inflight = source.indexOf("recordInflight(consolidated,");
    assertTrue(refusal > 0 && inflight > refusal);
    assertTrue(source.indexOf("return false;", refusal) < inflight);
  }

  @Test
  void inflightIsWrittenFromExactlyOnePlaceAndOnlyAfterDispatch() throws Exception {
    String facade = read("src/main/java/com/thesettler_x_create/create/CreateNetworkFacade.java");
    // One writer only: if a second path ever records inflight, it has to be reviewed against the
    // dispatch check as well, so this guard has to fail rather than silently allow it.
    assertEquals(
        1,
        countOccurrences(facade, "pickup.recordInflight("),
        "inflight must only be written by CreateNetworkFacade.recordInflight");
    assertEquals(
        1,
        countOccurrences(facade, "      recordInflight(consolidated,"),
        "recordInflight must only be called once, inside the dispatched branch");

    // And nobody outside the facade may write inflight directly.
    for (String path :
        allJavaSources().stream()
            .filter(p -> !p.endsWith("CreateNetworkFacade.java"))
            .filter(p -> !p.endsWith("CreateShopBlockEntity.java"))
            .toList()) {
      assertFalse(
          read(path).contains(".recordInflight("),
          path + " must not record inflight, only CreateNetworkFacade may");
    }
  }

  private static int countOccurrences(String haystack, String needle) {
    int count = 0;
    for (int at = haystack.indexOf(needle); at >= 0; at = haystack.indexOf(needle, at + 1)) {
      count++;
    }
    return count;
  }

  private static java.util.List<String> allJavaSources() throws Exception {
    try (var paths = Files.walk(Path.of("src/main/java"))) {
      return paths.filter(p -> p.toString().endsWith(".java")).map(Path::toString).toList();
    }
  }

  @Test
  void noCallSiteBroadcastsThroughCreateDirectly() throws Exception {
    for (String path :
        new String[] {
          "src/main/java/com/thesettler_x_create/create/CreateNetworkFacade.java",
          "src/main/java/com/thesettler_x_create/network/ModNetwork.java"
        }) {
      assertFalse(
          read(path).contains("LogisticsManager.broadcastPackageRequest("),
          path + " must go through CreateLogisticsBridge");
    }
  }
}
