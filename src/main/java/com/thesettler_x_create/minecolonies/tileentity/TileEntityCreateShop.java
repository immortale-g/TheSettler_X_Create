package com.thesettler_x_create.minecolonies.tileentity;

import com.minecolonies.api.inventory.InventoryCitizen;
import com.minecolonies.api.tileentities.AbstractTileEntityRack;
import com.minecolonies.api.tileentities.AbstractTileEntityWareHouse;
import com.minecolonies.api.util.InventoryUtils;
import com.minecolonies.api.util.ItemStackUtils;
import com.minecolonies.api.util.Tuple;
import com.minecolonies.api.util.WorldUtil;
import com.minecolonies.core.tileentities.TileEntityRack;
import com.thesettler_x_create.blockentity.CreateShopBlockEntity;
import com.thesettler_x_create.init.ModBlockEntities;
import com.thesettler_x_create.minecolonies.building.BuildingCreateShop;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

public class TileEntityCreateShop extends AbstractTileEntityWareHouse {
  private static final String TAG_NETWORK = "StockNetwork";
  private static final String TAG_ADDRESS = "ShopAddress";
  private static final long CAPACITY_STALL_TTL = 20L * 45L;
  private static final long CAPACITY_STALL_NOTICE_COOLDOWN = 20L * 90L;
  private UUID stockNetworkId;
  private String shopAddress = "";
  private long lastNotification;
  private long capacityStallUntil;
  private long capacityStallLastNotice;
  private ItemStack capacityStallStack = ItemStack.EMPTY;
  private int capacityStallRequested;
  private int capacityStallAccepted;

  public TileEntityCreateShop(BlockPos pos, BlockState state) {
    super(ModBlockEntities.CREATE_SHOP_BUILDING.get(), pos, state);
    this.lastNotification = 0L;
  }

