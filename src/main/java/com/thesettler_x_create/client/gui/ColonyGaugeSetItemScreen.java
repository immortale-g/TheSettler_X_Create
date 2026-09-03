package com.thesettler_x_create.client.gui;

import com.simibubi.create.foundation.gui.AllGuiTextures;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.gui.menu.AbstractSimiContainerScreen;
import com.simibubi.create.foundation.gui.widget.IconButton;
import com.thesettler_x_create.init.ModItems;
import com.thesettler_x_create.menu.ColonyGaugeSetItemMenu;
import java.util.Collections;
import java.util.List;
import net.createmod.catnip.gui.element.GuiGameElement;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

public class ColonyGaugeSetItemScreen extends AbstractSimiContainerScreen<ColonyGaugeSetItemMenu> {
  private static final Component TITLE =
      Component.translatable("com.thesettler_x_create.gui.colony_gauge.set_item_label");

  private IconButton confirmButton;
  private List<Rect2i> extraAreas = Collections.emptyList();

  public ColonyGaugeSetItemScreen(ColonyGaugeSetItemMenu menu, Inventory inv, Component title) {
    super(menu, inv, title);
  }

  @Override
  protected void init() {
    int bgHeight = AllGuiTextures.FACTORY_GAUGE_SET_ITEM.getHeight();
    int bgWidth = AllGuiTextures.FACTORY_GAUGE_SET_ITEM.getWidth();
    setWindowSize(bgWidth, bgHeight + AllGuiTextures.PLAYER_INVENTORY.getHeight());
    super.init();
    clearWidgets();
    int x = getGuiLeft();
    int y = getGuiTop();

    confirmButton = new IconButton(x + bgWidth - 40, y + bgHeight - 25, AllIcons.I_CONFIRM);
    confirmButton.withCallback(() -> minecraft.player.closeContainer());
    addRenderableWidget(confirmButton);

    extraAreas = List.of(new Rect2i(x + bgWidth, y + bgHeight - 30, 40, 20));
  }

  @Override
  protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
    int x = getGuiLeft();
    int y = getGuiTop();
    AllGuiTextures.FACTORY_GAUGE_SET_ITEM.render(graphics, x - 5, y);
    renderPlayerInventory(graphics, x + 5, y + 94);

    ItemStack stack = ModItems.COLONY_GAUGE.get().getDefaultInstance();
    graphics.drawString(font, TITLE, x + imageWidth / 2 - font.width(TITLE) / 2 - 5, y + 4, 0x3D3C48, false);

    GuiGameElement.of(stack).scale(3).render(graphics, x + 180, y + 48);
  }

  @Override
  public List<Rect2i> getExtraAreas() {
    return extraAreas;
  }
}
