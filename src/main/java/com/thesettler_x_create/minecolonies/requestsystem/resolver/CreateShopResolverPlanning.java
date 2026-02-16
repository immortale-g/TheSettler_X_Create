package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import com.google.common.collect.Lists;
import com.minecolonies.api.colony.requestsystem.requestable.IDeliverable;
import com.minecolonies.api.colony.requestsystem.requestable.Tool;
import com.minecolonies.api.equipment.registry.EquipmentTypeEntry;
import com.minecolonies.api.tileentities.AbstractTileEntityRack;
import com.minecolonies.api.util.InventoryUtils;
import com.minecolonies.api.util.WorldUtil;
import com.thesettler_x_create.Config;
import com.thesettler_x_create.TheSettlerXCreate;
import com.thesettler_x_create.blockentity.CreateShopBlockEntity;
import com.thesettler_x_create.minecolonies.building.BuildingCreateShop;
import com.thesettler_x_create.minecolonies.tileentity.TileEntityCreateShop;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.IItemHandler;

/** Helper for computing availability and delivery plans for Create Shop requests. */
final class CreateShopResolverPlanning {
  int getAvailableFromRacks(TileEntityCreateShop tile, IDeliverable deliverable) {
    Level level = tile.getLevel();
    if (level == null) {
      return 0;
    }
    if (tile.getBuilding() instanceof BuildingCreateShop shop) {
      shop.ensureRackContainers();
    }
    int total = 0;
    for (BlockPos pos : tile.getBuilding().getContainers()) {
      if (!WorldUtil.isBlockLoaded(level, pos)) {
        continue;
      }
      BlockEntity entity = level.getBlockEntity(pos);
      if (!(entity instanceof AbstractTileEntityRack rack)) {
        continue;
      }
      total += rack.getItemCount(deliverable::matches);
    }
    if (total > 0) {
      return Math.max(0, total);
    }
    // Fallback: direct scan around the shop for unregistered racks.
    total = scanRacksAroundShop(tile, deliverable, null);
    return Math.max(0, total);
  }

  int getAvailableFromPickup(CreateShopBlockEntity pickup, IDeliverable deliverable) {
    if (pickup == null) {
      return 0;
    }
    IItemHandler handler = pickup.getItemHandler(null);
    if (handler == null) {
      return 0;
    }
    int total = 0;
    for (int i = 0; i < handler.getSlots(); i++) {
      ItemStack stack = handler.getStackInSlot(i);
      if (stack.isEmpty()) {
        continue;
      }
      if (deliverable.matches(stack)) {
        total += stack.getCount();
      }
    }
    return Math.max(0, total);
  }

  List<com.minecolonies.api.util.Tuple<ItemStack, BlockPos>> planFromRacksWithPositions(
      TileEntityCreateShop tile, IDeliverable deliverable, int amount) {
    if (amount <= 0) {
      return Lists.newArrayList();
    }
    Level level = tile.getLevel();
    if (level == null) {
      return Lists.newArrayList();
    }
    if (tile.getBuilding() instanceof BuildingCreateShop shop) {
      shop.ensureRackContainers();
    }
    int remaining = amount;
    List<com.minecolonies.api.util.Tuple<ItemStack, BlockPos>> planned = Lists.newArrayList();
    List<com.minecolonies.api.util.Tuple<ItemStack, BlockPos>> matches =
        tile.getMatchingItemStacksInWarehouse(deliverable::matches);
    if (deliverable instanceof Tool tool) {
      matches.sort(
          Comparator.comparingInt(
                  (com.minecolonies.api.util.Tuple<ItemStack, BlockPos> entry) ->
                      getToolLevel(tool, entry.getA()))
              .reversed());
    }
    for (var tuple : matches) {
      if (remaining <= 0) {
        break;
      }
      ItemStack stack = tuple.getA();
      if (stack.isEmpty()) {
        continue;
      }
      int toTake = Math.min(remaining, stack.getCount());
      ItemStack copy = stack.copy();
      copy.setCount(toTake);
      planned.add(new com.minecolonies.api.util.Tuple<>(copy, tuple.getB()));
      remaining -= toTake;
    }
    if (planned.isEmpty()) {
      scanRacksAroundShop(tile, deliverable, planned);
      if (!planned.isEmpty()) {
        // Trim to requested amount.
        int plannedCount = countPlanned(planned);
        if (plannedCount > amount) {
          List<com.minecolonies.api.util.Tuple<ItemStack, BlockPos>> trimmed = Lists.newArrayList();
          int needed = amount;
          for (var entry : planned) {
            if (needed <= 0) {
              break;
            }
            ItemStack stack = entry.getA();
            if (stack.isEmpty()) {
              continue;
            }
            int take = Math.min(needed, stack.getCount());
            ItemStack copy = stack.copy();
            copy.setCount(take);
            trimmed.add(new com.minecolonies.api.util.Tuple<>(copy, entry.getB()));
            needed -= take;
          }
          planned = trimmed;
        }
      }
    }
    return planned;
  }

