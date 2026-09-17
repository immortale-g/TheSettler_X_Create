package com.thesettler_x_create.minecolonies.moduleview;

import com.ldtteam.blockui.views.BOWindow;
import com.minecolonies.api.colony.buildings.modules.AbstractBuildingModuleView;
import com.thesettler_x_create.minecolonies.client.gui.CreateShopNetworkMinimumModuleWindow;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Client side of what the shop keeps in its Create network before the colony may draw. */
public class CreateShopNetworkMinimumModuleView extends AbstractBuildingModuleView {
  /** One item kind and how many of it stay in the network. */
  public record Entry(ItemStack stack, int amount) {}

  private List<Entry> entries = Collections.emptyList();
  private boolean limitReached;

  @Override
  public void deserialize(RegistryFriendlyByteBuf buf) {
    int count = buf.readVarInt();
    List<Entry> read = new ArrayList<>(Math.max(0, count));
    for (int i = 0; i < count; i++) {
      ItemStack stack = ItemStack.STREAM_CODEC.decode(buf);
      int amount = buf.readVarInt();
      read.add(new Entry(stack, amount));
    }
    entries = read;
    limitReached = buf.readBoolean();
  }

  public List<Entry> getEntries() {
    return entries;
  }

  public boolean hasReachedLimit() {
    return limitReached;
  }

  @Override
  public BOWindow getWindow() {
    return new CreateShopNetworkMinimumModuleWindow(this);
  }

  @Override
  public String getIcon() {
    return "stock";
  }

  @Override
  public Component getDesc() {
    return Component.translatable("com.thesettler_x_create.gui.createshop.networkminimum");
  }
}
