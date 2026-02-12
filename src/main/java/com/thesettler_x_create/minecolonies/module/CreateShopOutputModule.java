package com.thesettler_x_create.minecolonies.module;

import com.minecolonies.api.colony.buildings.modules.AbstractBuildingModule;
import com.thesettler_x_create.minecolonies.building.BuildingCreateShop;
import net.minecraft.network.RegistryFriendlyByteBuf;

public class CreateShopOutputModule extends AbstractBuildingModule {
  @Override
  public void serializeToView(RegistryFriendlyByteBuf buf) {
    BuildingCreateShop shop = building instanceof BuildingCreateShop b ? b : null;
    boolean linked = shop != null && shop.getOutputPos() != null;
    buf.writeBoolean(linked);
    if (linked) {
      buf.writeBlockPos(shop.getOutputPos());
    }
  }
}
