package com.thesettler_x_create.blockentity;

import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.thesettler_x_create.block.ColonyGaugeBlock;
import com.thesettler_x_create.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

public class ColonyPackagerBlockEntity extends PackagerBlockEntity {

  public ColonyPackagerBlockEntity(BlockPos pos, BlockState state) {
    super(ModBlockEntities.COLONY_PACKAGER.get(), pos, state);
  }

  @Override
  public boolean unwrapBox(ItemStack box, boolean simulate) {
    boolean result = super.unwrapBox(box, simulate);
    if (result && !simulate) {
      for (Direction d : Direction.values()) {
        BlockPos neighbor = getBlockPos().relative(d);
        if (level != null
            && level.getBlockEntity(neighbor) instanceof ColonyGaugeBlockEntity gauge
            && ColonyGaugeBlock.connectedDirection(gauge.getBlockState()) == d.getOpposite()) {
          gauge.onDeliveryReceived();
        }
      }
    }
    return result;
  }
}
