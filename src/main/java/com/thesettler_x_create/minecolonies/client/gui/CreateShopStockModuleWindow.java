package com.thesettler_x_create.minecolonies.client.gui;

import com.ldtteam.blockui.Pane;
import com.ldtteam.blockui.controls.ItemIcon;
import com.ldtteam.blockui.controls.Text;
import com.ldtteam.blockui.views.ScrollingList;
import com.minecolonies.core.client.gui.AbstractModuleWindow;
import com.simibubi.create.content.logistics.BigItemStack;
import com.thesettler_x_create.TheSettlerXCreate;
import com.thesettler_x_create.minecolonies.moduleview.CreateShopStockModuleView;
import com.thesettler_x_create.network.CreateShopStockRefreshPayload;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

public class CreateShopStockModuleWindow extends AbstractModuleWindow<CreateShopStockModuleView> {
  private final com.minecolonies.api.colony.buildings.views.IBuildingView building;
  private final CreateShopStockModuleView moduleView;
  private final ScrollingList stockList;
  private List<BigItemStack> stock = new ArrayList<>();
  private int refreshTicks;

  public CreateShopStockModuleWindow(CreateShopStockModuleView moduleView) {
    super(
        moduleView,
        net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
            TheSettlerXCreate.MODID, "gui/layouthuts/layoutcreateshop_stock.xml"));
    this.building = moduleView.getBuildingView();
    this.moduleView = moduleView;

    Text desc = findPaneOfTypeByID("desc", Text.class);
    if (desc != null) {
      desc.setText(moduleView.getDesc());
    }
    stockList = findPaneOfTypeByID("stockList", ScrollingList.class);
  }

  @Override
  public void onOpened() {
    updateStock();
    refreshTicks = 0;
    PacketDistributor.sendToServer(new CreateShopStockRefreshPayload(building.getPosition()));
  }

  @Override
  public void onUpdate() {
    super.onUpdate();
    refreshTicks++;
    if (refreshTicks >= 40) {
      refreshTicks = 0;
      PacketDistributor.sendToServer(new CreateShopStockRefreshPayload(building.getPosition()));
    }
  }

  private void updateStock() {
    stock = new ArrayList<>(moduleView.getStock());
    if (stockList == null) {
      return;
    }
    if (!moduleView.hasNetwork()) {
      stockList.setEmptyText(
          Component.translatable("com.thesettler_x_create.gui.createshop.stock.network_missing"));
    } else {
      stockList.setEmptyText(
          Component.translatable("com.thesettler_x_create.gui.createshop.stock.storage_empty"));
    }
    stockList.setDataProvider(
        new ScrollingList.DataProvider() {
          @Override
          public int getElementCount() {
            return stock.size();
          }

          @Override
          public void updateElement(int index, Pane row) {
            if (index < 0 || index >= stock.size()) {
              return;
            }
            BigItemStack entry = stock.get(index);
            ItemIcon icon = row.findPaneOfTypeByID("itemIcon", ItemIcon.class);
            if (icon != null) {
              var display = entry.stack.copy();
              int displayCount = entry.isInfinite() ? 1 : Math.max(1, Math.min(64, entry.count));
              display.setCount(displayCount);
              icon.setItem(display);
            }

            Text name = row.findPaneOfTypeByID("itemName", Text.class);
            if (name != null) {
              name.setText(entry.stack.getHoverName());
            }

            Text count = row.findPaneOfTypeByID("itemCount", Text.class);
            if (count != null) {
              String amount = entry.isInfinite() ? "inf" : String.valueOf(Math.max(0, entry.count));
              count.setText(Component.literal(amount));
            }
          }
        });
    stockList.refreshElementPanes();
  }
}
