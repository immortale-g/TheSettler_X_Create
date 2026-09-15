package com.thesettler_x_create.create;

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
