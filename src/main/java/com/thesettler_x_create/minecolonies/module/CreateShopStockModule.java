package com.thesettler_x_create.minecolonies.module;

import com.minecolonies.api.colony.buildings.modules.AbstractBuildingModule;
import com.simibubi.create.content.logistics.BigItemStack;
import com.thesettler_x_create.minecolonies.building.BuildingCreateShop;
import com.thesettler_x_create.minecolonies.tileentity.TileEntityCreateShop;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;

public class CreateShopStockModule extends AbstractBuildingModule {
  @Override
  public void serializeToView(RegistryFriendlyByteBuf buf) {
    TileEntityCreateShop shop = getShopTile();
    UUID networkId = shop == null ? null : shop.getStockNetworkId();
    if (networkId == null) {
      buf.writeBoolean(false);
      buf.writeVarInt(0);
      return;
    }

    buf.writeBoolean(true);
    List<BigItemStack> stacks = getRegisteredStorageStock();
    buf.writeVarInt(stacks.size());
    for (BigItemStack stack : stacks) {
      BigItemStack.STREAM_CODEC.encode(buf, stack);
    }
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

  private List<BigItemStack> getRegisteredStorageStock() {
    if (building instanceof BuildingCreateShop shopBuilding) {
      return shopBuilding.getRegisteredStorageStock();
    }
    return Collections.emptyList();
  }
}
