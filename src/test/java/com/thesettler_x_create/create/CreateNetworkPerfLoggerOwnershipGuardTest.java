package com.thesettler_x_create.create;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Seam-audit finding s2-4: {@code CreateNetworkFacade} used to own its {@code
 * CreateNetworkPerfLogger} as an instance field, but the facade is constructed fresh at nearly
 * every call site, so the logger's own same-tick log cooldown reset to zero every time and
 * effectively never held. The logger must live on the long-lived {@code TileEntityCreateShop}
 * instead.
 */
class CreateNetworkPerfLoggerOwnershipGuardTest {

  @Test
  void tileEntityOwnsALongLivedPerfLogger() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/tileentity/TileEntityCreateShop.java"));

    assertTrue(
        source.contains(
            "private final CreateNetworkPerfLogger perfLogger = new CreateNetworkPerfLogger();"));
    assertTrue(source.contains("public CreateNetworkPerfLogger getPerfLogger()"));
  }

  @Test
  void facadeReusesTheShopsPerfLoggerInsteadOfOwningOne() throws Exception {
    String source =
        Files.readString(
            Path.of("src/main/java/com/thesettler_x_create/create/CreateNetworkFacade.java"));

    assertTrue(
        source.contains("shop != null ? shop.getPerfLogger() : new CreateNetworkPerfLogger()"));
  }
}
