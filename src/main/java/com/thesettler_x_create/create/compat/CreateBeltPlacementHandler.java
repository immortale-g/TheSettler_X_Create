package com.thesettler_x_create.create.compat;

import com.ldtteam.structurize.api.RotationMirror;
import com.ldtteam.structurize.placement.IPlacementContext;
import com.ldtteam.structurize.placement.handlers.placement.IPlacementHandler;
import com.ldtteam.structurize.placement.handlers.placement.PlacementHandlers;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
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
import net.minecraft.resources.ResourceKey;
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
 * <p>The buffer is keyed by the belt run's {@code Controller} position from its blueprint NBT - but
 * that position is blueprint-local (Structurize has no notion of "this is a block position field"
 * for a foreign mod's custom tile data, so it never gets translated to world coordinates like the
 * block's own position does). Two builds of the same blueprint - a second colony on the same
 * server, or the same colony rebuilding after an abandoned/cancelled first attempt - produce an
 * identical {@code Controller} value, so keying on it alone lets one build's segments leak into
 * another's buffer. The key is therefore widened to include the dimension and the colony resolved
 * at each segment's own world position, and a buffer whose first segment arrived longer than {@link
 * Config#BELT_PLACEMENT_BUFFER_TTL_TICKS} ago is treated as abandoned and discarded before reuse,
 * so a stale entry from a cancelled build can't corrupt a later one that happens to land on the
 * same key.
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

  /**
   * Identifies one belt run's buffer. {@code colonyId} is {@code -1} when no colony can be resolved
   * at the segment's position (should not normally happen for a colony-builder placement, but a run
   * must never silently share a buffer with another run just because both fell back to the same
   * sentinel).
   */
  private record ControllerKey(
      ResourceKey<Level> dimension, int colonyId, BlockPos controllerPos) {}

  /**
   * Everything buffered for one in-progress belt run, keyed together so a future code path can't
   * update one piece of a run's state (items, segments, start time) while forgetting the others -
   * previously three separate maps that only stayed in sync because every write site happened to
   * touch all three by hand.
   */
  private static final class PendingBeltRun {
    final Map<BlockPos, List<ItemStack>> itemsBySegment = new HashMap<>();
    final TreeMap<BlockPos, BeltSegment> segments = new TreeMap<>();
    long startedAt = -1L;
  }

  private final Map<ControllerKey, PendingBeltRun> pendingRunsByController = new HashMap<>();

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
    return requiredItems(level, blockPos, blockState, tileEntityData);
  }

  // Structurize 1.0.807 and older. Not an override when compiling against the newer API, but it is
  // the method older Structurize versions call. Keep the signature exact.
  public List<ItemStack> getRequiredItems(
      Level level,
      BlockPos blockPos,
      BlockState blockState,
      @Nullable CompoundTag tileEntityData,
      boolean complete) {
    return requiredItems(level, blockPos, blockState, tileEntityData);
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
      Level level, BlockPos blockPos, BlockState blockState, @Nullable CompoundTag tileEntityData) {
    if (tileEntityData == null) {
      return List.of();
    }
    ControllerKey key = resolveControllerKey(level, blockPos, tileEntityData);
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

    PendingBeltRun run = markBufferTouched(key, level);
    run.itemsBySegment.put(blockPos, segmentItems);
    if (length <= 0 || run.itemsBySegment.size() < length) {
      return List.of();
    }
    List<ItemStack> combined = new ArrayList<>();
    run.itemsBySegment.values().forEach(combined::addAll);
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
    ControllerKey key = resolveControllerKey(level, blockPos, tileEntityData);
    int length = tileEntityData.getInt("Length");

    PendingBeltRun run = markBufferTouched(key, level);
    run.segments.put(blockPos, new BeltSegment(blockPos, blockState, tileEntityData));

    if (length <= 0 || run.segments.size() < length) {
      // Still waiting on the rest of this belt run; hold off placing anything for now.
      return ActionProcessingResult.SUCCESS;
    }

    List<BlockPos> placedSoFar = new ArrayList<>();
    for (BeltSegment segment : run.segments.values()) {
      if (!level.setBlock(segment.pos(), segment.state(), Block.UPDATE_ALL)) {
        placedSoFar.forEach(pos -> level.removeBlock(pos, false));
        clearBuffers(key);
        return ActionProcessingResult.DENY;
      }
      placedSoFar.add(segment.pos());
      if (segment.tileEntityData() != null) {
        PlacementHandlers.handleTileEntityPlacement(
            segment.tileEntityData(), level, segment.pos(), rotationMirror);
      }
    }
    clearBuffers(key);
    if (Config.DEBUG_LOGGING.getAsBoolean()) {
      TheSettlerXCreate.LOGGER.info(
          "[CreateCompat] placed belt run controller={} length={}", key.controllerPos(), length);
    }
    return ActionProcessingResult.SUCCESS;
  }

  private void clearBuffers(ControllerKey key) {
    pendingRunsByController.remove(key);
  }

  /**
   * Resolves the buffer key for a segment, folding in the dimension and the colony at the segment's
   * own world position so two unrelated belt runs (different colonies, or a stale abandoned run and
   * a fresh one) can never share a buffer just because their blueprint-local {@code Controller}
   * value happens to match.
   */
  private static ControllerKey resolveControllerKey(
      Level level, BlockPos segmentPos, CompoundTag tileEntityData) {
    BlockPos controllerPos = readControllerPos(tileEntityData);
    IColony colony = IColonyManager.getInstance().getColonyByPosFromWorld(level, segmentPos);
    int colonyId = colony != null ? colony.getID() : -1;
    return new ControllerKey(level.dimension(), colonyId, controllerPos);
  }

  /**
   * Discards this key's buffer if it was first touched longer than {@link
   * Config#BELT_PLACEMENT_BUFFER_TTL_TICKS} ago (an abandoned/cancelled build), then returns the
   * (possibly freshly-created) buffer for this key with its start time recorded if it doesn't have
   * one yet.
   */
  private PendingBeltRun markBufferTouched(ControllerKey key, Level level) {
    long now = level.getGameTime();
    PendingBeltRun run = pendingRunsByController.get(key);
    boolean stale =
        run != null
            && run.startedAt >= 0
            && now - run.startedAt > Config.BELT_PLACEMENT_BUFFER_TTL_TICKS.get();
    if (stale) {
      TheSettlerXCreate.LOGGER.warn(
          "[CreateCompat] discarding abandoned belt placement buffer controller={} colony={}"
              + " age={}",
          key.controllerPos(),
          key.colonyId(),
          now - run.startedAt);
      clearBuffers(key);
      run = null;
    }
    if (run == null) {
      run = new PendingBeltRun();
      pendingRunsByController.put(key, run);
    }
    if (run.startedAt < 0) {
      run.startedAt = now;
    }
    return run;
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
