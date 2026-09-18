package com.thesettler_x_create.minecolonies.moduleview;

import com.ldtteam.blockui.views.BOWindow;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.core.colony.buildings.moduleviews.ItemListModuleView;
import com.thesettler_x_create.TheSettlerXCreate;
import com.thesettler_x_create.minecolonies.client.gui.ShopColonyDeniedListModuleWindow;
import java.util.Set;
import java.util.function.Function;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * The block list of what the colony may not draw from this shop's Create network.
 *
 * <p>MineColonies' own {@link ItemListModuleView} does the work, including the message that carries
 * a click back to the server. Only the window differs: its layout is a copy of MineColonies'
 * filterable list with a line explaining which way round the list works, because a list of items
 * under a shop tab reads just as easily as "these are the ones the colony gets", and its rows say
 * allowed or forbidden instead of on and off.
 */
public class ShopColonyDeniedListModuleView extends ItemListModuleView {
  private static final ResourceLocation LAYOUT =
      ResourceLocation.fromNamespaceAndPath(
          TheSettlerXCreate.MODID, "gui/layouthuts/layoutcreateshop_colonydenied.xml");

  private static final ResourceLocation ICON =
      ResourceLocation.fromNamespaceAndPath(
          TheSettlerXCreate.MODID, "textures/gui/modules/colony_denied.png");

  public ShopColonyDeniedListModuleView(
      String id,
      Component desc,
      boolean inverted,
      Function<IBuildingView, Set<ItemStorage>> allItems) {
    super(id, desc, inverted, allItems);
  }

  @Override
  public BOWindow getWindow() {
    return new ShopColonyDeniedListModuleWindow(this, LAYOUT);
  }

  @Override
  public ResourceLocation getIconResourceLocation() {
    // MineColonies names the tab icon after the list's id and looks for it among their own
    // textures, where a list of ours is not, so the tab showed the missing texture. Ours is a rack
    // under a no entry sign, which is what this list does to it.
    return ICON;
  }
}
