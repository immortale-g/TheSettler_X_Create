package com.thesettler_x_create.minecolonies.moduleview;

import com.ldtteam.blockui.views.BOWindow;
import com.minecolonies.api.colony.buildings.modules.AbstractBuildingModuleView;
import com.thesettler_x_create.minecolonies.client.gui.CreateShopAddressModuleWindow;
import net.minecraft.network.RegistryFriendlyByteBuf;

public class CreateShopAddressModuleView extends AbstractBuildingModuleView {
  private String address = "";

  @Override
  public void deserialize(RegistryFriendlyByteBuf buf) {
    address = buf.readUtf(64);
  }

  public String getAddress() {
    return address == null ? "" : address;
  }

  @Override
  public BOWindow getWindow() {
    return new CreateShopAddressModuleWindow(this);
  }

  @Override
  public String getIcon() {
    return "settings";
  }

  @Override
  public net.minecraft.network.chat.Component getDesc() {
    return net.minecraft.network.chat.Component.translatable(
        "com.thesettler_x_create.gui.createshop.address");
  }
}
