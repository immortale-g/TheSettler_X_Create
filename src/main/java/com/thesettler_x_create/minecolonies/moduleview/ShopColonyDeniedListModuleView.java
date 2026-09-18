package com.thesettler_x_create.minecolonies.moduleview;

import com.ldtteam.blockui.views.BOWindow;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.core.client.gui.modules.building.ItemListModuleWindow;
import com.minecolonies.core.colony.buildings.moduleviews.ItemListModuleView;
import com.thesettler_x_create.TheSettlerXCreate;
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
 * under a shop tab reads just as easily as "these are the ones the colony gets".
 */
public class ShopColonyDeniedListModuleView extends ItemListModuleView {
  private static final ResourceLocation LAYOUT =
      ResourceLocation.fromNamespaceAndPath(
          TheSettlerXCreate.MODID, "gui/layouthuts/layoutcreateshop_colonydenied.xml");

  public ShopColonyDeniedListModuleView(
      String id,
      Component desc,
      boolean inverted,
      Function<IBuildingView, Set<ItemStorage>> allItems) {
    super(id, desc, inverted, allItems);
  }

  @Override
  public BOWindow getWindow() {
    return new ItemListModuleWindow(this, LAYOUT);
  }
}
