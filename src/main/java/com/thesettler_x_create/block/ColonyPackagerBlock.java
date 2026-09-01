package com.thesettler_x_create.block;

import com.simibubi.create.content.logistics.packager.PackagerBlock;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.thesettler_x_create.init.ModBlockEntities;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

public class ColonyPackagerBlock extends PackagerBlock {

  public ColonyPackagerBlock(Properties properties) {
    super(properties);
  }

  @Override
  public BlockEntityType<? extends PackagerBlockEntity> getBlockEntityType() {
    return ModBlockEntities.COLONY_PACKAGER.get();
  }

  @Override
  protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
    builder.add(FACING, POWERED);
  }
}
