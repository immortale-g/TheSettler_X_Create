package com.thesettler_x_create.create;

import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DirectionalBlock;
import org.jetbrains.annotations.Nullable;

/**
 * Single chokepoint for the one thing the shop needs to know about Create's packager block: which
 * inventory an arriving package is unpacked into.
 *
 * <p>A packager unpacks into exactly one block, the one its facing points away from ({@code
 * PackagerBlockEntity.unwrapBox}). For a shop that makes one of its racks the inbound bottleneck:
 * once that rack is full, nothing else arrives from the network no matter how much room the other
 * racks have. Knowing which rack it is, is what lets the shopkeeper keep it clear.
 *
 * <p>Held apart from {@link CreatePackageBridge}, which is about the package item, so a future
 * compatibility shim for a Create addon that moves the packager's unpack target has one place to
 * patch.
 */
public final class CreatePackagerBridge {
  private CreatePackagerBridge() {}

  /** Whether a packager beside {@code pos} unpacks its arriving packages into the block there. */
  public static boolean isPackagerUnpackingInto(@Nullable Level level, @Nullable BlockPos pos) {
    if (level == null || pos == null) {
      return false;
    }
    for (Direction side : Direction.values()) {
      BlockPos neighbor = pos.relative(side);
      if (!level.isLoaded(neighbor)) {
        continue;
      }
      if (!(level.getBlockEntity(neighbor) instanceof PackagerBlockEntity packager)) {
        continue;
      }
      if (unpackTargetOf(packager).equals(pos)) {
        return true;
      }
    }
    return false;
  }

  /** The block a packager unpacks into: the one its facing points away from. */
  private static BlockPos unpackTargetOf(PackagerBlockEntity packager) {
    Direction facing =
        packager.getBlockState().getOptionalValue(DirectionalBlock.FACING).orElse(Direction.UP);
    return packager.getBlockPos().relative(facing.getOpposite());
  }
}