  int scanRacksAroundShop(
      TileEntityCreateShop tile,
      IDeliverable deliverable,
      List<com.minecolonies.api.util.Tuple<ItemStack, BlockPos>> planned) {
    BuildingCreateShop shop = tile.getBuilding() instanceof BuildingCreateShop b ? b : null;
    if (shop == null) {
      return 0;
    }
    Level level = tile.getLevel();
    if (level == null) {
      return 0;
    }
    BlockPos origin = shop.getLocation().getInDimensionLocation();
    int radius = 16;
    int minX = origin.getX() - radius;
    int maxX = origin.getX() + radius;
    int minY = origin.getY() - 6;
    int maxY = origin.getY() + 6;
    int minZ = origin.getZ() - radius;
    int maxZ = origin.getZ() + radius;
    int total = 0;
    for (int x = minX; x <= maxX; x++) {
      for (int y = minY; y <= maxY; y++) {
        for (int z = minZ; z <= maxZ; z++) {
          BlockPos pos = new BlockPos(x, y, z);
          if (!WorldUtil.isBlockLoaded(level, pos)) {
            continue;
          }
          BlockEntity entity = level.getBlockEntity(pos);
          if (!(entity instanceof AbstractTileEntityRack rack)) {
            continue;
          }
          int count = rack.getItemCount(deliverable::matches);
          if (count <= 0) {
            continue;
          }
          total += count;
          if (planned != null) {
            for (ItemStack stack :
                InventoryUtils.filterItemHandler(rack.getInventory(), deliverable::matches)) {
              planned.add(new com.minecolonies.api.util.Tuple<>(stack.copy(), pos));
            }
          }
        }
      }
    }
    if (Config.DEBUG_LOGGING.getAsBoolean() && total > 0) {
      TheSettlerXCreate.LOGGER.info(
          "[CreateShop] fallback rack scan found {} matching items near {}", total, origin);
    }
    return total;
  }

  List<com.minecolonies.api.util.Tuple<ItemStack, BlockPos>> planFromPickupWithPositions(
      CreateShopBlockEntity pickup, IDeliverable deliverable, int amount) {
    if (amount <= 0 || pickup == null) {
      return Lists.newArrayList();
    }
    IItemHandler handler = pickup.getItemHandler(null);
    if (handler == null) {
      return Lists.newArrayList();
    }
    int remaining = amount;
    List<com.minecolonies.api.util.Tuple<ItemStack, BlockPos>> planned = Lists.newArrayList();
    BlockPos pickupPos = pickup.getBlockPos();
    for (int i = 0; i < handler.getSlots(); i++) {
      if (remaining <= 0) {
        break;
      }
      ItemStack stack = handler.getStackInSlot(i);
      if (stack.isEmpty()) {
        continue;
      }
      if (!deliverable.matches(stack)) {
        continue;
      }
      int toTake = Math.min(remaining, stack.getCount());
      ItemStack copy = stack.copy();
      copy.setCount(toTake);
      planned.add(new com.minecolonies.api.util.Tuple<>(copy, pickupPos));
      remaining -= toTake;
    }
    return planned;
  }

  int countPlanned(List<com.minecolonies.api.util.Tuple<ItemStack, BlockPos>> planned) {
    if (planned == null || planned.isEmpty()) {
      return 0;
    }
    int total = 0;
    for (var entry : planned) {
      if (entry == null) {
        continue;
      }
      ItemStack stack = entry.getA();
      if (!stack.isEmpty()) {
        total += stack.getCount();
      }
    }
    return total;
  }

  List<ItemStack> extractStacks(List<com.minecolonies.api.util.Tuple<ItemStack, BlockPos>> stacks) {
    List<ItemStack> result = Lists.newArrayList();
    if (stacks == null) {
      return result;
    }
    for (var entry : stacks) {
      if (entry == null) {
        continue;
      }
      ItemStack stack = entry.getA();
      if (!stack.isEmpty()) {
        result.add(stack.copy());
      }
    }
    return result;
  }

  private int getToolLevel(Tool tool, ItemStack stack) {
    if (tool == null || stack.isEmpty()) {
      return 0;
    }
    EquipmentTypeEntry type = tool.getEquipmentType();
    return type.getMiningLevel(stack);
  }
}
