package com.thesettler_x_create.minecolonies.building;

import com.minecolonies.api.util.MessageUtils;
import com.thesettler_x_create.Config;
import com.thesettler_x_create.minecolonies.tileentity.TileEntityCreateShop;

/** Handles missing stock network notifications for the Create Shop. */
final class ShopNetworkNotifier {
  private final BuildingCreateShop shop;
  private long lastMissingNetworkWarning;

  ShopNetworkNotifier(BuildingCreateShop shop) {
    this.shop = shop;
    this.lastMissingNetworkWarning = 0L;
  }

  void notifyMissingNetwork() {
    if (!Config.CHAT_MESSAGES_ENABLED.getAsBoolean()) {
      return;
    }
    TileEntityCreateShop shopTile = shop.getCreateShopTileEntity();
    if (shopTile == null || shopTile.getLevel() == null) {
      return;
    }
    long gameTime = shopTile.getLevel().getGameTime();
    if (gameTime - lastMissingNetworkWarning
        <= Config.MISSING_NETWORK_WARNING_COOLDOWN.getAsLong()) {
      return;
    }
    lastMissingNetworkWarning = gameTime;
    MessageUtils.format("com.thesettler_x_create.message.createshop.no_network")
        .sendTo(shop.getColony())
        .forAllPlayers();
  }
}
