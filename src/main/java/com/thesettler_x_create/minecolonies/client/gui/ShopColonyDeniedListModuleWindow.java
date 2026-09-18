package com.thesettler_x_create.minecolonies.client.gui;

import static com.minecolonies.api.util.constant.WindowConstants.BUTTON_SWITCH;
import static com.minecolonies.api.util.constant.WindowConstants.RESOURCE_ICON;
import static com.minecolonies.api.util.constant.WindowConstants.RESOURCE_NAME;

import com.ldtteam.blockui.Pane;
import com.ldtteam.blockui.controls.Button;
import com.ldtteam.blockui.controls.ItemIcon;
import com.ldtteam.blockui.controls.Text;
import com.ldtteam.blockui.views.ScrollingList;
import com.minecolonies.api.colony.buildings.modules.IItemListModuleView;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.core.client.gui.modules.building.ItemListModuleWindow;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * MineColonies' filterable list, saying in words what the row button means.
 *
 * <p>Theirs writes "On" and "Off" there, which are the right words for a guard who fetches or does
 * not fetch. On a list headed "off limits" they read backwards: every row of an empty block list
 * says "On", and On next to a forbidden item is exactly the wrong way round. This one says whether
 * the colony may draw that item or not.
 *
 * <p>Everything else stays MineColonies': the list, the search, the module view and the message
 * that carries a click back to the server.
 */
public class ShopColonyDeniedListModuleWindow extends ItemListModuleWindow {
  private static final int BLACK = 0x000000;

  public ShopColonyDeniedListModuleWindow(IItemListModuleView moduleView, ResourceLocation layout) {
    super(moduleView, layout);
    // Takes the button over from MineColonies, whose own handler reads the state back off the
    // label and compares it to their "On" - a comparison our words would never match, so every
    // click would toggle the wrong way.
    registerButton(BUTTON_SWITCH, this::switchClicked);
  }

  private void switchClicked(@NotNull Button button) {
    int row = resourceList.getListElementIndexByPane(button);
    if (row < 0 || row >= currentDisplayedList.size()) {
      return;
    }
    ItemStorage item = currentDisplayedList.get(row);
    // On this list, being in it is what forbids an item.
    if (moduleView.isAllowedItem(item)) {
      moduleView.removeItem(item);
    } else {
      moduleView.addItem(item);
    }
    resourceList.refreshElementPanes();
  }

  @Override
  protected void updateResourceList() {
    resourceList.enable();
    resourceList.show();

    resourceList.setDataProvider(
        new ScrollingList.DataProvider() {
          @Override
          public int getElementCount() {
            return currentDisplayedList.size();
          }

          @Override
          public void updateElement(int index, @NotNull Pane rowPane) {
            if (index < 0 || index >= currentDisplayedList.size()) {
              return;
            }
            ItemStorage storage = currentDisplayedList.get(index);
            ItemStack resource = storage.getItemStack();

            Text name = rowPane.findPaneOfTypeByID(RESOURCE_NAME, Text.class);
            if (name != null) {
              name.setText(resource.getHoverName());
              name.setColors(BLACK);
            }
            ItemIcon icon = rowPane.findPaneOfTypeByID(RESOURCE_ICON, ItemIcon.class);
            if (icon != null) {
              icon.setItem(resource);
            }
            Button switchButton = rowPane.findPaneOfTypeByID(BUTTON_SWITCH, Button.class);
            if (switchButton != null) {
              switchButton.setText(
                  Component.translatable(
                      moduleView.isAllowedItem(storage)
                          ? "com.thesettler_x_create.gui.createshop.coloniesmaydraw.forbidden"
                          : "com.thesettler_x_create.gui.createshop.coloniesmaydraw.allowed"));
            }
          }
        });
  }
}
