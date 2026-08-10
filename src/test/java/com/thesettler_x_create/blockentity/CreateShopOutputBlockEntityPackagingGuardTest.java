package com.thesettler_x_create.blockentity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CreateShopOutputBlockEntityPackagingGuardTest {
  private static final Path SOURCE =
      Path.of("src/main/java/com/thesettler_x_create/blockentity/CreateShopOutputBlockEntity.java");

  @Test
  void packageAddressFieldAndNbtTagArePresent() throws Exception {
    String src = Files.readString(SOURCE);
    assertTrue(src.contains("TAG_PACKAGE_ADDRESS"), "TAG_PACKAGE_ADDRESS constant missing");
    assertTrue(src.contains("packageAddress"), "packageAddress field missing");
    assertTrue(src.contains("getPackageAddress()"), "getPackageAddress() missing");
    assertTrue(src.contains("setPackageAddress("), "setPackageAddress() missing");
  }

  @Test
  void createApiCallsArePresent() throws Exception {
    String src = Files.readString(SOURCE);
    assertTrue(src.contains("PackageItem.containing("), "PackageItem.containing() call missing");
    assertTrue(src.contains("PackageItem.addAddress("), "PackageItem.addAddress() call missing");
  }

  @Test
  void emptyAddressGuardIsEnforced() throws Exception {
    String src = Files.readString(SOURCE);
    assertTrue(
        src.contains("packageAddress.isEmpty()"),
        "empty-address guard missing — block must output EMPTY when no address configured");
  }

  @Test
  void handlerAlwaysExposesExactlyOneSlot() throws Exception {
    String src = Files.readString(SOURCE);
    // getSlots() must return the literal 1, not permaItems.size()
    assertTrue(src.contains("return 1;"), "getSlots() must return 1 (single package slot)");
    long permaItemsSizeReferences =
        src.lines().filter(l -> l.contains("getPermaItems().size()")).count();
    assertEquals(0, permaItemsSizeReferences, "getSlots() must not delegate to permaItems.size()");
  }
}