  public void setStockNetworkId(@Nullable UUID id) {
    stockNetworkId = id;
    setChanged();
    if (com.thesettler_x_create.Config.DEBUG_LOGGING.getAsBoolean()) {
      com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
          "[CreateShop] Stock network set to {} at {}", id, worldPosition);
    }
  }

  @Nullable
  public UUID getStockNetworkId() {
    return stockNetworkId;
  }

  public String getShopAddress() {
    return shopAddress;
  }

  public void setShopAddress(String address) {
    shopAddress = address == null ? "" : address;
    setChanged();
  }

  @Override
  public void saveAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
    super.saveAdditional(tag, registries);
    if (stockNetworkId != null) {
      tag.putUUID(TAG_NETWORK, stockNetworkId);
    }
    if (!shopAddress.isEmpty()) {
      tag.putString(TAG_ADDRESS, shopAddress);
    }
  }

  @Override
  public void loadAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
    super.loadAdditional(tag, registries);
    stockNetworkId = tag.hasUUID(TAG_NETWORK) ? tag.getUUID(TAG_NETWORK) : null;
    shopAddress = tag.getString(TAG_ADDRESS);
  }

  @Override
  public AbstractContainerMenu createMenu(int id, Inventory playerInventory, Player player) {
    return super.createMenu(id, playerInventory, player);
  }

  @Override
  public void tick() {
    super.tick();
  }

  @Override
  public boolean hasMatchingItemStackInWarehouse(Predicate<ItemStack> filter, int count) {
    int found = 0;
    if (getBuilding() == null) {
      return false;
    }
    for (BlockPos pos : getBuilding().getContainers()) {
      if (!WorldUtil.isBlockLoaded(level, pos)) {
        continue;
      }
      BlockEntity entity = getLevel().getBlockEntity(pos);
      if (!(entity instanceof TileEntityRack rack)) {
        continue;
      }
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

  @Override
  public boolean hasMatchingItemStackInWarehouse(ItemStack stack, int count, boolean matchNBT) {
    return hasMatchingItemStackInWarehouse(stack, count, matchNBT, 0);
  }

  @Override
  public boolean hasMatchingItemStackInWarehouse(
      ItemStack stack, int count, boolean matchNBT, boolean matchDamage, int countExcluded) {
    int found = 0 - countExcluded;
    if (getBuilding() == null) {
      return false;
    }
    for (BlockPos pos : getBuilding().getContainers()) {
      if (!WorldUtil.isBlockLoaded(level, pos)) {
        continue;
      }
      BlockEntity entity = getLevel().getBlockEntity(pos);
      if (!(entity instanceof AbstractTileEntityRack rack)) {
        continue;
      }
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

  @Override
  public boolean hasMatchingItemStackInWarehouse(
      ItemStack stack, int count, boolean matchNBT, int countExcluded) {
    return hasMatchingItemStackInWarehouse(stack, count, matchNBT, true, countExcluded);
  }

  @Override
  public List<Tuple<ItemStack, BlockPos>> getMatchingItemStacksInWarehouse(
      Predicate<ItemStack> filter) {
    List<Tuple<ItemStack, BlockPos>> matches = new ArrayList<>();
    if (getBuilding() == null) {
      return matches;
    }
    for (BlockPos pos : getBuilding().getContainers()) {
      if (!WorldUtil.isBlockLoaded(level, pos)) {
        continue;
      }
      BlockEntity entity = getLevel().getBlockEntity(pos);
      if (!(entity instanceof TileEntityRack rack)) {
        continue;
      }
      if (rack.isEmpty()) {
        continue;
      }
      if (rack.getItemCount(filter) <= 0) {
        continue;
      }
      for (ItemStack stack : InventoryUtils.filterItemHandler(rack.getInventory(), filter)) {
        matches.add(new Tuple<>(stack, pos));
      }
    }
    return matches;
  }

  @Override
  public void dumpInventoryIntoWareHouse(InventoryCitizen inventory) {
    for (int slot = 0; slot < inventory.getSlots(); slot++) {
      ItemStack stack = inventory.getStackInSlot(slot);
      if (ItemStackUtils.isEmpty(stack)) {
        continue;
      }
      AbstractTileEntityRack rack = getRackForStack(stack);
      if (rack == null) {
        maybeNotifyFull();
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
  public List<ItemStack> insertIntoRacks(List<ItemStack> stacks) {
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
          getBuilding() == null ? 1 : Math.max(1, getBuilding().getContainers().size());
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
      if (!remaining.isEmpty()) {
        IItemHandler hut = getItemHandlerCap((Direction) null);
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
  public boolean canAcceptInbound(ItemStack stack) {
    if (stack == null || stack.isEmpty()) {
      return false;
    }
    ItemStack probe = stack.copy();
    probe.setCount(1);
    AbstractTileEntityRack rack = getRackForStack(probe);
    if (rack != null && canInsertAtLeastOne(rack.getItemHandlerCap(), probe)) {
      return true;
    }
    IItemHandler hut = getItemHandlerCap((Direction) null);
    return canInsertAtLeastOne(hut, probe);
  }

  /**
   * Returns true when at least one rack item is currently not reserved for pending requests.
   *
   * <p>Used to keep the shopkeeper active for inbound rack cleanup work.
   */
  public boolean hasUnreservedRackItems(@Nullable CreateShopBlockEntity pickup) {
    if (pickup == null || getBuilding() == null || getLevel() == null) {
      return false;
    }
    for (RackStackBudget budget : collectRackBudgets(pickup)) {
      if (budget.remaining > 0) {
        return true;
      }
    }
    return false;
  }

  /**
   * Moves up to {@code maxStacks} unreserved rack stacks into hut inventory.
   *
   * <p>Items reserved by pending request ids remain in racks so delivery planning can consume them.
   */
  public int moveUnreservedRackStacksToHut(@Nullable CreateShopBlockEntity pickup, int maxStacks) {
    if (pickup == null || maxStacks <= 0 || getBuilding() == null || getLevel() == null) {
      return 0;
    }
    IItemHandler hut = getItemHandlerCap((Direction) null);
    if (hut == null) {
      return 0;
    }
    List<RackStackBudget> budgets = collectRackBudgets(pickup);
    if (budgets.isEmpty()) {
      return 0;
    }
    List<AbstractTileEntityRack> racks = collectRacksForHousekeeping();
    if (racks.isEmpty()) {
      return 0;
    }
    int movedStacks = 0;
    for (AbstractTileEntityRack rack : racks) {
      if (movedStacks >= maxStacks || rack == null) {
        continue;
      }
      IItemHandler handler = rack.getInventory();
      if (handler == null) {
        handler = rack.getItemHandlerCap();
      }
      if (handler == null) {
        continue;
      }
      for (int slot = 0; slot < handler.getSlots() && movedStacks < maxStacks; slot++) {
        ItemStack inSlot = handler.getStackInSlot(slot);
        if (inSlot.isEmpty()) {
          continue;
        }
        RackStackBudget budget = findBudget(budgets, inSlot);
        if (budget == null || budget.remaining <= 0) {
          continue;
        }
        int stackMoveTarget = Math.min(inSlot.getCount(), budget.remaining);
        if (stackMoveTarget <= 0) {
          continue;
        }
        int insertable = simulateInsertCount(hut, inSlot, stackMoveTarget);
        if (insertable <= 0) {
          continue;
        }
        ItemStack extracted = handler.extractItem(slot, insertable, false);
        if (extracted.isEmpty()) {
          continue;
        }
        ItemStack leftover =
            InventoryUtils.transferItemStackIntoNextBestSlotInItemHandlerWithResult(extracted, hut);
        int inserted = insertable - (leftover.isEmpty() ? 0 : leftover.getCount());
        if (!leftover.isEmpty()) {
          InventoryUtils.transferItemStackIntoNextBestSlotInItemHandlerWithResult(
              leftover, handler);
        }
        if (inserted <= 0) {
          continue;
        }
        budget.remaining -= inserted;
        movedStacks++;
      }
    }
    if (movedStacks > 0) {
      setChanged();
    }
    return movedStacks;
  }

  /**
   * Marks a temporary capacity stall when requested inbound quantity cannot fit into shop storage.
   */
  public void noteCapacityStall(ItemStack stack, int requested, int accepted) {
    if (stack == null || stack.isEmpty() || requested <= 0 || accepted >= requested) {
      return;
    }
    long now = getLevel() == null ? 0L : getLevel().getGameTime();
    capacityStallUntil = now + CAPACITY_STALL_TTL;
    capacityStallStack = stack.copy();
    capacityStallStack.setCount(1);
    capacityStallRequested = Math.max(1, requested);
    capacityStallAccepted = Math.max(0, accepted);
  }

  public void clearCapacityStall() {
    capacityStallUntil = 0L;
    capacityStallStack = ItemStack.EMPTY;
    capacityStallRequested = 0;
    capacityStallAccepted = 0;
  }

  public boolean hasCapacityStall() {
    if (capacityStallUntil <= 0L || level == null) {
      return false;
    }
    return level.getGameTime() < capacityStallUntil;
  }

  @Nullable
  public CapacityStallNotice consumeCapacityStallNotice() {
    if (!hasCapacityStall() || level == null || capacityStallStack.isEmpty()) {
      return null;
    }
    long now = level.getGameTime();
    if (capacityStallLastNotice > 0L
        && now - capacityStallLastNotice < CAPACITY_STALL_NOTICE_COOLDOWN) {
      return null;
    }
    capacityStallLastNotice = now;
    return new CapacityStallNotice(
        capacityStallStack.copy(), capacityStallRequested, capacityStallAccepted);
  }

  /**
   * Computes how much of the requested inbound stacks can fit right now using a virtual slot
   * simulation across racks and hut buffer.
   *
   * <p>This prevents over-ordering when only limited free slots are available for new item types.
   */
  public List<ItemStack> planInboundAcceptedStacks(List<ItemStack> requestedStacks) {
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

  private static boolean canInsertAtLeastOne(IItemHandler handler, ItemStack stack) {
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

  private static int simulateInsertCount(IItemHandler handler, ItemStack stack, int maxCount) {
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
    if (getBuilding() == null || getLevel() == null) {
      return racks;
    }
    for (BlockPos pos : getBuilding().getContainers()) {
      if (!WorldUtil.isBlockLoaded(level, pos)) {
        continue;
      }
      BlockEntity entity = getLevel().getBlockEntity(pos);
      if (!(entity instanceof AbstractTileEntityRack rack)) {
        continue;
      }
      VirtualItemHandler virtual = createVirtualHandler(rack.getItemHandlerCap());
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

  private List<RackStackBudget> collectRackBudgets(CreateShopBlockEntity pickup) {
    List<RackStackBudget> totals = new ArrayList<>();
    if (getBuilding() == null || getLevel() == null || pickup == null) {
      return totals;
    }
    for (AbstractTileEntityRack rack : collectRacksForHousekeeping()) {
      IItemHandler handler = rack.getInventory();
      if (handler == null) {
        handler = rack.getItemHandlerCap();
      }
      if (handler == null) {
        continue;
      }
      for (int slot = 0; slot < handler.getSlots(); slot++) {
        ItemStack stack = handler.getStackInSlot(slot);
        if (stack.isEmpty() || stack.getCount() <= 0) {
          continue;
        }
        RackStackBudget budget = findBudget(totals, stack);
        if (budget == null) {
          ItemStack key = stack.copy();
          key.setCount(1);
          totals.add(new RackStackBudget(key, stack.getCount()));
        } else {
          budget.remaining += stack.getCount();
        }
      }
    }
    totals.removeIf(
        budget ->
            budget == null
                || budget.key == null
                || budget.key.isEmpty()
                || (budget.remaining =
                        Math.max(0, budget.remaining - pickup.getReservedFor(budget.key)))
                    <= 0);
    return totals;
  }

  private List<AbstractTileEntityRack> collectRacksForHousekeeping() {
    List<AbstractTileEntityRack> racks = new ArrayList<>();
    if (getBuilding() == null || getLevel() == null) {
      return racks;
    }
    if (getBuilding() instanceof BuildingCreateShop shop) {
      shop.ensureRackContainers();
    }
    Set<BlockPos> rackPositions = new LinkedHashSet<>(getBuilding().getContainers());
    if (rackPositions.isEmpty() && getBuilding() instanceof BuildingCreateShop shop) {
      BlockPos origin = shop.getLocation().getInDimensionLocation();
      int radius = 16;
      int minX = origin.getX() - radius;
      int maxX = origin.getX() + radius;
      int minY = origin.getY() - 6;
      int maxY = origin.getY() + 6;
      int minZ = origin.getZ() - radius;
      int maxZ = origin.getZ() + radius;
      for (int x = minX; x <= maxX; x++) {
        for (int y = minY; y <= maxY; y++) {
          for (int z = minZ; z <= maxZ; z++) {
            rackPositions.add(new BlockPos(x, y, z));
          }
        }
      }
    }
    for (BlockPos pos : rackPositions) {
      if (!WorldUtil.isBlockLoaded(level, pos)) {
        continue;
      }
      BlockEntity entity = getLevel().getBlockEntity(pos);
      if (entity instanceof AbstractTileEntityRack rack) {
        racks.add(rack);
      }
    }
    return racks;
  }

  @Nullable
  private static RackStackBudget findBudget(List<RackStackBudget> budgets, ItemStack stack) {
    if (budgets == null || budgets.isEmpty() || stack == null || stack.isEmpty()) {
      return null;
    }
    for (RackStackBudget budget : budgets) {
      if (budget != null && ItemStack.isSameItemSameComponents(budget.key, stack)) {
        return budget;
      }
    }
    return null;
  }

  private static final class RackStackBudget {
    private final ItemStack key;
    private int remaining;

    private RackStackBudget(ItemStack key, int remaining) {
      this.key = key;
      this.remaining = Math.max(0, remaining);
    }
  }

  private void maybeNotifyFull() {
    Level world = getLevel();
    if (world == null) {
      return;
    }
    if (world.getGameTime() - lastNotification <= 6000L) {
      return;
    }
    lastNotification = world.getGameTime();
  }

  private AbstractTileEntityRack getRackForStack(ItemStack stack) {
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
    if (getBuilding() == null) {
      return null;
    }
    for (BlockPos pos : getBuilding().getContainers()) {
      if (!WorldUtil.isBlockLoaded(level, pos)) {
        continue;
      }
      BlockEntity entity = getLevel().getBlockEntity(pos);
      if (!(entity instanceof AbstractTileEntityRack rack)) {
        continue;
      }
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
    if (getBuilding() == null) {
      return null;
    }
    for (BlockPos pos : getBuilding().getContainers()) {
      if (!WorldUtil.isBlockLoaded(level, pos)) {
        continue;
      }
      BlockEntity entity = getLevel().getBlockEntity(pos);
      if (!(entity instanceof AbstractTileEntityRack rack)) {
        continue;
      }
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
    if (getBuilding() == null) {
      return null;
    }
    for (BlockPos pos : getBuilding().getContainers()) {
      BlockEntity entity = getLevel().getBlockEntity(pos);
      if (!(entity instanceof TileEntityRack rack)) {
        continue;
      }
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

  public static class CapacityStallNotice {
    public final ItemStack stackKey;
    public final int requested;
    public final int accepted;

    public CapacityStallNotice(ItemStack stackKey, int requested, int accepted) {
      this.stackKey = stackKey;
      this.requested = requested;
      this.accepted = accepted;
    }
  }
}
