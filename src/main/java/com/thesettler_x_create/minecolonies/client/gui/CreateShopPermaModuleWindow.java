package com.thesettler_x_create.minecolonies.client.gui;

import com.ldtteam.blockui.Pane;
import com.ldtteam.blockui.controls.Button;
import com.ldtteam.blockui.controls.ItemIcon;
import com.ldtteam.blockui.controls.Text;
import com.ldtteam.blockui.controls.TextField;
import com.ldtteam.blockui.views.ScrollingList;
import com.minecolonies.core.client.gui.AbstractModuleWindow;
import com.thesettler_x_create.TheSettlerXCreate;
import com.thesettler_x_create.minecolonies.moduleview.CreateShopPermaModuleView;
import com.thesettler_x_create.network.SetCreateShopPermaOrePayload;
import com.thesettler_x_create.network.SetCreateShopPermaWaitPayload;
import java.util.List;
import java.util.Locale;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public class CreateShopPermaModuleWindow extends AbstractModuleWindow<CreateShopPermaModuleView> {
  private final com.minecolonies.api.colony.buildings.views.IBuildingView building;
  private final CreateShopPermaModuleView moduleView;
  private final ScrollingList oreList;
  private final Button waitFullButton;
  private final Button selectAllButton;
  private final Button selectNoneButton;
  private final TextField searchInput;
  private List<CreateShopPermaModuleView.OreEntry> filteredOres = List.of();

  public CreateShopPermaModuleWindow(CreateShopPermaModuleView moduleView) {
    super(
        moduleView,
        ResourceLocation.fromNamespaceAndPath(
            TheSettlerXCreate.MODID, "gui/layouthuts/layoutcreateshop_perma.xml"));
    this.building = moduleView.getBuildingView();
    this.moduleView = moduleView;

    Text desc = findPaneOfTypeByID("desc", Text.class);
    if (desc != null) {
      desc.setText(moduleView.getDesc());
    }

    oreList = findPaneOfTypeByID("oreList", ScrollingList.class);
    waitFullButton = findPaneOfTypeByID("waitFull", Button.class);
    if (waitFullButton != null) {
      waitFullButton.setHandler(btn -> toggleWaitFull());
    }
    selectAllButton = findPaneOfTypeByID("selectAll", Button.class);
    if (selectAllButton != null) {
      selectAllButton.setHandler(btn -> setAll(true));
    }
    selectNoneButton = findPaneOfTypeByID("selectNone", Button.class);
    if (selectNoneButton != null) {
      selectNoneButton.setHandler(btn -> setAll(false));
    }
    searchInput = findPaneOfTypeByID("searchInput", TextField.class);
    if (searchInput != null) {
      searchInput.setText("");
      searchInput.setHandler(field -> updateOreList());
    }
    updateWaitFullLabel();
    updateOreList();
  }

  private void toggleWaitFull() {
    if (!moduleView.isEnabled()) {
      return;
    }
    boolean next = !moduleView.isWaitFullStack();
    moduleView.setWaitFullStack(next);
    PacketDistributor.sendToServer(new SetCreateShopPermaWaitPayload(building.getPosition(), next));
    updateWaitFullLabel();
  }

  private void updateWaitFullLabel() {
    if (waitFullButton == null) {
      return;
    }
    String key =
        moduleView.isWaitFullStack()
            ? "com.thesettler_x_create.gui.createshop.perma.wait_full_on"
            : "com.thesettler_x_create.gui.createshop.perma.wait_full_off";
    waitFullButton.setText(Component.translatable(key));
  }

  private void updateOreList() {
    if (oreList == null) {
      return;
    }
    List<CreateShopPermaModuleView.OreEntry> ores = moduleView.getOres();
    filteredOres = applyFilter(ores);
    if (!moduleView.isEnabled()) {
      oreList.setEmptyText(
          Component.translatable("com.thesettler_x_create.gui.createshop.perma.locked"));
    } else if (filteredOres.isEmpty()) {
      oreList.setEmptyText(
          Component.translatable("com.thesettler_x_create.gui.createshop.perma.empty"));
    }

    oreList.setDataProvider(
        new ScrollingList.DataProvider() {
          @Override
          public int getElementCount() {
            return filteredOres.size();
          }

          @Override
          public void updateElement(int index, Pane row) {
            if (index < 0 || index >= filteredOres.size()) {
              return;
            }
            CreateShopPermaModuleView.OreEntry entry = filteredOres.get(index);
            ItemStack stack = entry.getStack();

            ItemIcon icon = row.findPaneOfTypeByID("itemIcon", ItemIcon.class);
            if (icon != null) {
              ItemStack display = stack.copy();
              display.setCount(1);
              icon.setItem(display);
            }

            Text name = row.findPaneOfTypeByID("itemName", Text.class);
            if (name != null) {
              name.setText(stack.getHoverName());
            }

            Button toggle = row.findPaneOfTypeByID("toggle", Button.class);
            if (toggle != null) {
              String labelKey =
                  entry.isSelected()
                      ? "com.thesettler_x_create.gui.createshop.perma.on"
                      : "com.thesettler_x_create.gui.createshop.perma.off";
              toggle.setText(Component.translatable(labelKey));
              toggle.setHandler(btn -> toggleEntry(entry));
            }
          }
        });
    oreList.refreshElementPanes();
  }

  private List<CreateShopPermaModuleView.OreEntry> applyFilter(
      List<CreateShopPermaModuleView.OreEntry> ores) {
    String filter = "";
    if (searchInput != null && searchInput.getText() != null) {
      filter = searchInput.getText().trim().toLowerCase(Locale.ROOT);
    }
    if (filter.isEmpty()) {
      return ores;
    }
    List<CreateShopPermaModuleView.OreEntry> result = new java.util.ArrayList<>();
    for (CreateShopPermaModuleView.OreEntry entry : ores) {
      if (entry == null) {
        continue;
      }
      ItemStack stack = entry.getStack();
      if (stack == null || stack.isEmpty()) {
        continue;
      }
      String name = stack.getHoverName().getString().toLowerCase(Locale.ROOT);
      String id =
          net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
      if (name.contains(filter) || id.toLowerCase(Locale.ROOT).contains(filter)) {
        result.add(entry);
      }
    }
    return result;
  }

  private void setAll(boolean enabled) {
    if (!moduleView.isEnabled()) {
      return;
    }
    List<CreateShopPermaModuleView.OreEntry> ores = moduleView.getOres();
    for (CreateShopPermaModuleView.OreEntry entry : ores) {
      if (entry == null) {
        continue;
      }
      if (entry.isSelected() == enabled) {
        continue;
      }
      entry.setSelected(enabled);
      ItemStack stack = entry.getStack();
      var id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
      PacketDistributor.sendToServer(
          new SetCreateShopPermaOrePayload(building.getPosition(), id, enabled));
    }
    updateOreList();
  }

  private void toggleEntry(CreateShopPermaModuleView.OreEntry entry) {
    if (!moduleView.isEnabled()) {
      return;
    }
    boolean next = !entry.isSelected();
    entry.setSelected(next);
    ItemStack stack = entry.getStack();
    var id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
    PacketDistributor.sendToServer(
        new SetCreateShopPermaOrePayload(building.getPosition(), id, next));
    oreList.refreshElementPanes();
  }
}
