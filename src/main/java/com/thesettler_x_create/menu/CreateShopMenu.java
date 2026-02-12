package com.thesettler_x_create.menu;

import com.thesettler_x_create.init.ModMenus;
import com.thesettler_x_create.minecolonies.tileentity.TileEntityCreateShop;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class CreateShopMenu extends AbstractContainerMenu {
  private final TileEntityCreateShop shop;
  private final Level level;
  private final BlockPos pos;

  public CreateShopMenu(int id, Inventory playerInventory, RegistryFriendlyByteBuf buf) {
    this(id, playerInventory, playerInventory.player.level(), buf.readBlockPos());
  }

  public CreateShopMenu(int id, Inventory playerInventory, TileEntityCreateShop shop) {
    this(id, playerInventory, shop.getLevel(), shop.getBlockPos());
  }

  private CreateShopMenu(int id, Inventory playerInventory, Level level, BlockPos pos) {
    super(ModMenus.CREATE_SHOP.get(), id);
    this.level = level;
    this.pos = pos;
    this.shop = level.getBlockEntity(pos) instanceof TileEntityCreateShop te ? te : null;
  }

  public BlockPos getPos() {
    return pos;
  }

  public String getShopAddress() {
    return shop == null ? "" : shop.getShopAddress();
  }

  @Override
  public boolean stillValid(Player player) {
    return shop != null
        && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) < 64.0;
  }

  @Override
  public ItemStack quickMoveStack(Player player, int index) {
    return ItemStack.EMPTY;
  }
}
