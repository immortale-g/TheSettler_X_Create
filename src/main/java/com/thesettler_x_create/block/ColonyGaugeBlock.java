package com.thesettler_x_create.block;

import com.simibubi.create.foundation.block.IBE;
import com.thesettler_x_create.blockentity.ColonyGaugeBlockEntity;
import com.thesettler_x_create.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class ColonyGaugeBlock extends net.minecraft.world.level.block.Block
    implements IBE<ColonyGaugeBlockEntity> {

  public ColonyGaugeBlock(Properties properties) {
    super(properties);
  }

  @Override
  public void setPlacedBy(
      Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
    super.setPlacedBy(level, pos, state, placer, stack);
    if (level.isClientSide()) return;
    CompoundTag data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    if (data.contains("GaugeColonyId")
        && level.getBlockEntity(pos) instanceof ColonyGaugeBlockEntity gauge) {
      gauge.setShopLink(
          data.getInt("GaugeColonyId"),
          BlockPos.of(data.getLong("GaugeShopPos")),
          data.getString("GaugeDimension"));
    }
  }

  @Override
  public void onRemove(
      BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
    IBE.onRemove(state, level, pos, newState);
  }

  @Override
  public Class<ColonyGaugeBlockEntity> getBlockEntityClass() {
    return ColonyGaugeBlockEntity.class;
  }

  @Override
  public BlockEntityType<ColonyGaugeBlockEntity> getBlockEntityType() {
    return ModBlockEntities.COLONY_GAUGE.get();
  }
}
