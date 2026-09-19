package com.thesettler_x_create.minecolonies.client.gui;

import com.ldtteam.blockui.Pane;
import com.ldtteam.blockui.controls.Button;
import com.ldtteam.blockui.controls.ItemIcon;
import com.ldtteam.blockui.controls.Text;
import com.ldtteam.blockui.controls.TextField;
import com.ldtteam.blockui.views.BOWindow;
import com.ldtteam.blockui.views.ScrollingList;
import com.minecolonies.core.client.gui.AbstractWindowSkeleton;
import com.thesettler_x_create.TheSettlerXCreate;
import com.thesettler_x_create.stock.AmountText;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.ToIntFunction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * One page for adding a network minimum: search, pick the item, type the amount, set it. What is
 * already guarded stands next to its row, so the same item is not set twice by accident; picking it
 * again is how its amount is changed.
 *
 * <p>Ours rather than Structurize' picker, which does the same job in two windows and cannot take
 * the amounts this needs: its field hands every key press to a filter that answers a "0" with
 * nothing and caps at a thousand, and a Create network minimum runs into the thousands.
 */
public class CreateShopAddMinimumWindow extends AbstractWindowSkeleton {
  private static final ResourceLocation LAYOUT =
      ResourceLocation.fromNamespaceAndPath(
          TheSettlerXCreate.MODID, "gui/layoutcreateshop_addminimum.xml");

  /** Rebuilding a list of several thousand items on every key press is felt. */
  private static final int SEARCH_DELAY_TICKS = 5;

  private static final int BLACK = 0x000000;
  private static final int RED = 0xAA0000;

  private static final int KEY_ENTER = 257;
  private static final int KEY_NUMPAD_ENTER = 335;

  /** A field filter only ever sees one key press, so it may only judge that much. */
  private static final TextField.Filter AMOUNT_FILTER =
      new TextField.Filter() {
        @Override
        public String filter(String fragment) {
          return fragment;
        }

        @Override
        public boolean isAllowedCharacter(char character) {
          return AmountText.isTypable(character);
        }
      };

  private final List<ItemStack> allItems;
  private final ToIntFunction<ItemStack> currentMinimum;
  private final BiConsumer<ItemStack, Integer> onChosen;
  private final ScrollingList itemList;
  private final TextField amountField;

  private List<ItemStack> shown = new ArrayList<>();
  private String search = "";
  private int searchDelay = -1;
  private ItemStack chosen = ItemStack.EMPTY;
  private boolean complaining;

  /**
   * @param origin the hut window, which this returns to
   * @param allItems every item that may be picked
   * @param currentMinimum what is guarded for an item today, 0 when nothing is
   * @param onChosen told about the item and the amount the player settled on
   */
  public CreateShopAddMinimumWindow(
      BOWindow origin,
      List<ItemStack> allItems,
      ToIntFunction<ItemStack> currentMinimum,
      BiConsumer<ItemStack, Integer> onChosen) {
    this(origin, allItems, currentMinimum, onChosen, false);
  }

  /**
   * @param origin the hut window, which this returns to
   * @param allItems every item that may be picked
   * @param currentMinimum what is guarded for an item today, 0 when nothing is
   * @param onChosen told about the item and the amount the player settled on
   * @param atLimit whether the shop already guards as many item kinds as it may, in which case
   *     {@code allItems} holds only the ones it guards: their amounts can still be changed, and the
   *     window says why nothing else is on offer
   */
  public CreateShopAddMinimumWindow(
      BOWindow origin,
      List<ItemStack> allItems,
      ToIntFunction<ItemStack> currentMinimum,
      BiConsumer<ItemStack, Integer> onChosen,
      boolean atLimit) {
    super(origin, LAYOUT);
    this.allItems = allItems;
    this.currentMinimum = currentMinimum;
    this.onChosen = onChosen;

    itemList = findPaneOfTypeByID("items", ScrollingList.class);
    amountField = findPaneOfTypeByID("amount", TextField.class);
    if (amountField != null) {
      amountField.setFilter(AMOUNT_FILTER);
    }

    registerButton("selectItem", this::itemClicked);
    registerButton("confirm", (Button button) -> confirm());
    registerButton("cancel", (Button button) -> close());

    TextField searchField = findPaneOfTypeByID("search", TextField.class);
    if (searchField != null) {
      searchField.setHandler(
          field -> {
            String typed = field.getText();
            if (!typed.equals(search)) {
              search = typed;
              searchDelay = SEARCH_DELAY_TICKS;
            }
          });
    }
    showChosen();
    if (atLimit) {
      say(BLACK, "com.thesettler_x_create.gui.createshop.networkminimum.limitreached");
    }
  }

  @Override
  public void onOpened() {
    super.onOpened();
    updateItems();
  }

