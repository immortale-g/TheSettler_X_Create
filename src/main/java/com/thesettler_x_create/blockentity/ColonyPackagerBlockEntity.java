package com.thesettler_x_create.blockentity;

import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlock;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.simibubi.create.content.logistics.packager.PackagingRequest;
import com.thesettler_x_create.Config;
import com.thesettler_x_create.TheSettlerXCreate;
import com.thesettler_x_create.init.ModBlockEntities;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;

public class ColonyPackagerBlockEntity extends PackagerBlockEntity {

  public ColonyPackagerBlockEntity(BlockPos pos, BlockState state) {
    super(ModBlockEntities.COLONY_PACKAGER.get(), pos, state);
  }

  @Override
  public boolean unwrapBox(ItemStack box, boolean simulate) {
    // Captured before super.unwrapBox() runs, since unpacking may consume/modify the box's
    // contents — and used below to tell the Gauge WHICH item arrived, so a gauge block with
    // several active panels waiting on different items attributes the delivery correctly
    // instead of always crediting whichever panel happens to be first in iteration order.
    ItemStack deliveredItem = firstNonEmpty(PackageItem.getContents(box));

    if (Config.DEBUG_LOGGING.getAsBoolean() && !simulate && level != null) {
      Direction facing =
          getBlockState().getOptionalValue(DirectionalBlock.FACING).orElse(Direction.UP);
      BlockPos target = getBlockPos().relative(facing.getOpposite());
      BlockState targetState = level.getBlockState(target);
      var targetBe = level.getBlockEntity(target);
      var cap =
          targetBe == null
              ? null
              : level.getCapability(
                  Capabilities.ItemHandler.BLOCK, target, targetState, targetBe, facing);
      TheSettlerXCreate.LOGGER.info(
          "[ColonyPackager] unwrapBox pos={} facing={} target={} targetBlock={} targetBe={} capabilityFound={} deliveredItem={}",
          getBlockPos(),
          facing,
          target,
          targetState.getBlock(),
          targetBe == null ? "<null>" : targetBe.getClass().getSimpleName(),
          cap != null,
          deliveredItem);
    }
    boolean result = super.unwrapBox(box, simulate);
    if (Config.DEBUG_LOGGING.getAsBoolean() && !simulate) {
      TheSettlerXCreate.LOGGER.info(
          "[ColonyPackager] unwrapBox pos={} result={} box={}",
          getBlockPos(),
          result,
          box.getItem());
    }
    if (result && !simulate) {
      for (Direction d : Direction.values()) {
        BlockPos neighbor = getBlockPos().relative(d);
        if (level != null
            && level.getBlockEntity(neighbor) instanceof ColonyGaugeBlockEntity gauge
            && FactoryPanelBlock.connectedDirection(gauge.getBlockState()) == d) {
          gauge.onDeliveryReceived(deliveredItem);
        }
      }
    }
    return result;
  }

  private static ItemStack firstNonEmpty(net.neoforged.neoforge.items.ItemStackHandler handler) {
    for (int i = 0; i < handler.getSlots(); i++) {
      ItemStack stack = handler.getStackInSlot(i);
      if (!stack.isEmpty()) return stack;
    }
    return ItemStack.EMPTY;
  }

  /**
   * The Gauge-side packager only ever receives/unwraps boxes (via {@link #unwrapBox}) — it never
   * builds or ships packages of its own. Without this override, the adjacent ColonyGauge's own
   * redstone output (emitted toward this block once satisfied, mirroring Create's FactoryPanelBlock
   * — see ColonyGaugeBlock#getDirectSignal) would make Create's stock "redstone mode" packing logic
   * continuously vacuum the connected chest into new boxes.
   */
  @Override
  public void attemptToSend(List<PackagingRequest> queuedRequests) {
    if (Config.DEBUG_LOGGING.getAsBoolean() && level != null && !level.isClientSide()) {
      TheSettlerXCreate.LOGGER.info(
          "[ColonyPackager] attemptToSend suppressed pos={} (this packager never sends)",
          getBlockPos());
    }
  }
}
