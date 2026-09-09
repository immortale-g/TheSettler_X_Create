package com.thesettler_x_create.minecolonies.tileentity;

import com.minecolonies.api.inventory.InventoryCitizen;
import com.minecolonies.api.tileentities.AbstractTileEntityRack;
import com.minecolonies.api.util.InventoryUtils;
import com.minecolonies.api.util.ItemStackUtils;
import com.minecolonies.api.util.Tuple;
import com.minecolonies.api.util.WorldUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * Low-level rack scanning/insertion/extraction engine for a {@link TileEntityCreateShop} - finding
 * which of the shop's racks hold or can accept a given stack, and a virtual (simulated, no real
 * mutation) capacity planner for "how much of this inbound delivery could actually fit right now."
 * Extracted from {@code TileEntityCreateShop} (which held this directly until the pre-1.0 hardening
 * pass) to separate raw rack access from the housekeeping/reservation-budget logic layered on top
 * of it (which stays on {@code TileEntityCreateShop} itself, since it depends on {@link
 * com.thesettler_x_create.blockentity.CreateShopBlockEntity}'s reservation ledger rather than on
 * racks alone).
 */
class ShopRackAccess {
  private final TileEntityCreateShop owner;

  ShopRackAccess(TileEntityCreateShop owner) {
    this.owner = owner;
  }

  /**
   * Collects every rack among this shop's containers that sits in a loaded chunk. Shared by every
   * method that needs to scan the shop's racks, so the loaded-chunk check and rack-type check only
   * need to be correct in one place.
   */
  List<TileEntityCreateShop.LoadedRack> getLoadedRacks() {
    List<TileEntityCreateShop.LoadedRack> racks = new ArrayList<>();
    if (owner.getBuilding() == null || owner.getLevel() == null) {
      return racks;
    }
    for (BlockPos pos : owner.getBuilding().getContainers()) {
      if (!WorldUtil.isBlockLoaded(owner.getLevel(), pos)) {
        continue;
      }
      BlockEntity entity = owner.getLevel().getBlockEntity(pos);
      if (entity instanceof AbstractTileEntityRack rack) {
        racks.add(new TileEntityCreateShop.LoadedRack(pos, rack));
      }
    }
    return racks;
  }

  boolean hasMatchingItemStackInWarehouse(Predicate<ItemStack> filter, int count) {
    int found = 0;
    for (TileEntityCreateShop.LoadedRack loaded : getLoadedRacks()) {
      AbstractTileEntityRack rack = loaded.rack();
      if (rack.isEmpty()) {
        continue;
      }
      found += rack.getItemCount(filter);
      if (found >= count) {
        return true;
      }
    }
    return false;
  }

  boolean hasMatchingItemStackInWarehouse(
      ItemStack stack, int count, boolean matchNBT, boolean matchDamage, int countExcluded) {
    int found = 0 - countExcluded;
    for (TileEntityCreateShop.LoadedRack loaded : getLoadedRacks()) {
      AbstractTileEntityRack rack = loaded.rack();
      if (rack.isEmpty()) {
        continue;
      }
      found += rack.getCount(stack, matchDamage, matchNBT);
      if (found >= count) {
        return true;
      }
    }
    return false;
  }

  List<Tuple<ItemStack, BlockPos>> getMatchingItemStacksInWarehouse(Predicate<ItemStack> filter) {
    List<Tuple<ItemStack, BlockPos>> matches = new ArrayList<>();
    for (TileEntityCreateShop.LoadedRack loaded : getLoadedRacks()) {
      AbstractTileEntityRack rack = loaded.rack();
      if (rack.isEmpty()) {
        continue;
      }
      if (rack.getItemCount(filter) <= 0) {
        continue;
      }
      for (ItemStack stack : InventoryUtils.filterItemHandler(rack.getInventory(), filter)) {
        matches.add(new Tuple<>(stack, loaded.pos()));
      }
    }
    return matches;
  }

