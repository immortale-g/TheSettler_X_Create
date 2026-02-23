package com.thesettler_x_create.minecolonies.moduleview;

import com.ldtteam.blockui.views.BOWindow;
import com.minecolonies.api.colony.buildings.modules.AbstractBuildingModuleView;
import com.simibubi.create.content.logistics.BigItemStack;
import com.thesettler_x_create.minecolonies.client.gui.CreateShopStockModuleWindow;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;

public class CreateShopStockModuleView extends AbstractBuildingModuleView {
  private List<BigItemStack> stock = Collections.emptyList();
  private boolean hasNetwork;

  @Override
  public void deserialize(RegistryFriendlyByteBuf buf) {
    hasNetwork = buf.readBoolean();
    stock = readStacks(buf);
  }

  public boolean hasNetwork() {
    return hasNetwork;
  }

  public List<BigItemStack> getStock() {
    return stock;
  }

  @Override
  public BOWindow getWindow() {
    return new CreateShopStockModuleWindow(this);
  }

  @Override
  public String getIcon() {
    return "stock";
  }

  @Override
  public net.minecraft.network.chat.Component getDesc() {
    return net.minecraft.network.chat.Component.translatable(
        "com.thesettler_x_create.gui.createshop.stock");
  }

  private static List<BigItemStack> readStacks(RegistryFriendlyByteBuf buf) {
    int count = buf.readVarInt();
    if (count <= 0) {
      return Collections.emptyList();
    }
    List<BigItemStack> stacks = new ArrayList<>(count);
    for (int i = 0; i < count; i++) {
      stacks.add(BigItemStack.STREAM_CODEC.decode(buf));
    }
    return stacks;
  }
}
