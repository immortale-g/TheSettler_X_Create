package com.thesettler_x_create.minecolonies.moduleview;

import com.ldtteam.blockui.views.BOWindow;
import com.minecolonies.api.colony.buildings.modules.AbstractBuildingModuleView;
import com.thesettler_x_create.minecolonies.client.gui.CreateShopNetworkMinimumModuleWindow;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Client side of what the shop keeps in its Create network before the colony may draw. */
public class CreateShopNetworkMinimumModuleView extends AbstractBuildingModuleView {
  /** One item kind and how many of it stay in the network. */
  public record Entry(ItemStack stack, int amount) {}

  private List<Entry> entries = new ArrayList<>();
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

  /** What stays in the network for this item kind, 0 when the player set nothing for it. */
  public int getMinimum(ItemStack stack) {
    if (stack == null || stack.isEmpty()) {
      return 0;
    }
    for (Entry entry : entries) {
      if (ItemStack.isSameItemSameComponents(entry.stack(), stack)) {
        return entry.amount();
      }
    }
    return 0;
  }

  /**
   * Writes what the player just set into this view, so the list shows it at once instead of after
   * the building's next update. The server has the say: the very next {@link
   * #deserialize(RegistryFriendlyByteBuf)} replaces this with what was actually stored, refusals
   * and all.
   */
  public void previewMinimum(ItemStack stack, int amount) {
    if (stack == null || stack.isEmpty()) {
      return;
    }
    entries.removeIf(entry -> ItemStack.isSameItemSameComponents(entry.stack(), stack));
    if (amount > 0) {
      entries.add(new Entry(stack.copyWithCount(1), amount));
    }
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
