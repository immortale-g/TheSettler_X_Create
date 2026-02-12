package com.thesettler_x_create.minecolonies.module;

import com.minecolonies.api.colony.buildings.modules.AbstractBuildingModule;
import com.thesettler_x_create.minecolonies.tileentity.TileEntityCreateShop;
import net.minecraft.network.RegistryFriendlyByteBuf;

public class CreateShopAddressModule extends AbstractBuildingModule {
  @Override
  public void serializeToView(RegistryFriendlyByteBuf buf) {
    TileEntityCreateShop shop = getShopTile();
    String address = shop == null ? "" : shop.getShopAddress();
    buf.writeUtf(address == null ? "" : address, 64);
  }

  private TileEntityCreateShop getShopTile() {
    if (building == null) {
      return null;
    }
    if (building.getTileEntity() instanceof TileEntityCreateShop shop) {
      return shop;
    }
    return null;
  }
}
