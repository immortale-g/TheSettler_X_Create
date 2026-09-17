package com.thesettler_x_create.minecolonies.client.gui;

import com.ldtteam.blockui.Pane;
import com.ldtteam.blockui.controls.Button;
import com.ldtteam.blockui.controls.ItemIcon;
import com.ldtteam.blockui.controls.Text;
import com.ldtteam.blockui.views.ScrollingList;
import com.ldtteam.structurize.client.gui.WindowSelectRes;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.core.client.gui.AbstractModuleWindow;
import com.thesettler_x_create.TheSettlerXCreate;
import com.thesettler_x_create.minecolonies.moduleview.CreateShopNetworkMinimumModuleView;
import com.thesettler_x_create.network.SetCreateShopNetworkMinimumPayload;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Lets the player say how much of an item kind stays in the shop's Create network. The picker is
 * the same one the warehouse uses for its minimum stock, so every item can be chosen, not only what
 * the network happens to hold right now.
 */
public class CreateShopNetworkMinimumModuleWindow
    extends AbstractModuleWindow<CreateShopNetworkMinimumModuleView> {
  private final CreateShopNetworkMinimumModuleView moduleView;
  private final ScrollingList minimumList;
  private List<CreateShopNetworkMinimumModuleView.Entry> entries = new ArrayList<>();

  public CreateShopNetworkMinimumModuleWindow(CreateShopNetworkMinimumModuleView moduleView) {
    super(
        moduleView,
        ResourceLocation.fromNamespaceAndPath(
            TheSettlerXCreate.MODID, "gui/layouthuts/layoutcreateshop_networkminimum.xml"));
    this.moduleView = moduleView;

    Text desc = findPaneOfTypeByID("desc", Text.class);
    if (desc != null) {
      desc.setText(moduleView.getDesc());
    }
    minimumList = findPaneOfTypeByID("minimumList", ScrollingList.class);
    registerButton("addMinimum", this::addMinimum);
    registerButton("removeMinimum", this::removeMinimum);
  }

  @Override
  public void onOpened() {
    super.onOpened();
    updateList();
  }

  private void addMinimum() {
    if (moduleView.hasReachedLimit()) {
      return;
    }
    new WindowSelectRes(
            this,
            Component.empty(),
            null,
            IColonyManager.getInstance().getCompatibilityManager().getListOfAllItems(),
            (stack, amount) ->
                PacketDistributor.sendToServer(
                    new SetCreateShopNetworkMinimumPayload(
                        buildingView.getPosition(), stack.copyWithCount(1), amount)),
            true,
            Component.translatable("com.thesettler_x_create.gui.createshop.networkminimum.select"))
        .open();
  }

  private void removeMinimum(Button button) {
    if (minimumList == null) {
      return;
    }
    int row = minimumList.getListElementIndexByPane(button);
    if (row < 0 || row >= entries.size()) {
      return;
    }
    CreateShopNetworkMinimumModuleView.Entry entry = entries.get(row);
    PacketDistributor.sendToServer(
        new SetCreateShopNetworkMinimumPayload(
            buildingView.getPosition(), entry.stack().copyWithCount(1), 0));
    entries.remove(row);
    minimumList.refreshElementPanes();
  }

  private void updateList() {
    entries = new ArrayList<>(moduleView.getEntries());
    if (minimumList == null) {
      return;
    }
    minimumList.setDataProvider(
        new ScrollingList.DataProvider() {
          @Override
          public int getElementCount() {
            return entries.size();
          }

          @Override
          public void updateElement(int index, Pane row) {
            if (index < 0 || index >= entries.size()) {
              return;
            }
            CreateShopNetworkMinimumModuleView.Entry entry = entries.get(index);
            ItemIcon icon = row.findPaneOfTypeByID("itemIcon", ItemIcon.class);
            if (icon != null) {
              icon.setItem(entry.stack().copyWithCount(1));
            }
            Text name = row.findPaneOfTypeByID("itemName", Text.class);
            if (name != null) {
              name.setText(entry.stack().getHoverName());
            }
            Text amount = row.findPaneOfTypeByID("itemAmount", Text.class);
            if (amount != null) {
              amount.setText(Component.literal(String.valueOf(entry.amount())));
            }
          }
        });
    minimumList.refreshElementPanes();
  }
}
