package com.thesettler_x_create.create;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * The "check {@code PackageItem.isPackage}, then read its {@code ItemStackHandler} contents"
 * sequence used to be duplicated independently in {@code ShopLostPackageInteraction} (three
 * times), {@code ColonyPackagerBlockEntity}, and {@code CreateShopOutputBlockTestCommands}, and
 * building a new package was duplicated once more in {@code CreateShopOutputBlockEntity}. All five
 * now route through {@link CreatePackageBridge} instead of calling {@code PackageItem} directly.
 */
class CreatePackageBridgeConsolidationGuardTest {

  @Test
  void shopLostPackageInteractionRoutesThroughTheSharedBridge() throws Exception {
    assertNoDirectPackageItemCalls(
        "src/main/java/com/thesettler_x_create/minecolonies/building/ShopLostPackageInteraction.java");
  }

  @Test
  void shopLostPackageHandoverProcessorRoutesThroughTheSharedBridge() throws Exception {
    assertNoDirectPackageItemCalls(
        "src/main/java/com/thesettler_x_create/minecolonies/building/ShopLostPackageHandoverProcessor.java");
  }

  @Test
  void createShopOutputBlockEntityRoutesThroughTheSharedBridge() throws Exception {
    assertNoDirectPackageItemCalls(
        "src/main/java/com/thesettler_x_create/blockentity/CreateShopOutputBlockEntity.java");
  }

  @Test
  void colonyPackagerBlockEntityRoutesThroughTheSharedBridge() throws Exception {
    assertNoDirectPackageItemCalls(
        "src/main/java/com/thesettler_x_create/blockentity/ColonyPackagerBlockEntity.java");
  }

  @Test
  void createShopOutputBlockTestCommandsCountHelperRoutesThroughTheSharedBridge() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/command/CreateShopOutputBlockTestCommands.java"));
    assertTrue(source.contains("CreatePackageBridge.readContents("));
    // getAddress()/instanceof PackageItem checks are untouched, out of scope for this bridge -
    // only assert the getContents()-based counting helper was migrated.
    assertFalse(source.contains("PackageItem.getContents("));
  }

  private static void assertNoDirectPackageItemCalls(String path) throws Exception {
    String source = Files.readString(Path.of(path));
    assertTrue(
        source.contains("CreatePackageBridge."), path + " should route through CreatePackageBridge");
    assertFalse(source.contains("PackageItem.isPackage("));
    assertFalse(source.contains("PackageItem.getContents("));
    assertFalse(source.contains("PackageItem.containing("));
    assertFalse(source.contains("PackageItem.addAddress("));
  }
}
