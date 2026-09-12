package com.thesettler_x_create.create.compat;

import com.ldtteam.structurize.api.RotationMirror;
import com.ldtteam.structurize.placement.IPlacementContext;
import com.ldtteam.structurize.placement.handlers.placement.IPlacementHandler;
import com.ldtteam.structurize.placement.handlers.placement.PlacementHandlers;
import com.simibubi.create.content.kinetics.belt.BeltBlock;
import com.simibubi.create.content.kinetics.belt.BeltPart;
import com.thesettler_x_create.Config;
import com.thesettler_x_create.TheSettlerXCreate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Tuple;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Structurize placement handler for Create's Belt. A belt is not a single independent block — each
 * segment's saved NBT references a shared {@code Controller} position and the run's total {@code
 * Length}, and Create's own kinetic-network validation removes a belt segment that briefly exists
 * without the rest of its run (which is exactly what happens if the colony builder places one
 * segment at a time, the way it does for every other block). This handler buffers every segment
 * belonging to the same controller as the builder places them, and only commits the run to the
 * world — as one atomic sequence of block+NBT placements — once every segment has arrived.
 *
 * <p>Required-item reporting for a fresh Structurize blueprint's own belt (cargo items already
 * riding the belt at scan time aren't accounted for — that only matters for a belt captured live
 * from the world, not a hand-authored building blueprint) also needs a full run before it can
 * report an accurate total, for the same reason.
 *
 * <p>Structurize 1.0.808 replaced the placement signatures with {@link IPlacementContext} variants.
 * This handler implements both sets, so one jar works with MineColonies before and after that
 * change; each Structurize version only ever calls the set its own interface declares.
 */
public class CreateBeltPlacementHandler implements IPlacementHandler {
  private static final ResourceLocation BELT_ID =
      ResourceLocation.fromNamespaceAndPath("create", "belt");
  private static final ResourceLocation SHAFT_ID =
      ResourceLocation.fromNamespaceAndPath("create", "shaft");
  private static final ResourceLocation BELT_CONNECTOR_ID =
      ResourceLocation.fromNamespaceAndPath("create", "belt_connector");

  private record BeltSegment(
      BlockPos pos, BlockState state, @Nullable CompoundTag tileEntityData) {}

  private final Map<BlockPos, Map<BlockPos, List<ItemStack>>> pendingItemsByController =
      new HashMap<>();
  private final Map<BlockPos, TreeMap<BlockPos, BeltSegment>> pendingSegmentsByController =
      new HashMap<>();

  @Override
  public boolean canHandle(Level level, BlockPos blockPos, BlockState blockState) {
    return BELT_ID.equals(BuiltInRegistries.BLOCK.getKey(blockState.getBlock()));
  }

  // Structurize 1.0.808 and newer.
  @Override
  public List<ItemStack> getRequiredItems(
      Level level,
      BlockPos blockPos,
      BlockState blockState,
      @Nullable CompoundTag tileEntityData,
      IPlacementContext placementContext) {
    return requiredItems(blockPos, blockState, tileEntityData);
  }

  // Structurize 1.0.807 and older. Not an override when compiling against the newer API, but it is
  // the method older Structurize versions call. Keep the signature exact.
  public List<ItemStack> getRequiredItems(
      Level level,
      BlockPos blockPos,
      BlockState blockState,
      @Nullable CompoundTag tileEntityData,
      boolean complete) {
    return requiredItems(blockPos, blockState, tileEntityData);
  }

  // Structurize 1.0.808 and newer. Same comparison as Structurize's own general block handler.
  @Override
  public boolean doesWorldStateMatchBlueprintState(
      BlockState worldState,
      BlockState blueprintState,
      @Nullable Tuple<BlockEntity, CompoundTag> blockEntityData,
      IPlacementContext placementContext) {
    return worldState.equals(blueprintState);
  }

