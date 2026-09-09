package com.thesettler_x_create.network;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Seam-audit finding s4-5: {@code ADDRESS_MAX_LENGTH} was declared 4 separate times. Three of them
 * (this mod's own Shop/Packager address system, using a plain {@code EditBox}) agreed on 64 and are
 * now consolidated into {@code ModNetwork.SHOP_ADDRESS_MAX_LENGTH}. The 4th, in {@code
 * ColonyGaugeConfigPacket}, is 25 and intentionally stays separate: the Gauge screen's address
 * field is Create's own {@code AddressEditBox}, which hardcodes {@code setMaxLength(25)} in its own
 * constructor (confirmed against CreateModFork source) to match Create's package-address convention
 * - consolidating it into the shared 64 would let a player type more than the widget that
 * originally inspired the 25-char limit intends, and would desync the packet's writeUtf cap from
 * what the UI (silently, via Create's own widget) actually enforces.
 */
class AddressMaxLengthConsolidationGuardTest {

  @Test
  void sharedConstantIsDefinedOnceInModNetwork() throws Exception {
    String source =
        Files.readString(Path.of("src/main/java/com/thesettler_x_create/network/ModNetwork.java"));
    assertTrue(source.contains("public static final int SHOP_ADDRESS_MAX_LENGTH = 64;"));
  }

  @Test
  void theThreeShopPackagerFilesReuseTheSharedConstant() throws Exception {
    String shopScreen =
        Files.readString(
            Path.of("src/main/java/com/thesettler_x_create/client/gui/CreateShopScreen.java"));
    String shopPayload =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/network/SetCreateShopAddressPayload.java"));
    String packagerPayload =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/network/SetPackagerAddressPayload.java"));

    for (String source : new String[] {shopScreen, shopPayload, packagerPayload}) {
      assertTrue(source.contains("ModNetwork.SHOP_ADDRESS_MAX_LENGTH"));
      assertFalse(source.contains("private static final int ADDRESS_MAX_LENGTH"));
    }
  }

  @Test
  void gaugeConfigPacketIntentionallyKeepsItsOwnSmallerConstant() throws Exception {
    String source =
        Files.readString(
            Path.of("src/main/java/com/thesettler_x_create/network/ColonyGaugeConfigPacket.java"));
    assertTrue(source.contains("private static final int ADDRESS_MAX_LENGTH = 25;"));
    assertTrue(source.contains("Intentionally not ModNetwork.SHOP_ADDRESS_MAX_LENGTH"));
  }
}