  void dumpInventoryIntoWareHouse(InventoryCitizen inventory) {
    for (int slot = 0; slot < inventory.getSlots(); slot++) {
      ItemStack stack = inventory.getStackInSlot(slot);
      if (ItemStackUtils.isEmpty(stack)) {
        continue;
      }
      AbstractTileEntityRack rack = getRackForStack(stack);
      if (rack == null) {
        owner.maybeNotifyFull();
        return;
      }
      IItemHandler handler = rack.getItemHandlerCap();
      InventoryUtils.transferItemStackIntoNextBestSlotInItemHandler(inventory, slot, handler);
    }
  }

  /**
   * Tries to insert stacks into shop racks and returns leftovers that did not fit.
   *
   * <p>Used for manual package handover recovery.
   */
  List<ItemStack> insertIntoRacks(List<ItemStack> stacks) {
    return insertIntoRacksInternal(stacks, true);
  }

  /**
   * Tries to insert stacks into shop racks only and returns leftovers that did not fit.
   *
   * <p>Used for lost-package handover so rack-only delivery flow stays consistent.
   */
  List<ItemStack> insertIntoRacksOnly(List<ItemStack> stacks) {
    return insertIntoRacksInternal(stacks, false);
  }

  private List<ItemStack> insertIntoRacksInternal(
      List<ItemStack> stacks, boolean allowHutFallback) {
    List<ItemStack> leftovers = new ArrayList<>();
    if (stacks == null || stacks.isEmpty()) {
      return leftovers;
    }
    for (ItemStack original : stacks) {
      if (ItemStackUtils.isEmpty(original)) {
        continue;
      }
      ItemStack remaining = original.copy();
      int containerCount =
          owner.getBuilding() == null ? 1 : Math.max(1, owner.getBuilding().getContainers().size());
      int guard = containerCount + 2;
      while (!remaining.isEmpty() && guard-- > 0) {
        AbstractTileEntityRack rack = getRackForStack(remaining);
        if (rack == null) {
          break;
        }
        ItemStack before = remaining.copy();
        remaining =
            InventoryUtils.transferItemStackIntoNextBestSlotInItemHandlerWithResult(
                remaining, rack.getItemHandlerCap());
        if (remaining.getCount() == before.getCount()) {
          break;
        }
      }
      if (allowHutFallback && !remaining.isEmpty()) {
        IItemHandler hut = owner.getItemHandlerCap((Direction) null);
        if (hut != null) {
          remaining =
              InventoryUtils.transferItemStackIntoNextBestSlotInItemHandlerWithResult(
                  remaining, hut);
        }
      }
      if (!remaining.isEmpty()) {
        leftovers.add(remaining);
      }
    }
    return leftovers;
  }

  /**
   * True if at least one item of the given stack can currently be accepted by rack or hut buffer.
   */
  boolean canAcceptInbound(ItemStack stack) {
    if (stack == null || stack.isEmpty()) {
      return false;
    }
    ItemStack probe = stack.copy();
    probe.setCount(1);
    AbstractTileEntityRack rack = getRackForStack(probe);
    if (rack != null && canInsertAtLeastOne(rack.getItemHandlerCap(), probe)) {
      return true;
    }
    IItemHandler hut = owner.getItemHandlerCap((Direction) null);
    return canInsertAtLeastOne(hut, probe);
  }

  /**
   * Computes how much of the requested inbound stacks can fit right now using a virtual slot
   * simulation across racks and hut buffer.
   *
   * <p>This prevents over-ordering when only limited free slots are available for new item types.
   */
  List<ItemStack> planInboundAcceptedStacks(List<ItemStack> requestedStacks) {
    if (requestedStacks == null || requestedStacks.isEmpty()) {
      return List.of();
    }
    List<VirtualItemHandler> virtualRacks = collectVirtualRacks();
    List<ItemStack> accepted = new ArrayList<>();

    for (ItemStack original : requestedStacks) {
      if (ItemStackUtils.isEmpty(original) || original.getCount() <= 0) {
        continue;
      }
      ItemStack remaining = original.copy();
      int movedTotal = 0;
      while (!remaining.isEmpty()) {
        VirtualItemHandler rack = findBestVirtualRack(remaining, virtualRacks);
        int moved = insertIntoVirtual(rack, remaining);
        if (moved <= 0) {
          break;
        }
        movedTotal += moved;
      }
      if (movedTotal > 0) {
        ItemStack movedStack = original.copy();
        movedStack.setCount(movedTotal);
        accepted.add(movedStack);
      }
    }
    return accepted;
  }

