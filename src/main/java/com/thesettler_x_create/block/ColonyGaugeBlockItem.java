package com.thesettler_x_create.block;

import com.thesettler_x_create.ItemStackDataUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;

public class ColonyGaugeBlockItem extends BlockItem {

  public ColonyGaugeBlockItem(Block block, Properties properties) {
    super(block, properties);
  }

  @Override
  public InteractionResult place(BlockPlaceContext context) {
    ItemStack stack = context.getItemInHand();
    CompoundTag data = ItemStackDataUtil.copyCustomData(stack);
    if (!GaugeLinkData.isLinked(data)) {
      if (!context.getLevel().isClientSide() && context.getPlayer() != null) {
        context
            .getPlayer()
            .displayClientMessage(
                Component.translatable(
                    "com.thesettler_x_create.message.colony_gauge.link_shop_first"),
                true);
      }
      return InteractionResult.FAIL;
    }
    return super.place(context);
  }
}
