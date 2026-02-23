package com.thesettler_x_create.minecolonies.building;

import com.minecolonies.api.blocks.AbstractBlockMinecoloniesRack;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.util.WorldUtil;
import com.minecolonies.core.tileentities.TileEntityRack;
import com.simibubi.create.content.logistics.BigItemStack;
import com.thesettler_x_create.Config;
import com.thesettler_x_create.TheSettlerXCreate;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Tuple;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;

/** Maintains rack container discovery and inventory counting for the Create Shop. */
final class ShopRackIndex {
  private final BuildingCreateShop shop;
  private long lastRackScanTick = -1L;

  ShopRackIndex(BuildingCreateShop shop) {
    this.shop = shop;
  }

  void ensureRackContainers() {
    IColony colony = shop.getColony();
    if (colony == null) {
      return;
    }
    Level level = colony.getWorld();
    if (level == null) {
      return;
    }
    long now = level.getGameTime();
    if (lastRackScanTick >= 0 && now - lastRackScanTick < 40L) {
      return;
    }
    lastRackScanTick = now;
    int added = 0;
    Tuple<BlockPos, BlockPos> corners = shop.getCorners();
    boolean usedFallback = false;
    if (corners != null && corners.getA() != null && corners.getB() != null) {
      BlockPos start = corners.getA();
      BlockPos end = corners.getB();
      int minX = Math.min(start.getX(), end.getX());
      int maxX = Math.max(start.getX(), end.getX());
      int minY = Math.min(start.getY(), end.getY());
      int maxY = Math.max(start.getY(), end.getY());
      int minZ = Math.min(start.getZ(), end.getZ());
      int maxZ = Math.max(start.getZ(), end.getZ());
      if (minX != maxX && minZ != maxZ) {
        added += scanRackBox(level, minX, maxX, minY, maxY, minZ, maxZ);
      } else {
        usedFallback = true;
      }
    } else {
      usedFallback = true;
    }
    if (usedFallback) {
      BlockPos location = shop.getLocation().getInDimensionLocation();
      int minX = location.getX() - 8;
      int maxX = location.getX() + 8;
      int minY = location.getY() - 8;
      int maxY = location.getY() + 8;
      int minZ = location.getZ() - 8;
      int maxZ = location.getZ() + 8;
      added += scanRackBox(level, minX, maxX, minY, maxY, minZ, maxZ);
    }
    if (Config.DEBUG_LOGGING.getAsBoolean() && added > 0) {
      TheSettlerXCreate.LOGGER.info(
          "[CreateShop] ensureRackContainers added={} total={}", added, shop.getContainerCount());
    }
  }

  java.util.Map<ItemStack, Integer> getStockCountsForKeys(List<ItemStack> keys) {
    java.util.Map<ItemStack, Integer> counts = new java.util.HashMap<>();
    if (keys == null || keys.isEmpty()) {
      return counts;
    }
    for (ItemStack key : keys) {
      if (key == null || key.isEmpty()) {
        continue;
      }
      ItemStack normalized = normalizeKey(key);
      if (!containsKey(counts, normalized)) {
        counts.put(normalized, 0);
      }
    }
    if (counts.isEmpty()) {
      return counts;
    }
    IColony colony = shop.getColony();
    if (colony == null) {
      return counts;
    }
    Level level = colony.getWorld();
    if (level == null) {
      return counts;
    }
    ensureRackContainers();
    for (BlockPos pos : shop.getContainerList()) {
      if (!WorldUtil.isBlockLoaded(level, pos)) {
        continue;
      }
      BlockEntity entity = level.getBlockEntity(pos);
      if (!(entity instanceof TileEntityRack rack)) {
        continue;
      }
      addCountsFromHandler(rack.getInventory(), counts);
    }
    return counts;
  }

