package com.thesettler_x_create.client.gui;

import com.thesettler_x_create.menu.CreateShopMenu;
import com.thesettler_x_create.network.SetCreateShopAddressPayload;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

public class CreateShopScreen extends AbstractContainerScreen<CreateShopMenu> {
  private static final int ADDRESS_MAX_LENGTH = 64;
  private static final Component LABEL_ADDRESS =
      Component.translatable("com.thesettler_x_create.gui.createshop.address_label");
  private static final Component LABEL_SAVE =
      Component.translatable("com.thesettler_x_create.gui.createshop.save");

  private EditBox addressBox;

  public CreateShopScreen(CreateShopMenu menu, Inventory playerInventory, Component title) {
    super(menu, playerInventory, title);
    this.imageWidth = 176;
    this.imageHeight = 88;
  }

  @Override
  protected void init() {
    super.init();
    int x = (width - imageWidth) / 2;
    int y = (height - imageHeight) / 2;

    addressBox = new EditBox(font, x + 10, y + 25, 156, 18, LABEL_ADDRESS);
    addressBox.setMaxLength(ADDRESS_MAX_LENGTH);
    addressBox.setValue(menu.getShopAddress());
    addRenderableWidget(addressBox);

    Button save =
        Button.builder(
                LABEL_SAVE,
                btn -> {
                  String address = addressBox.getValue();
                  PacketDistributor.sendToServer(
                      new SetCreateShopAddressPayload(menu.getPos(), address));
                })
            .bounds(x + 10, y + 50, 60, 20)
            .build();
    addRenderableWidget(save);
  }

  @Override
  protected void renderBg(
      net.minecraft.client.gui.GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
    // No background texture; keep it simple for now.
  }

  @Override
  public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
    if (addressBox != null
        && addressBox.isFocused()
        && addressBox.keyPressed(keyCode, scanCode, modifiers)) {
      return true;
    }
    return super.keyPressed(keyCode, scanCode, modifiers);
  }

  @Override
  public boolean charTyped(char codePoint, int modifiers) {
    if (addressBox != null
        && addressBox.isFocused()
        && addressBox.charTyped(codePoint, modifiers)) {
      return true;
    }
    return super.charTyped(codePoint, modifiers);
  }

  @Override
  protected void renderLabels(
      net.minecraft.client.gui.GuiGraphics graphics, int mouseX, int mouseY) {
    graphics.drawString(font, LABEL_ADDRESS, 10, 10, 0x404040, false);
  }
}
