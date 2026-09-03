package com.thesettler_x_create.menu;

import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelPosition;
import com.simibubi.create.foundation.gui.menu.GhostItemMenu;
import com.simibubi.create.foundation.utility.CreateLang;
import com.thesettler_x_create.blockentity.ColonyGaugeBehaviour;
import com.thesettler_x_create.blockentity.ColonyGaugeBlockEntity;
import com.thesettler_x_create.init.ModMenus;
import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

public class ColonyGaugeSetItemMenu extends GhostItemMenu<ColonyGaugeBehaviour> {

  public ColonyGaugeSetItemMenu(int id, Inventory inv, RegistryFriendlyByteBuf extraData) {
    this(ModMenus.COLONY_GAUGE_SET_ITEM.get(), id, inv, extraData);
  }

  private ColonyGaugeSetItemMenu(
      MenuType<?> type, int id, Inventory inv, RegistryFriendlyByteBuf extraData) {
    super(type, id, inv, extraData);
  }

  private ColonyGaugeSetItemMenu(
      MenuType<?> type, int id, Inventory inv, ColonyGaugeBehaviour contentHolder) {
    super(type, id, inv, contentHolder);
  }

  public static ColonyGaugeSetItemMenu create(int id, Inventory inv, ColonyGaugeBehaviour be) {
    return new ColonyGaugeSetItemMenu(ModMenus.COLONY_GAUGE_SET_ITEM.get(), id, inv, be);
  }

  @Override
  protected ItemStackHandler createGhostInventory() {
    return new ItemStackHandler(1);
  }

  @Override
  protected boolean allowRepeats() {
    return true;
  }

  @Override
  @OnlyIn(Dist.CLIENT)
  protected ColonyGaugeBehaviour createOnClient(RegistryFriendlyByteBuf extraData) {
    FactoryPanelPosition pos = FactoryPanelPosition.STREAM_CODEC.decode(extraData);
    ColonyGaugeBlockEntity be =
        (ColonyGaugeBlockEntity) Minecraft.getInstance().level.getBlockEntity(pos.pos());
    return be.panels.get(pos.slot());
  }

  @Override
  protected void addSlots() {
    int playerX = 13;
    int playerY = 112;
    int slotX = 74;
    int slotY = 28;

    addPlayerSlots(playerX, playerY);
    addSlot(new SlotItemHandler(ghostInventory, 0, slotX, slotY));
  }

  @Override
  protected void saveData(ColonyGaugeBehaviour contentHolder) {
    if (!contentHolder.setFilter(ghostInventory.getStackInSlot(0))) {
      player.displayClientMessage(
          CreateLang.translateDirect("logistics.filter.invalid_item"), true);
      AllSoundEvents.DENY.playOnServer(player.level(), player.blockPosition(), 1, 1);
      return;
    }
    player
        .level()
        .playSound(
            null,
            contentHolder.getPos(),
            SoundEvents.ITEM_FRAME_ADD_ITEM,
            SoundSource.BLOCKS,
            .25f,
            .1f);
  }
}
