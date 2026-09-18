package com.thesettler_x_create.minecolonies.client.gui;

import com.ldtteam.blockui.controls.Button;
import com.ldtteam.blockui.controls.TextField;
import com.ldtteam.blockui.views.BOWindow;
import com.ldtteam.structurize.client.gui.WindowSelectRes;
import com.ldtteam.structurize.client.gui.util.InputFilters;
import com.thesettler_x_create.TheSettlerXCreate;
import java.util.List;
import java.util.function.BiConsumer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Structurize' item picker, with an amount field that takes more than 1000.
 *
 * <p>Two things stand in the way of a larger number there, and both belong to Structurize. Its
 * amount field carries {@code ONLY_POSITIVE_NUMBERS_MAX1k}, which refuses any keystroke that would
 * push the value past a thousand, and the field is thirty pixels wide, where BlockUI stops typing
 * at the width of the field. A Create network minimum runs into the thousands, so the field gets
 * the plain number filter and a layout of its own with room to type.
 *
 * <p>Everything else is theirs: the item list, the search, the confirm step and the callback.
 */
public class WideAmountSelectRes extends WindowSelectRes {
  private static final ResourceLocation LAYOUT =
      ResourceLocation.fromNamespaceAndPath(
          TheSettlerXCreate.MODID, "gui/layoutselectres_wide.xml");

  public WideAmountSelectRes(
      BOWindow previous,
      Component desc,
      List<ItemStack> allItems,
      BiConsumer<ItemStack, Integer> result,
      Component countText) {
    super(LAYOUT, previous, desc, ItemStack.EMPTY, allItems, result, true, countText);
  }

  @Override
  protected void secondaryConfirm(Button button) {
    super.secondaryConfirm(button);
    // The amount field is shown by that call, and it arrives with the thousand cap on it.
    TextField count = findPaneOfTypeByID(COUNT, TextField.class);
    if (count != null) {
      count.setFilter(InputFilters.ONLY_NUMBERS);
    }
  }
}
