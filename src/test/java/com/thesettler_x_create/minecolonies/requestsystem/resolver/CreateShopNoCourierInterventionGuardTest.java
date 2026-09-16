package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * MineColonies owns courier work. When a delivery request is cancelled or failed it removes the
 * token from the courier's task queue and from the warehouse queue itself
 * (DeliverymenRequestResolver#onAssignedRequestCancelled). Driving that by hand either duplicated
 * it or, via cleanRequestData without a cancel, left couriers stuck on tokens that no longer
 * resolve. The resolver package may read courier state, never change it.
 */
class CreateShopNoCourierInterventionGuardTest {
  private static final Path RESOLVER_DIR =
      Path.of("src/main/java/com/thesettler_x_create/minecolonies/requestsystem/resolver");

  private static final List<String> FORBIDDEN =
      List.of(
          ".onTaskDeletion(",
          ".finishRequest(",
          ".cleanRequestData(",
          ".addRequest(",
          "getMutableRequestList().remove",
          "getMutableRequestList().add",
          "addConcurrentDelivery(");

  @Test
  void resolverPackageDoesNotChangeCourierOrWarehouseQueues() throws Exception {
    List<String> hits = new ArrayList<>();
    try (Stream<Path> files = Files.list(RESOLVER_DIR)) {
      for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
        String code = CreateShopGuardSource.activeCode(Files.readString(file));
        for (String call : FORBIDDEN) {
          if (code.contains(call)) {
            hits.add(file.getFileName() + ": " + call);
          }
        }
      }
    }
    assertTrue(hits.isEmpty(), "courier/warehouse queue mutation in resolver package: " + hits);
  }
}