  static boolean canInsertAtLeastOne(IItemHandler handler, ItemStack stack) {
    if (handler == null || stack == null || stack.isEmpty()) {
      return false;
    }
    for (int slot = 0; slot < handler.getSlots(); slot++) {
      ItemStack remaining = handler.insertItem(slot, stack, true);
      if (remaining.isEmpty() || remaining.getCount() < stack.getCount()) {
        return true;
      }
    }
    return false;
  }

  static int simulateInsertCount(IItemHandler handler, ItemStack stack, int maxCount) {
    if (handler == null || stack == null || stack.isEmpty() || maxCount <= 0) {
      return 0;
    }
    ItemStack remaining = stack.copy();
    remaining.setCount(Math.min(maxCount, stack.getCount()));
    int requested = remaining.getCount();
    for (int slot = 0; slot < handler.getSlots() && !remaining.isEmpty(); slot++) {
      remaining = handler.insertItem(slot, remaining, true);
    }
    return Math.max(0, requested - (remaining.isEmpty() ? 0 : remaining.getCount()));
  }

  private List<VirtualItemHandler> collectVirtualRacks() {
    List<VirtualItemHandler> racks = new ArrayList<>();
    for (TileEntityCreateShop.LoadedRack loaded : getLoadedRacks()) {
      VirtualItemHandler virtual = createVirtualHandler(loaded.rack().getItemHandlerCap());
      if (virtual != null) {
        racks.add(virtual);
      }
    }
    return racks;
  }

  private static VirtualItemHandler createVirtualHandler(IItemHandler handler) {
    if (handler == null) {
      return null;
    }
    return new VirtualItemHandler(handler);
  }

  private static VirtualItemHandler findBestVirtualRack(
      ItemStack stack, List<VirtualItemHandler> racks) {
    if (stack == null || stack.isEmpty() || racks == null || racks.isEmpty()) {
      return null;
    }
    for (VirtualItemHandler rack : racks) {
      if (rack != null && rack.hasExactWithSpace(stack)) {
        return rack;
      }
    }
    for (VirtualItemHandler rack : racks) {
      if (rack != null && rack.hasSimilarWithSpace(stack)) {
        return rack;
      }
    }
    VirtualItemHandler best = null;
    int bestFree = 0;
    for (VirtualItemHandler rack : racks) {
      if (rack == null) {
        continue;
      }
      int free = rack.freeSlots();
      if (free > bestFree) {
        best = rack;
        bestFree = free;
      }
    }
    return best;
  }

  private static int insertIntoVirtual(VirtualItemHandler handler, ItemStack stack) {
    if (handler == null || stack == null || stack.isEmpty()) {
      return 0;
    }
    int before = stack.getCount();
    handler.insert(stack);
    return before - stack.getCount();
  }

  private static final class VirtualItemHandler {
    private final List<ItemStack> slots;
    private final int[] slotLimits;

    private VirtualItemHandler(IItemHandler source) {
      this.slots = new ArrayList<>();
      this.slotLimits = new int[source.getSlots()];
      for (int i = 0; i < source.getSlots(); i++) {
        ItemStack inSlot = source.getStackInSlot(i);
        this.slots.add(inSlot == null ? ItemStack.EMPTY : inSlot.copy());
        this.slotLimits[i] = Math.max(1, source.getSlotLimit(i));
      }
    }

    private int freeSlots() {
      int free = 0;
      for (ItemStack stack : slots) {
        if (stack == null || stack.isEmpty()) {
          free++;
        }
      }
      return free;
    }

