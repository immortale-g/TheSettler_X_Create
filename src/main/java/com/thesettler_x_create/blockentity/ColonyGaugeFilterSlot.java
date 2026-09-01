package com.thesettler_x_create.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class ColonyGaugeFilterSlot extends ValueBoxTransform {

  @Override
  public Vec3 getLocalOffset(LevelAccessor level, BlockPos pos, BlockState state) {
    return VecHelper.voxelSpace(8, 17, 8);
  }

  @Override
  public void rotate(LevelAccessor level, BlockPos pos, BlockState state, PoseStack ms) {
    ms.mulPose(Axis.XP.rotationDegrees(90));
  }
}
