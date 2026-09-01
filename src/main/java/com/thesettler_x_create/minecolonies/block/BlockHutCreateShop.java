package com.thesettler_x_create.minecolonies.block;

import com.minecolonies.api.blocks.AbstractBlockHut;
import com.minecolonies.api.colony.buildings.registry.BuildingEntry;
import com.minecolonies.core.network.messages.client.colony.ColonyViewBuildingViewMessage;
import com.thesettler_x_create.block.ColonyGaugeBlockItem;
import com.thesettler_x_create.item.StockLinkLinkerItem;
import com.thesettler_x_create.minecolonies.registry.ModMinecoloniesBuildings;
import com.thesettler_x_create.minecolonies.tileentity.TileEntityCreateShop;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class BlockHutCreateShop extends AbstractBlockHut<BlockHutCreateShop> {

  @Override
  public String getHutName() {
    return "blockhutcreateshop";
  }

  @Override
  public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
    return new TileEntityCreateShop(pos, state);
  }

  @Override
  public BuildingEntry getBuildingEntry() {
    return ModMinecoloniesBuildings.CREATE_SHOP.get();
  }

  @Override
  public ItemInteractionResult useItemOn(
      ItemStack stack,
      BlockState state,
      Level level,
      BlockPos pos,
      Player player,
      InteractionHand hand,
      BlockHitResult hit) {
    if (stack.getItem() instanceof ColonyGaugeBlockItem) {
      if (!level.isClientSide) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof TileEntityCreateShop shop && shop.getColony() != null) {
          CompoundTag tag =
              stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
          tag.putInt("GaugeColonyId", shop.getColony().getID());
          tag.putLong("GaugeShopPos", pos.asLong());
          tag.putString("GaugeDimension", level.dimension().location().toString());
          stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
          player.displayClientMessage(
              Component.literal("Colony Gauge linked to shop. Now place it near a Frogport."),
              true);
        } else {
          player.displayClientMessage(
              Component.literal("No active colony shop found at this position."), true);
        }
      }
      return ItemInteractionResult.SUCCESS;
    }

    if (stack.getItem() instanceof StockLinkLinkerItem) {
      if (player.isShiftKeyDown()) {
        if (!level.isClientSide) {
          stack.remove(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
          player.displayClientMessage(Component.literal("Stock-Link Linker zurückgesetzt."), true);
        }
        return ItemInteractionResult.SUCCESS;
      }
      if (!level.isClientSide) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof TileEntityCreateShop shop) {
          UUID stored = StockLinkLinkerItem.getStoredNetworkId(stack);
          if (stored == null) {
            player.displayClientMessage(
                Component.literal("Kein Netzwerk im Linker gespeichert."), true);
          } else {
            shop.setStockNetworkId(stored);
            level.sendBlockUpdated(pos, state, state, 3);
            player.displayClientMessage(
                Component.literal("Shop mit Stock-Netzwerk verbunden."), true);
          }
        }
      }
      return ItemInteractionResult.SUCCESS;
    }

    ItemInteractionResult result = super.useItemOn(stack, state, level, pos, player, hand, hit);
    if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
      BlockEntity be = level.getBlockEntity(pos);
      if (be instanceof TileEntityCreateShop shop && shop.getBuilding() != null) {
        new ColonyViewBuildingViewMessage(shop.getBuilding(), true).sendToPlayer(serverPlayer);
      }
    }
    return result;
  }
}
