package com.thesettler_x_create.create.compat;

import com.ldtteam.structurize.placement.handlers.placement.PlacementHandlers;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Structurize placement handler for Create block states that exist only as the result of placing
 * one block next to another (e.g. a Shaft encased by an Andesite Casing) rather than being
 * placeable from an item of their own. Structurize's default resolution assumes the required item
 * for an unhandled block state is the block's own item, which doesn't exist for these composite
 * states — the colony builder would request an unobtainable item and stall forever. This handler
 * maps each composite block id to the real item that actually builds it; placement mechanics (a
 * plain block+NBT set) are otherwise unaffected, so placement itself is delegated to Structurize's
 * own {@link PlacementHandlers.GeneralBlockPlacementHandler}.
 */
public class CompositeBlockItemRemapHandler extends PlacementHandlers.GeneralBlockPlacementHandler {
  private final Map<ResourceLocation, ResourceLocation> blockIdToRequiredItemId;

  public CompositeBlockItemRemapHandler(
      Map<ResourceLocation, ResourceLocation> blockIdToRequiredItemId) {
    this.blockIdToRequiredItemId = Map.copyOf(blockIdToRequiredItemId);
  }

  @Override
  public boolean canHandle(Level level, BlockPos blockPos, BlockState blockState) {
    return blockIdToRequiredItemId.containsKey(
        BuiltInRegistries.BLOCK.getKey(blockState.getBlock()));
  }

  @Override
  public List<ItemStack> getRequiredItems(
      Level level,
      BlockPos blockPos,
      BlockState blockState,
      @Nullable CompoundTag tileEntityData,
      boolean complete) {
    ResourceLocation itemId =
        blockIdToRequiredItemId.get(BuiltInRegistries.BLOCK.getKey(blockState.getBlock()));
    if (itemId == null) {
      return List.of();
    }
    Item item = BuiltInRegistries.ITEM.get(itemId);
    return item == null || item.equals(net.minecraft.world.item.Items.AIR)
        ? List.of()
        : List.of(new ItemStack(item));
  }
}
