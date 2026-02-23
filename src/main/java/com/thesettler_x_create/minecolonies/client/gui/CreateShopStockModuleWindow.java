package com.thesettler_x_create.minecolonies.client.gui;

import com.ldtteam.blockui.Pane;
import com.ldtteam.blockui.controls.Button;
import com.ldtteam.blockui.controls.ItemIcon;
import com.ldtteam.blockui.controls.Text;
import com.ldtteam.blockui.controls.TextField;
import com.ldtteam.blockui.views.ScrollingList;
import com.minecolonies.core.client.gui.AbstractModuleWindow;
import com.simibubi.create.content.logistics.BigItemStack;
import com.thesettler_x_create.TheSettlerXCreate;
import com.thesettler_x_create.minecolonies.moduleview.CreateShopStockModuleView;
import com.thesettler_x_create.network.CreateShopBatchRequestPayload;
import com.thesettler_x_create.network.CreateShopStockRefreshPayload;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public class CreateShopStockModuleWindow extends AbstractModuleWindow<CreateShopStockModuleView> {
  private enum StockViewMode {
    HUT,
    STORAGE
  }

  private final com.minecolonies.api.colony.buildings.views.IBuildingView building;
  private final CreateShopStockModuleView moduleView;
  private final ScrollingList stockList;
  private final Button viewHutButton;
  private final Button viewStorageButton;
  private List<BigItemStack> hutStock = new ArrayList<>();
  private List<BigItemStack> storageStock = new ArrayList<>();
  private final Map<String, Integer> requestAmounts = new HashMap<>();
  private final Map<String, ItemStack> requestStacks = new HashMap<>();
  private final Map<String, Integer> availableCounts = new HashMap<>();
  private final Set<String> infiniteItems = new HashSet<>();
  private int refreshTicks;
  private StockViewMode viewMode = StockViewMode.STORAGE;

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
    viewHutButton = findPaneOfTypeByID("viewHut", Button.class);
    viewStorageButton = findPaneOfTypeByID("viewStorage", Button.class);
    registerButton("viewHut", btn -> setViewMode(StockViewMode.HUT));
    registerButton("viewStorage", btn -> setViewMode(StockViewMode.STORAGE));
    registerButton("requestAll", btn -> requestAll());
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
    hutStock = new ArrayList<>(moduleView.getHutStock());
    storageStock = new ArrayList<>(moduleView.getStorageStock());
    requestStacks.clear();
    availableCounts.clear();
    infiniteItems.clear();

    for (BigItemStack entry : storageStock) {
      ItemStack base = entry.stack.copy();
      base.setCount(1);
      String key = getStackKey(base);
      requestStacks.put(key, base);
      availableCounts.put(key, entry.count);
      if (entry.isInfinite()) {
        infiniteItems.add(key);
      }
    }
    requestAmounts.keySet().retainAll(requestStacks.keySet());

    if (stockList == null) {
      return;
    }

    refreshModeUi();

    stockList.setDataProvider(
        new ScrollingList.DataProvider() {
          @Override
          public int getElementCount() {
            return getActiveStock().size();
          }

          @Override
          public void updateElement(int index, Pane row) {
            List<BigItemStack> active = getActiveStock();
            if (index < 0 || index >= active.size()) {
              return;
            }

            BigItemStack entry = active.get(index);
            ItemIcon icon = row.findPaneOfTypeByID("itemIcon", ItemIcon.class);
            if (icon != null) {
              ItemStack display = entry.stack.copy();
              int displayCount = entry.isInfinite() ? 1 : Math.max(1, Math.min(64, entry.count));
              display.setCount(displayCount);
              icon.setItem(display);
            }

            Text name = row.findPaneOfTypeByID("itemName", Text.class);
            if (name != null) {
              name.setText(entry.stack.getHoverName());
            }

            TextField amountField = row.findPaneOfTypeByID("requestAmount", TextField.class);
            if (amountField != null) {
              String key = getStackKey(entry.stack);
              if (viewMode == StockViewMode.STORAGE) {
                Integer stored = requestAmounts.get(key);
                amountField.setText(stored == null || stored <= 0 ? "" : String.valueOf(stored));
                amountField.setHandler(field -> onAmountInput(field, key));
              } else {
                amountField.setText("");
                amountField.setHandler(field -> {});
              }
            }
          }
        });
    stockList.refreshElementPanes();
  }

  private void onAmountInput(TextField field, String key) {
    try {
      int parsed = Integer.parseInt(field.getText());
      if (parsed > 0) {
        requestAmounts.put(key, parsed);
      } else {
        requestAmounts.remove(key);
      }
    } catch (NumberFormatException ex) {
      requestAmounts.remove(key);
    }
  }

  private void requestAll() {
    if (viewMode != StockViewMode.STORAGE || !moduleView.hasNetwork()) {
      return;
    }
    List<BigItemStack> order = new ArrayList<>();
    Map<String, Integer> requested = new HashMap<>();
    for (Map.Entry<String, Integer> entry : requestAmounts.entrySet()) {
      int amount = entry.getValue() == null ? 0 : entry.getValue();
      if (amount <= 0) {
        continue;
      }
      ItemStack stack = requestStacks.get(entry.getKey());
      if (stack == null || stack.isEmpty()) {
        continue;
      }
      if (!infiniteItems.contains(entry.getKey())) {
        int available = availableCounts.getOrDefault(entry.getKey(), 0);
        amount = Math.min(amount, Math.max(1, available));
      }
      if (amount <= 0) {
        continue;
      }
      order.add(new BigItemStack(stack.copy(), amount));
      requested.put(entry.getKey(), amount);
    }

    if (order.isEmpty()) {
      return;
    }
    PacketDistributor.sendToServer(
        new CreateShopBatchRequestPayload(building.getPosition(), order));

    if (!requested.isEmpty()) {
      for (BigItemStack entry : storageStock) {
        String key = getStackKey(entry.stack);
        Integer amount = requested.get(key);
        if (amount == null || amount <= 0) {
          continue;
        }
        if (!entry.isInfinite()) {
          entry.count = Math.max(0, entry.count - amount);
          availableCounts.put(key, entry.count);
        }
      }
      if (stockList != null) {
        stockList.refreshElementPanes();
      }
    }
  }

  private String getStackKey(ItemStack stack) {
    String key = stack.getItem().builtInRegistryHolder().key().location().toString();
    if (stack.has(DataComponents.CUSTOM_DATA)) {
      key += "|" + stack.get(DataComponents.CUSTOM_DATA);
    }
    return key;
  }

  private void setViewMode(StockViewMode next) {
    if (next == null) {
      return;
    }
    viewMode = next;
    refreshModeUi();
    if (stockList != null) {
      stockList.refreshElementPanes();
    }
  }

  private void refreshModeUi() {
    if (stockList == null) {
      return;
    }
    if (viewMode == StockViewMode.HUT) {
      stockList.setEmptyText(
          Component.translatable("com.thesettler_x_create.gui.createshop.stock.hut_empty"));
    } else if (!moduleView.hasNetwork()) {
      stockList.setEmptyText(
          Component.translatable("com.thesettler_x_create.gui.createshop.stock.network_missing"));
    } else {
      stockList.setEmptyText(
          Component.translatable("com.thesettler_x_create.gui.createshop.stock.storage_empty"));
    }

    if (viewHutButton != null) {
      viewHutButton.setEnabled(viewMode != StockViewMode.HUT);
    }
    if (viewStorageButton != null) {
      viewStorageButton.setEnabled(viewMode != StockViewMode.STORAGE);
    }
    Button requestAllButton = findPaneOfTypeByID("requestAll", Button.class);
    if (requestAllButton != null) {
      requestAllButton.setVisible(viewMode == StockViewMode.STORAGE);
      requestAllButton.setEnabled(viewMode == StockViewMode.STORAGE && moduleView.hasNetwork());
    }
  }

  private List<BigItemStack> getActiveStock() {
    return viewMode == StockViewMode.HUT ? hutStock : storageStock;
  }
}
