package com.thesettler_x_create.minecolonies.moduleview;

import com.ldtteam.blockui.views.BOWindow;
import com.minecolonies.api.colony.buildings.modules.AbstractBuildingModuleView;
import com.thesettler_x_create.minecolonies.client.gui.CreateShopAddressModuleWindow;
import net.minecraft.network.RegistryFriendlyByteBuf;

public class CreateShopAddressModuleView extends AbstractBuildingModuleView {
  private String address = "";
  private String packageAddress = "";

  @Override
  public void deserialize(RegistryFriendlyByteBuf buf) {
    address = buf.readUtf(64);
    packageAddress = buf.readUtf(64);
  }

  public String getAddress() {
    return address == null ? "" : address;
  }

  public String getPackageAddress() {
    return packageAddress == null ? "" : packageAddress;
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