  List<BigItemStack> getRegisteredStorageStock() {
    java.util.List<ItemStack> merged = new java.util.ArrayList<>();
    IColony colony = shop.getColony();
    if (colony == null) {
      return java.util.Collections.emptyList();
    }
    Level level = colony.getWorld();
    if (level == null) {
      return java.util.Collections.emptyList();
    }
    ensureRackContainers();
    for (BlockPos pos : shop.getContainerList()) {
      if (!WorldUtil.isBlockLoaded(level, pos)) {
        continue;
      }
      BlockEntity entity = level.getBlockEntity(pos);
      if (entity == null) {
        continue;
      }
      net.neoforged.neoforge.items.IItemHandler handler = null;
      if (entity instanceof TileEntityRack rack) {
        handler = rack.getInventory();
      } else {
        var state = level.getBlockState(pos);
        handler = Capabilities.ItemHandler.BLOCK.getCapability(level, pos, state, entity, null);
        if (handler == null) {
          handler =
              Capabilities.ItemHandler.BLOCK.getCapability(
                  level, pos, state, entity, net.minecraft.core.Direction.UP);
        }
      }
      if (handler == null) {
        continue;
      }
      mergeFromHandler(handler, merged);
    }

    if (merged.isEmpty()) {
      return java.util.Collections.emptyList();
    }
    java.util.List<BigItemStack> result = new java.util.ArrayList<>();
    for (ItemStack stack : merged) {
      if (stack == null || stack.isEmpty()) {
        continue;
      }
      result.add(new BigItemStack(stack.copy(), stack.getCount()));
    }
    result.sort(java.util.Comparator.comparingInt((BigItemStack entry) -> entry.count).reversed());
    return result;
  }

  void onRackRegistered(Level world, BlockPos pos, BlockEntity entity) {
    if (!(entity instanceof TileEntityRack rack)) {
      return;
    }
    rack.setInWarehouse(Boolean.TRUE);
  }

  private int scanRackBox(Level level, int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
    int added = 0;
    for (int x = minX; x <= maxX; x++) {
      for (int y = minY; y <= maxY; y++) {
        for (int z = minZ; z <= maxZ; z++) {
          BlockPos pos = new BlockPos(x, y, z);
          if (!WorldUtil.isBlockLoaded(level, pos)) {
            continue;
          }
          BlockEntity entity = level.getBlockEntity(pos);
          if (!(entity instanceof TileEntityRack rack)) {
            continue;
          }
          if (shop.hasContainer(pos)) {
            continue;
          }
          if (!(level.getBlockState(pos).getBlock() instanceof AbstractBlockMinecoloniesRack)) {
            continue;
          }
          shop.addContainer(pos);
          onRackRegistered(level, pos, rack);
          added++;
        }
      }
    }
    return added;
  }

  private void addCountsFromHandler(
      net.neoforged.neoforge.items.IItemHandler handler, java.util.Map<ItemStack, Integer> counts) {
    if (handler == null || counts == null || counts.isEmpty()) {
      return;
    }
    for (int slot = 0; slot < handler.getSlots(); slot++) {
      ItemStack stack = handler.getStackInSlot(slot);
      if (stack == null || stack.isEmpty()) {
        continue;
      }
      ItemStack key = findMatchingKey(counts, stack);
      if (key != null) {
        counts.put(key, counts.get(key) + stack.getCount());
      }
    }
  }

  private ItemStack findMatchingKey(java.util.Map<ItemStack, Integer> counts, ItemStack stack) {
    if (counts == null || counts.isEmpty() || stack == null || stack.isEmpty()) {
      return null;
    }
    for (ItemStack key : counts.keySet()) {
      if (ItemStack.isSameItemSameComponents(key, stack)) {
        return key;
      }
    }
    return null;
  }

  private boolean containsKey(java.util.Map<ItemStack, Integer> counts, ItemStack key) {
    if (counts == null || counts.isEmpty() || key == null || key.isEmpty()) {
      return false;
    }
    for (ItemStack existing : counts.keySet()) {
      if (ItemStack.isSameItemSameComponents(existing, key)) {
        return true;
      }
    }
    return false;
  }

  private ItemStack normalizeKey(ItemStack stack) {
    ItemStack copy = stack.copy();
    copy.setCount(1);
    return copy;
  }

  private void mergeFromHandler(
      net.neoforged.neoforge.items.IItemHandler handler, java.util.List<ItemStack> merged) {
    if (handler == null || merged == null) {
      return;
    }
    for (int slot = 0; slot < handler.getSlots(); slot++) {
      ItemStack stack = handler.getStackInSlot(slot);
      if (stack == null || stack.isEmpty()) {
        continue;
      }
      boolean found = false;
      for (ItemStack existing : merged) {
        if (ItemStack.isSameItemSameComponents(existing, stack)) {
          existing.grow(stack.getCount());
          found = true;
          break;
        }
      }
      if (!found) {
        merged.add(stack.copy());
      }
    }
  }
}
