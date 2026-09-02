package com.thesettler_x_create.block;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;

public class ColonyGaugeBlockItem extends BlockItem {

  public ColonyGaugeBlockItem(Block block, Properties properties) {
    super(block, properties);
  }

  @Override
  public InteractionResult place(BlockPlaceContext context) {
    ItemStack stack = context.getItemInHand();
    CompoundTag data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    if (!data.contains("GaugeColonyId")) {
      if (!context.getLevel().isClientSide() && context.getPlayer() != null) {
        context
            .getPlayer()
            .displayClientMessage(
                Component.literal(
                    "Right-click a Create Shop hut first to link the gauge to a colony."),
                true);
      }
      return InteractionResult.FAIL;
    }
    com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
        "[ColonyGaugeBlockItem] place() called, clickedPos={}, client={}",
        context.getClickedPos(), context.getLevel().isClientSide());
    InteractionResult result = super.place(context);
    com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
        "[ColonyGaugeBlockItem] place() result={}", result);
    return result;
  }
}