  private List<ItemStack> requiredItems(
      BlockPos blockPos, BlockState blockState, @Nullable CompoundTag tileEntityData) {
    if (tileEntityData == null) {
      return List.of();
    }
    BlockPos controllerPos = readControllerPos(tileEntityData);
    int length = tileEntityData.getInt("Length");

    List<ItemStack> segmentItems = new ArrayList<>();
    if (blockState.hasProperty(BeltBlock.PART)) {
      BeltPart part = blockState.getValue(BeltBlock.PART);
      if (part != BeltPart.MIDDLE) {
        addStack(segmentItems, SHAFT_ID);
      }
      if (part == BeltPart.START) {
        addStack(segmentItems, BELT_CONNECTOR_ID);
      }
    }

    Map<BlockPos, List<ItemStack>> knownSegments =
        pendingItemsByController.computeIfAbsent(controllerPos, ignored -> new HashMap<>());
    knownSegments.put(blockPos, segmentItems);
    if (length <= 0 || knownSegments.size() < length) {
      return List.of();
    }
    List<ItemStack> combined = new ArrayList<>();
    knownSegments.values().forEach(combined::addAll);
    return combined;
  }

  // Structurize 1.0.808 and newer.
  @Override
  public ActionProcessingResult handle(
      Level level,
      BlockPos blockPos,
      BlockState blockState,
      @Nullable CompoundTag tileEntityData,
      IPlacementContext placementContext) {
    return place(level, blockPos, blockState, tileEntityData, placementContext.getRotationMirror());
  }

  // Structurize 1.0.807 and older, see getRequiredItems.
  public ActionProcessingResult handle(
      Level level,
      BlockPos blockPos,
      BlockState blockState,
      @Nullable CompoundTag tileEntityData,
      boolean complete,
      BlockPos centerPos,
      RotationMirror rotationMirror) {
    return place(level, blockPos, blockState, tileEntityData, rotationMirror);
  }

  private ActionProcessingResult place(
      Level level,
      BlockPos blockPos,
      BlockState blockState,
      @Nullable CompoundTag tileEntityData,
      RotationMirror rotationMirror) {
    if (tileEntityData == null) {
      return ActionProcessingResult.DENY;
    }
    BlockPos controllerPos = readControllerPos(tileEntityData);
    int length = tileEntityData.getInt("Length");

    TreeMap<BlockPos, BeltSegment> segments =
        pendingSegmentsByController.computeIfAbsent(controllerPos, ignored -> new TreeMap<>());
    segments.put(blockPos, new BeltSegment(blockPos, blockState, tileEntityData));

    if (length <= 0 || segments.size() < length) {
      // Still waiting on the rest of this belt run; hold off placing anything for now.
      return ActionProcessingResult.SUCCESS;
    }

    List<BlockPos> placedSoFar = new ArrayList<>();
    for (BeltSegment segment : segments.values()) {
      if (!level.setBlock(segment.pos(), segment.state(), Block.UPDATE_ALL)) {
        placedSoFar.forEach(pos -> level.removeBlock(pos, false));
        clearBuffers(controllerPos);
        return ActionProcessingResult.DENY;
      }
      placedSoFar.add(segment.pos());
      if (segment.tileEntityData() != null) {
        PlacementHandlers.handleTileEntityPlacement(
            segment.tileEntityData(), level, segment.pos(), rotationMirror);
      }
    }
    clearBuffers(controllerPos);
    if (Config.DEBUG_LOGGING.getAsBoolean()) {
      TheSettlerXCreate.LOGGER.info(
          "[CreateCompat] placed belt run controller={} length={}", controllerPos, length);
    }
    return ActionProcessingResult.SUCCESS;
  }

  private void clearBuffers(BlockPos controllerPos) {
    pendingSegmentsByController.remove(controllerPos);
    pendingItemsByController.remove(controllerPos);
  }

  private static void addStack(List<ItemStack> items, ResourceLocation itemId) {
    Item item = BuiltInRegistries.ITEM.get(itemId);
    if (item != null && !item.equals(net.minecraft.world.item.Items.AIR)) {
      items.add(new ItemStack(item));
    }
  }

  private static BlockPos readControllerPos(CompoundTag tileEntityData) {
    int[] controller = tileEntityData.getIntArray("Controller");
    return new BlockPos(controller[0], controller[1], controller[2]);
  }
}
