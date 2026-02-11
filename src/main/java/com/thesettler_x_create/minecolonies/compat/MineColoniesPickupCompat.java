package com.thesettler_x_create.minecolonies.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;

public final class MineColoniesPickupCompat {
    private MineColoniesPickupCompat() {
    }

    public static boolean isValidPickupSource(Level level, BlockPos pos) {
        if (level == null || pos == null) {
            return false;
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) {
            return false;
        }
        BlockState state = be.getBlockState();
        return Capabilities.ItemHandler.BLOCK.getCapability(level, pos, state, be, Direction.UP) != null
                || Capabilities.ItemHandler.BLOCK.getCapability(level, pos, state, be, null) != null;
    }
}
