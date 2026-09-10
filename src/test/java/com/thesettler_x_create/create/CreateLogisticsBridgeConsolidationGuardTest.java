package com.thesettler_x_create.create;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Every call site that places a Create stock-network package request used to build its own {@code
 * PackageOrderWithCrafts} and call {@code LogisticsManager.broadcastPackageRequest} directly - the
 * same few lines duplicated once in {@link CreateNetworkFacade} and twice in {@code ModNetwork}
 * (test- and batch-request handlers). That is now consolidated behind {@link
 * CreateLogisticsBridge#broadcastPackageRequest}, so a future Create-addon compatibility shim (e.g.
 * for mixins that change the request shape) only has to patch one place.
 */
class CreateLogisticsBridgeConsolidationGuardTest {

  @Test
  void createNetworkFacadeRoutesThroughTheSharedBridge() throws Exception {
    String source =
        Files.readString(
            Path.of("src/main/java/com/thesettler_x_create/create/CreateNetworkFacade.java"));

    assertTrue(source.contains("CreateLogisticsBridge.broadcastPackageRequest("));
    assertFalse(source.contains("LogisticsManager.broadcastPackageRequest("));
    assertFalse(source.contains("PackageOrderWithCrafts.simple("));
  }

  @Test
  void modNetworkTestAndBatchHandlersRouteThroughTheSharedBridge() throws Exception {
    String source =
        Files.readString(Path.of("src/main/java/com/thesettler_x_create/network/ModNetwork.java"));

    int bridgeCalls = countOccurrences(source, "CreateLogisticsBridge.broadcastPackageRequest(");
    assertTrue(
        bridgeCalls == 2,
        "expected handleTestRequest and handleBatchRequest to both route through the bridge, found "
            + bridgeCalls
            + " call(s)");
    assertFalse(source.contains("LogisticsManager.broadcastPackageRequest("));
    assertFalse(source.contains("PackageOrderWithCrafts.simple("));
  }

  private static int countOccurrences(String source, String needle) {
    int count = 0;
    int index = 0;
    while ((index = source.indexOf(needle, index)) != -1) {
      count++;
      index += needle.length();
    }
    return count;
  }
}
