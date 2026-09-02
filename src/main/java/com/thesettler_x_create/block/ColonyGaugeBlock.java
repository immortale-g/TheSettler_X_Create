package com.thesettler_x_create.block;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.foundation.block.IBE;
import com.thesettler_x_create.blockentity.ColonyGaugeBlockEntity;
import com.thesettler_x_create.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import org.jetbrains.annotations.Nullable;

public class ColonyGaugeBlock extends FaceAttachedHorizontalDirectionalBlock
    implements IBE<ColonyGaugeBlockEntity> {

  public static final MapCodec<ColonyGaugeBlock> CODEC = simpleCodec(ColonyGaugeBlock::new);
  public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

  public ColonyGaugeBlock(Properties properties) {
    super(properties);
    registerDefaultState(defaultBlockState()
        .setValue(FACE, AttachFace.WALL)
        .setValue(FACING, Direction.NORTH)
        .setValue(POWERED, false));
  }

  @Override
  protected void createBlockStateDefinition(
      StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
    super.createBlockStateDefinition(builder.add(FACE, FACING, POWERED));
  }

  /** Direction from the gauge toward the block it is attached to. */
  public static Direction connectedDirection(BlockState state) {
    return switch (state.getValue(FACE)) {
      case FLOOR -> Direction.DOWN;
      case CEILING -> Direction.UP;
      default -> state.getValue(FACING).getOpposite();
    };
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
  public boolean isSignalSource(BlockState state) {
    return true;
  }

  @Override
  public int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
    return state.getValue(POWERED) ? 15 : 0;
  }

  @Override
  public int getDirectSignal(
      BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
    return state.getValue(POWERED) && connectedDirection(state) == direction ? 15 : 0;
  }

  @Override
  protected MapCodec<? extends FaceAttachedHorizontalDirectionalBlock> codec() {
    return CODEC;
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
