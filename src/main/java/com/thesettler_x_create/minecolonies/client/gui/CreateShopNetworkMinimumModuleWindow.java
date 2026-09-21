package com.thesettler_x_create.minecolonies.client.gui;

import com.ldtteam.blockui.Pane;
import com.ldtteam.blockui.controls.Button;
import com.ldtteam.blockui.controls.ItemIcon;
import com.ldtteam.blockui.controls.Text;
import com.ldtteam.blockui.views.ScrollingList;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.core.client.gui.AbstractModuleWindow;
import com.thesettler_x_create.TheSettlerXCreate;
import com.thesettler_x_create.minecolonies.moduleview.CreateShopNetworkMinimumModuleView;
import com.thesettler_x_create.network.SetCreateShopNetworkMinimumPayload;
import com.thesettler_x_create.stock.AmountText;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Lets the player say how much of an item kind stays in the shop's Create network. Adding one goes
 * through {@link CreateShopAddMinimumWindow}: every item in the game can be chosen there, not only
 * what the network happens to hold right now.
 *
 * <p>The list shows what is set, and nothing is typed into it; an amount is changed by picking the
 * item again.
 */
public class CreateShopNetworkMinimumModuleWindow
    extends AbstractModuleWindow<CreateShopNetworkMinimumModuleView> {

  private final CreateShopNetworkMinimumModuleView moduleView;
  private final ScrollingList minimumList;

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

  /**
   * Opens the picker. At the limit it offers only the item kinds the shop already guards, so their
   * amounts can still be changed: the list itself has nothing to type into, and a dead button was
   * the only answer a player got who wanted to raise one of thirty minimums. The server draws the
   * same line, refusing a kind that is not guarded yet rather than any change at all.
   */
  private void addMinimum() {
    boolean atLimit = moduleView.hasReachedLimit();
    List<ItemStack> choices =
        atLimit
            ? moduleView.getEntries().stream()
                .map(CreateShopNetworkMinimumModuleView.Entry::stack)
                .toList()
            : IColonyManager.getInstance().getCompatibilityManager().getListOfAllItems();
    new CreateShopAddMinimumWindow(this, choices, moduleView::getMinimum, this::setMinimum, atLimit)
        .open();
  }

  /** What the picker settled on: 0 clears the item again, the same as the X in the list. */
  private void setMinimum(ItemStack stack, int amount) {
    if (stack == null || stack.isEmpty()) {
      return;
    }
    ItemStack kind = stack.copyWithCount(1);
    PacketDistributor.sendToServer(
        new SetCreateShopNetworkMinimumPayload(buildingView.getPosition(), kind, amount));
    moduleView.previewMinimum(kind, amount);
  }

  private void removeMinimum(Button button) {
    if (minimumList == null) {
      return;
    }
    int row = minimumList.getListElementIndexByPane(button);
    List<CreateShopNetworkMinimumModuleView.Entry> entries = moduleView.getEntries();
    if (row < 0 || row >= entries.size()) {
      return;
    }
    setMinimum(entries.get(row).stack(), 0);
  }

  private void updateList() {
    if (minimumList == null) {
      return;
    }
    // Read straight from the view, not from a copy taken when the window opened: what the server
    // stored arrives a tick or two later, and a copy would keep showing the state from before.
    minimumList.setDataProvider(
        new ScrollingList.DataProvider() {
          @Override
          public int getElementCount() {
            return moduleView.getEntries().size();
          }

          @Override
          public void updateElement(int index, Pane row) {
            List<CreateShopNetworkMinimumModuleView.Entry> entries = moduleView.getEntries();
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
              amount.setText(Component.literal(AmountText.format(entry.amount())));
            }
          }
        });
    minimumList.refreshElementPanes();
  }
}