    private boolean hasExactWithSpace(ItemStack incoming) {
      for (int i = 0; i < slots.size(); i++) {
        ItemStack slot = slots.get(i);
        if (slot == null || slot.isEmpty()) {
          continue;
        }
        if (!ItemStack.isSameItemSameComponents(slot, incoming)) {
          continue;
        }
        int max = Math.min(slot.getMaxStackSize(), slotLimits[i]);
        if (slot.getCount() < max) {
          return true;
        }
      }
      return false;
    }

    private boolean hasSimilarWithSpace(ItemStack incoming) {
      for (int i = 0; i < slots.size(); i++) {
        ItemStack slot = slots.get(i);
        if (slot == null || slot.isEmpty()) {
          continue;
        }
        if (!ItemStack.isSameItem(slot, incoming)) {
          continue;
        }
        int max = Math.min(slot.getMaxStackSize(), slotLimits[i]);
        if (slot.getCount() < max) {
          return true;
        }
      }
      return false;
    }

    private void insert(ItemStack remaining) {
      if (remaining == null || remaining.isEmpty()) {
        return;
      }
      for (int i = 0; i < slots.size() && !remaining.isEmpty(); i++) {
        ItemStack slot = slots.get(i);
        if (slot == null || slot.isEmpty()) {
          continue;
        }
        if (!ItemStack.isSameItemSameComponents(slot, remaining)) {
          continue;
        }
        int max = Math.min(slot.getMaxStackSize(), slotLimits[i]);
        if (slot.getCount() >= max) {
          continue;
        }
        int moved = Math.min(remaining.getCount(), max - slot.getCount());
        if (moved <= 0) {
          continue;
        }
        slot.grow(moved);
        remaining.shrink(moved);
      }
      for (int i = 0; i < slots.size() && !remaining.isEmpty(); i++) {
        ItemStack slot = slots.get(i);
        if (slot != null && !slot.isEmpty()) {
          continue;
        }
        int max = Math.min(remaining.getMaxStackSize(), slotLimits[i]);
        int moved = Math.min(remaining.getCount(), max);
        if (moved <= 0) {
          continue;
        }
        ItemStack placed = remaining.copy();
        placed.setCount(moved);
        slots.set(i, placed);
        remaining.shrink(moved);
      }
    }
  }

  AbstractTileEntityRack getRackForStack(ItemStack stack) {
    AbstractTileEntityRack rack = getPositionOfChestWithItemStack(stack);
    if (rack != null) {
      return rack;
    }
    rack = getPositionOfChestWithSimilarItemStack(stack);
    if (rack != null) {
      return rack;
    }
    return searchMostEmptyRack();
  }

  private AbstractTileEntityRack getPositionOfChestWithItemStack(ItemStack stack) {
    for (TileEntityCreateShop.LoadedRack loaded : getLoadedRacks()) {
      AbstractTileEntityRack rack = loaded.rack();
      if (rack.getFreeSlots() <= 0) {
        continue;
      }
      if (rack.hasItemStack(stack, 1, true)) {
        return rack;
      }
    }
    return null;
  }

  private AbstractTileEntityRack getPositionOfChestWithSimilarItemStack(ItemStack stack) {
    for (TileEntityCreateShop.LoadedRack loaded : getLoadedRacks()) {
      AbstractTileEntityRack rack = loaded.rack();
      if (rack.getFreeSlots() <= 0) {
        continue;
      }
      if (rack.hasSimilarStack(stack)) {
        return rack;
      }
    }
    return null;
  }

  private AbstractTileEntityRack searchMostEmptyRack() {
    int bestFree = 0;
    AbstractTileEntityRack bestRack = null;
    for (TileEntityCreateShop.LoadedRack loaded : getLoadedRacks()) {
      AbstractTileEntityRack rack = loaded.rack();
      if (rack.isEmpty()) {
        return rack;
      }
      int freeSlots = rack.getFreeSlots();
      if (freeSlots > bestFree) {
        bestFree = freeSlots;
        bestRack = rack;
      }
    }
    return bestRack;
  }
}
