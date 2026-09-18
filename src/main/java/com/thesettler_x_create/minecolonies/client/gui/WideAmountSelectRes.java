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
    TextField count = findPaneOfTypeByID(COUNT, TextField.class);
    if (count == null) {
      return;
    }
    // TextField.writeText hands the filter the typed character alone, not the whole field, and
    // ONLY_POSITIVE_NUMBERS_MAX1k answers a "0" with an empty string, because the number it parses
    // is not positive. So a zero could never be typed into this field at all, wherever the cursor
    // stood, and only 1 to 9 arrived. MineColonies' warehouse minimum has the same field and the
    // same hole: 10 cannot be entered there either, only 11.
    //
    // ONLY_NUMBERS hands the character straight back, and the thousand cap goes with it.
    count.setFilter(InputFilters.ONLY_NUMBERS);
    count.setCursorPosition(count.getText().length());
  }
}
