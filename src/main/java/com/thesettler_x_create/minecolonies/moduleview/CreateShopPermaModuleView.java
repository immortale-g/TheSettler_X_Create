package com.thesettler_x_create.minecolonies.moduleview;

import com.ldtteam.blockui.views.BOWindow;
import com.minecolonies.api.colony.buildings.modules.AbstractBuildingModuleView;
import com.thesettler_x_create.minecolonies.client.gui.CreateShopPermaModuleWindow;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.ItemStack;

public class CreateShopPermaModuleView extends AbstractBuildingModuleView {
  public static final class OreEntry {
    private final ItemStack stack;
    private boolean selected;

    private OreEntry(ItemStack stack, boolean selected) {
      this.stack = stack;
      this.selected = selected;
    }

    public ItemStack getStack() {
      return stack;
    }

    public boolean isSelected() {
      return selected;
    }

    public void setSelected(boolean selected) {
      this.selected = selected;
    }
  }

  private boolean enabled;
  private boolean waitFullStack;
  private List<OreEntry> ores = Collections.emptyList();

  @Override
  public void deserialize(RegistryFriendlyByteBuf buf) {
    enabled = buf.readBoolean();
    waitFullStack = buf.readBoolean();
    int count = buf.readVarInt();
    if (count <= 0) {
      ores = Collections.emptyList();
      return;
    }
    List<OreEntry> entries = new ArrayList<>(count);
    for (int i = 0; i < count; i++) {
      ItemStack stack = ItemStack.STREAM_CODEC.decode(buf);
      boolean selected = buf.readBoolean();
      entries.add(new OreEntry(stack, selected));
    }
    ores = entries;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public boolean isWaitFullStack() {
    return waitFullStack;
  }

  public void setWaitFullStack(boolean waitFullStack) {
    this.waitFullStack = waitFullStack;
  }

  public List<OreEntry> getOres() {
    return ores;
  }

  @Override
  public BOWindow getWindow() {
    return new CreateShopPermaModuleWindow(this);
  }

  @Override
  public String getIcon() {
    return "stock";
  }

  @Override
  public net.minecraft.network.chat.Component getDesc() {
    return net.minecraft.network.chat.Component.translatable(
        "com.thesettler_x_create.gui.createshop.perma");
  }
}
