package com.thesettler_x_create.minecolonies.tileentity;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Clean Code Audit finding a2-5: {@code maybeNotifyFull()} was a no-op stub - it updated a cooldown
 * timestamp and returned, with no observable effect, despite its name and the {@code
 * ShopRackAccess} call site both implying a player-facing warning. Fixed by actually sending the
 * cooldown-gated chat message, mirroring the existing {@code ShopNetworkNotifier} pattern.
 */
class TileEntityCreateShopRackFullNotificationGuardTest {
  @Test
  void maybeNotifyFullActuallySendsAGatedChatMessage() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/tileentity/TileEntityCreateShop.java"));

    int method = source.indexOf("void maybeNotifyFull() {");
    assertTrue(method > 0);
    String body = source.substring(method, Math.min(source.length(), method + 800));

    assertTrue(body.contains("Config.RACK_FULL_WARNING_COOLDOWN.getAsLong()"));
    assertTrue(body.contains("Config.CHAT_MESSAGES_ENABLED.getAsBoolean()"));
    assertTrue(body.contains("com.thesettler_x_create.message.createshop.rack_full"));
    assertTrue(body.contains("getBuilding() instanceof BuildingCreateShop shop"));
  }
}
