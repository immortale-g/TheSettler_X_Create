package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import com.google.common.collect.Lists;
import com.minecolonies.api.colony.requestsystem.requestable.IDeliverable;
import com.minecolonies.api.colony.requestsystem.requestable.Tool;
import com.minecolonies.api.equipment.registry.EquipmentTypeEntry;
import com.minecolonies.api.tileentities.AbstractTileEntityRack;
import com.minecolonies.api.util.WorldUtil;
import com.thesettler_x_create.DebugLog;
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
  /**
   * The rack stock a colony request may be served from: what the racks hold, minus what the shop
   * still owes its Colony Factory Gauges.
   *
   * <p>A gauge order is goods the colony already handed over, waiting to be packaged for a Create
   * address. Since the rack/hut split they wait in the hut buffer, where the racks do not see them
   * anyway; the subtraction is what keeps a full hut, which puts them into the racks instead, from
   * turning one colony's gauge order into another citizen's delivery. It stands where a per-gauge
   * pickup reservation used to, which reached every caller through {@code reservedForOthers} and
   * made every reader of that ledger deal with reservations that were not rack stock.
   */
  int getAvailableFromRacks(TileEntityCreateShop tile, IDeliverable deliverable) {
    if (tile == null || tile.getBuilding() == null) {
      return 0;
    }
    Level level = tile.getLevel();
    if (level == null) {
      return 0;
    }
    BuildingCreateShop shop =
        tile.getBuilding() instanceof BuildingCreateShop createShop ? createShop : null;
    if (shop != null) {
      shop.ensureRackContainers();
    }
    ItemStack expected = deliverable == null ? ItemStack.EMPTY : deliverable.getResult();
    int total = 0;
    int sameItemTotal = 0;
    int rackCount = 0;
    int containerCount = tile.getBuilding().getContainers().size();
    for (BlockPos pos : tile.getBuilding().getContainers()) {
      if (!WorldUtil.isBlockLoaded(level, pos)) {
        continue;
      }
      BlockEntity entity = level.getBlockEntity(pos);
      if (!(entity instanceof AbstractTileEntityRack rack)) {
        continue;
      }
      rackCount++;
      total += rack.getItemCount(deliverable::matches);
      sameItemTotal += countSameItemInRack(rack, expected);
    }
    int owedToGauges =
        shop == null || deliverable == null ? 0 : shop.getOwedToGauges(deliverable::matches);
    int available = Math.max(0, total - owedToGauges);
    if (available > 0) {
      return available;
    }
    if (DebugLog.enabled()) {
      TheSettlerXCreate.LOGGER.info(
          "[CreateShop] rack availability strict=0 expected={} containers={} racksSeen={} sameItem={} inRacks={} owedToGauges={}",
          expected == null || expected.isEmpty() ? "<empty>" : expected.getItem(),
          containerCount,
          rackCount,
          sameItemTotal,
          total,
          owedToGauges);
    }
    return available;
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
    return planned;
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

  private int countSameItemInRack(AbstractTileEntityRack rack, ItemStack expected) {
    if (rack == null || expected == null || expected.isEmpty()) {
      return 0;
    }
    return rack.getItemCount(
        candidate -> candidate != null && ItemStack.isSameItem(candidate, expected));
  }
}
