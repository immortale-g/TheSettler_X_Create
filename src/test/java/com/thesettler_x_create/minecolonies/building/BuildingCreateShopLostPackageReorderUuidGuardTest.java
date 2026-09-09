package com.thesettler_x_create.minecolonies.building;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Seam-audit finding s2-3: the lost-package reorder path called {@code requestStacksImmediate}
 * without a request UUID, so the resulting inflight entry was unreachable by the UUID-based cancel
 * path (cancelInflightByUuid etc. explicitly skip null-UUID entries as legacy). The interaction
 * already tracks its own requestUuid field - it just wasn't being threaded through.
 */
class BuildingCreateShopLostPackageReorderUuidGuardTest {

  @Test
  void restartLostPackageDetailedAcceptsAndForwardsRequestUuid() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/building/BuildingCreateShop.java"));

    int method = source.indexOf("LostPackageReorderResult restartLostPackageDetailed(");
    int secondMethod =
        source.indexOf("LostPackageReorderResult restartLostPackageDetailed(", method + 1);
    assertTrue(secondMethod > method, "expected a second (UUID-accepting) overload");
    String overloadSignature = source.substring(secondMethod, source.indexOf('{', secondMethod));
    assertTrue(overloadSignature.contains("@Nullable java.util.UUID requestUuid"));

    String body = source.substring(secondMethod);
    assertTrue(
        body.contains(".requestStacksImmediate(List.of(requested), requesterName, requestUuid);"));
  }

  @Test
  void interactionPassesItsOwnRequestUuidToReorder() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/building/ShopLostPackageInteraction.java"));

    int call = source.indexOf("shop.restartLostPackageDetailed(");
    assertTrue(call > 0);
    String body = source.substring(call, source.indexOf(';', call));
    assertTrue(body.contains("requestUuid"));
  }
}