  @Override
  public void onUpdate() {
    super.onUpdate();
    if (searchDelay > 0) {
      searchDelay--;
    } else if (searchDelay == 0) {
      searchDelay = -1;
      updateItems();
    }
    if (complaining && readAmount() >= 0 && !chosen.isEmpty()) {
      say(BLACK, "com.thesettler_x_create.gui.createshop.networkminimum.shorthand");
      complaining = false;
    }
  }

  @Override
  public boolean onUnhandledKeyTyped(int character, int key) {
    if (key == KEY_ENTER || key == KEY_NUMPAD_ENTER) {
      confirm();
      return true;
    }
    return super.onUnhandledKeyTyped(character, key);
  }

  private void itemClicked(Button button) {
    if (itemList == null) {
      return;
    }
    int row = itemList.getListElementIndexByPane(button);
    if (row < 0 || row >= shown.size()) {
      return;
    }
    chosen = shown.get(row).copyWithCount(1);
    int guarded = currentMinimum.applyAsInt(chosen);
    if (guarded > 0 && amountField != null) {
      // An item that already has a minimum brings it along, so a change starts from what is set.
      // An item without one leaves the field alone: whatever was typed before picking was meant.
      amountField.setText(AmountText.format(guarded));
      amountField.setCursorPosition(amountField.getText().length());
    }
    showChosen();
  }

  private void confirm() {
    if (chosen.isEmpty()) {
      complain("com.thesettler_x_create.gui.createshop.networkminimum.noitem");
      return;
    }
    int amount = readAmount();
    if (amount < 0) {
      complain("com.thesettler_x_create.gui.createshop.networkminimum.notanumber");
      return;
    }
    onChosen.accept(chosen, amount);
    close();
  }

  private int readAmount() {
    return amountField == null ? -1 : AmountText.parse(amountField.getText());
  }

  private void showChosen() {
    ItemIcon icon = findPaneOfTypeByID("chosenIcon", ItemIcon.class);
    if (icon != null) {
      icon.setItem(chosen);
    }
    Text name = findPaneOfTypeByID("chosenName", Text.class);
    if (name != null) {
      name.setText(
          chosen.isEmpty()
              ? Component.translatable(
                  "com.thesettler_x_create.gui.createshop.networkminimum.noitemyet")
              : chosen.getHoverName());
    }
  }

  private void complain(String translationKey) {
    say(RED, translationKey);
    complaining = true;
  }

  private void say(int color, String translationKey) {
    Text hint = findPaneOfTypeByID("hint", Text.class);
    if (hint == null) {
      return;
    }
    hint.setColors(color);
    hint.setText(Component.translatable(translationKey));
  }

  private void updateItems() {
    shown = matching(search);
    if (itemList == null) {
      return;
    }
    List<ItemStack> rows = shown;
    itemList.setDataProvider(
        new ScrollingList.DataProvider() {
          @Override
          public int getElementCount() {
            return rows.size();
          }

          @Override
          public void updateElement(int index, @NotNull Pane row) {
            if (index < 0 || index >= rows.size()) {
              return;
            }
            ItemStack stack = rows.get(index);
            ItemIcon icon = row.findPaneOfTypeByID("itemIcon", ItemIcon.class);
            if (icon != null) {
              icon.setItem(stack);
            }
            Text name = row.findPaneOfTypeByID("itemName", Text.class);
            if (name != null) {
              name.setText(stack.getHoverName());
            }
            Text current = row.findPaneOfTypeByID("itemCurrent", Text.class);
            if (current != null) {
              int guarded = currentMinimum.applyAsInt(stack);
              current.setText(
                  guarded > 0 ? Component.literal(AmountText.format(guarded)) : Component.empty());
            }
          }
        });
    itemList.refreshElementPanes();
  }

  /**
   * What the search field points at, the closest match first. An empty field is every item in the
   * order the game hands them over.
   */
  private List<ItemStack> matching(String query) {
    String needle = query.trim().toLowerCase(Locale.ROOT);
    if (needle.isEmpty()) {
      return new ArrayList<>(allItems);
    }
    List<ItemStack> hits = new ArrayList<>();
    for (ItemStack stack : allItems) {
      if (nameOf(stack).contains(needle)) {
        hits.add(stack);
      }
    }
    // A name that starts with what was typed is what the player meant far more often than one that
    // merely carries it somewhere in the middle: "iron" should not bury the iron ingot under the
    // blocks of raw iron.
    hits.sort(
        Comparator.<ItemStack, Boolean>comparing(stack -> !nameOf(stack).startsWith(needle))
            .thenComparing(CreateShopAddMinimumWindow::nameOf));
    return hits;
  }

  private static String nameOf(ItemStack stack) {
    return stack.getHoverName().getString().toLowerCase(Locale.ROOT);
  }
}
