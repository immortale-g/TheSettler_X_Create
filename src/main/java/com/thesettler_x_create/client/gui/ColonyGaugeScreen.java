package com.thesettler_x_create.client.gui;

import com.simibubi.create.content.logistics.AddressEditBox;
import com.simibubi.create.content.logistics.box.PackageStyles;
import com.simibubi.create.content.trains.station.NoShadowFontWrapper;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.gui.widget.IconButton;
import com.simibubi.create.foundation.gui.widget.ScrollInput;
import com.simibubi.create.foundation.utility.CreateLang;
import com.thesettler_x_create.blockentity.ColonyGaugeBehaviour;
import com.thesettler_x_create.init.ModItems;
import com.thesettler_x_create.network.ColonyGaugeConfigPacket;
import java.util.List;
import net.createmod.catnip.gui.AbstractSimiScreen;
import net.createmod.catnip.gui.element.GuiGameElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public class ColonyGaugeScreen extends AbstractSimiScreen {

  private final ColonyGaugeBehaviour behaviour;
  private AddressEditBox addressBox;
  private IconButton confirmButton;
  private IconButton deleteButton;
  private ScrollInput promiseExpiration;
  private boolean sendReset;
  private boolean sendClearPromises;

  public ColonyGaugeScreen(ColonyGaugeBehaviour behaviour) {
    this.behaviour = behaviour;
  }

  @Override
  protected void init() {
    int sizeX = AllGuiTextures.FACTORY_GAUGE_BOTTOM.getWidth();
    int sizeY =
        AllGuiTextures.FACTORY_GAUGE_RESTOCK.getHeight()
            + AllGuiTextures.FACTORY_GAUGE_BOTTOM.getHeight();
    setWindowSize(sizeX, sizeY);
    super.init();
    clearWidgets();

    int x = guiLeft;
    int y = guiTop;

    if (addressBox == null) {
      addressBox =
          new AddressEditBox(
              this,
              new NoShadowFontWrapper(font),
              x + 36,
              y + windowHeight - 51,
              108,
              10,
              false,
              behaviour.getAddressHint());
      addressBox.setValue(behaviour.manualAddress == null ? "" : behaviour.manualAddress);
      addressBox.setTextColor(0x555555);
    }
    addressBox.setX(x + 36);
    addressBox.setY(y + windowHeight - 51);
    addRenderableWidget(addressBox);

    confirmButton = new IconButton(x + sizeX - 33, y + sizeY - 25, AllIcons.I_CONFIRM);
    confirmButton.withCallback(() -> minecraft.setScreen(null));
    confirmButton.setToolTip(CreateLang.translate("gui.factory_panel.save_and_close").component());
    addRenderableWidget(confirmButton);

    deleteButton = new IconButton(x + sizeX - 55, y + sizeY - 25, AllIcons.I_TRASH);
    deleteButton.withCallback(
        () -> {
          sendReset = true;
          minecraft.setScreen(null);
        });
    deleteButton.setToolTip(CreateLang.translate("gui.factory_panel.reset").component());
    addRenderableWidget(deleteButton);

    promiseExpiration =
        new ScrollInput(x + 97, y + windowHeight - 24, 28, 16)
            .withRange(-1, 31)
            .titled(CreateLang.translate("gui.factory_panel.promises_expire_title").component());
    promiseExpiration.setState(behaviour.promiseClearingInterval);
    addRenderableWidget(promiseExpiration);
  }

  @Override
  public void tick() {
    super.tick();
    addressBox.tick();
    promiseExpiration.titled(
        CreateLang.translate(
                promiseExpiration.getState() == -1
                    ? "gui.factory_panel.promises_do_not_expire"
                    : "gui.factory_panel.promises_expire_title")
            .component());
  }

  @Override
  protected void renderWindow(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
    int x = guiLeft;
    int y = guiTop;

    // The recipe-mode texture's top strip doubles as the blue title bar, peeking out above the
    // restocker texture drawn on top of it (same trick Create's FactoryPanelScreen uses).
    AllGuiTextures.FACTORY_GAUGE_RECIPE.render(graphics, x, y - 16);
    AllGuiTextures.FACTORY_GAUGE_RESTOCK.render(graphics, x, y);
    AllGuiTextures.FACTORY_GAUGE_BOTTOM.render(
        graphics, x, y + AllGuiTextures.FACTORY_GAUGE_RESTOCK.getHeight());

    Component title =
        Component.translatable("com.thesettler_x_create.gui.colony_gauge.settings_title");
    graphics.drawString(font, title, x + 97 - font.width(title) / 2, y - 12, 0x3D3C48, false);

    // Item currently requested, shown in the bracket baked into the FACTORY_GAUGE_RESTOCK texture.
    int inputX = x + 88;
    int inputY = y + 12;
    graphics.renderItem(behaviour.getFilter(), inputX, inputY);
    if (mouseX >= inputX - 2
        && mouseX < inputX + 18
        && mouseY >= inputY - 2
        && mouseY < inputY + 18) {
      graphics.renderComponentTooltip(
          font,
          List.of(
              behaviour.getFilter().isEmpty()
                  ? CreateLang.translate("gui.factory_panel.empty_panel").component()
                  : CreateLang.translate(
                          "gui.factory_panel.sending_item",
                          CreateLang.itemName(behaviour.getFilter()).string())
                      .component()),
          mouseX,
          mouseY);
    }

    GuiGameElement.of(ModItems.COLONY_GAUGE.get().getDefaultInstance())
        .scale(4)
        .at(0, 0, -200)
        .render(graphics, x + 195, y + 55);
    if (!behaviour.getFilter().isEmpty()) {
      GuiGameElement.of(behaviour.getFilter())
          .scale(1.625)
          .at(0, 0, 100)
          .render(graphics, x + 214, y + 68);
    }

    // Expiration value text, drawn over the scroll box.
    int state = promiseExpiration.getState();
    graphics.drawString(
        font,
        CreateLang.text(state == -1 ? " /" : state == 0 ? "30s" : state + "m").component(),
        promiseExpiration.getX() + 3,
        promiseExpiration.getY() + 4,
        0xffeeeeee,
        true);

    // Promise indicator: how much of the current request is still in transit.
    ItemStack promiseStack = PackageStyles.getDefaultBox();
    int promiseX = x + 68;
    int promiseY = y + windowHeight - 24;
    graphics.renderItem(promiseStack, promiseX, promiseY);
    int promised = behaviour.getPromised();
    graphics.renderItemDecorations(font, promiseStack, promiseX, promiseY, promised + "");

    if (mouseX >= promiseX
        && mouseX < promiseX + 16
        && mouseY >= promiseY
        && mouseY < promiseY + 16) {
      if (promised == 0) {
        graphics.renderComponentTooltip(
            font,
            List.of(
                CreateLang.translate("gui.factory_panel.no_open_promises").component(),
                CreateLang.translate("gui.factory_panel.restocker_promises_tip").component(),
                CreateLang.translate("gui.factory_panel.restocker_promises_tip_1").component()),
            mouseX,
            mouseY);
      } else {
        graphics.renderComponentTooltip(
            font,
            List.of(
                CreateLang.translate("gui.factory_panel.restocker_promises_tip").component(),
                CreateLang.translate("gui.factory_panel.restocker_promises_tip_1").component()),
            mouseX,
            mouseY);
      }
    }
  }

  @Override
  public boolean mouseClicked(double mouseX, double mouseY, int button) {
    int promiseX = guiLeft + 68;
    int promiseY = guiTop + windowHeight - 24;
    if (mouseX >= promiseX
        && mouseX < promiseX + 16
        && mouseY >= promiseY
        && mouseY < promiseY + 16) {
      sendClearPromises = true;
      sendConfig();
      sendClearPromises = false;
      Minecraft.getInstance()
          .getSoundManager()
          .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f, 0.25f));
      return true;
    }
    return super.mouseClicked(mouseX, mouseY, button);
  }

  private void sendConfig() {
    if (com.thesettler_x_create.Config.DEBUG_LOGGING.getAsBoolean()) {
      com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
          "[ColonyGauge] sending config pos={} address='{}' promiseClearingInterval={} clearPromises={} reset={}",
          behaviour.getPanelPosition(),
          addressBox.getValue(),
          promiseExpiration.getState(),
          sendClearPromises,
          sendReset);
    }
    PacketDistributor.sendToServer(
        new ColonyGaugeConfigPacket(
            behaviour.getPanelPosition(),
            addressBox.getValue(),
            promiseExpiration.getState(),
            sendClearPromises,
            sendReset));
  }

  @Override
  public void removed() {
    super.removed();
    sendConfig();
  }
}
