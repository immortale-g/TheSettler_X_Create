package com.thesettler_x_create.minecolonies.moduleview;

import com.ldtteam.blockui.views.BOWindow;
import com.minecolonies.api.colony.buildings.modules.AbstractBuildingModuleView;
import com.thesettler_x_create.minecolonies.client.gui.CreateShopOutputModuleWindow;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;

public class CreateShopOutputModuleView extends AbstractBuildingModuleView {
  private boolean linked;
  private BlockPos outputPos;

  @Override
  public void deserialize(RegistryFriendlyByteBuf buf) {
    linked = buf.readBoolean();
    outputPos = linked ? buf.readBlockPos() : null;
  }

  public boolean isLinked() {
    return linked;
  }

  public BlockPos getOutputPos() {
    return outputPos;
  }

  @Override
  public BOWindow getWindow() {
    return new CreateShopOutputModuleWindow(this);
  }

  @Override
  public boolean isPageVisible() {
    return false;
  }

  @Override
  public String getIcon() {
    return "settings";
  }

  @Override
  public net.minecraft.network.chat.Component getDesc() {
    return net.minecraft.network.chat.Component.translatable(
        "com.thesettler_x_create.gui.createshop.output");
  }
}
