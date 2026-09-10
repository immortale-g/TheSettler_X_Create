package com.thesettler_x_create.minecolonies.tileentity;

import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.inventory.InventoryCitizen;
import com.minecolonies.api.tileentities.AbstractTileEntityRack;
import com.minecolonies.api.tileentities.AbstractTileEntityWareHouse;
import com.minecolonies.api.util.InventoryUtils;
import com.minecolonies.api.util.Tuple;
import com.thesettler_x_create.blockentity.CreateShopBlockEntity;
import com.thesettler_x_create.create.CreateNetworkPerfLogger;
import com.thesettler_x_create.init.ModBlockEntities;
import com.thesettler_x_create.minecolonies.building.BuildingCreateShop;
import java.util.ArrayList;
import java.util.List;
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

  // One logger per shop, not per CreateNetworkFacade call - see CreateNetworkPerfLogger's javadoc.
  private final CreateNetworkPerfLogger perfLogger = new CreateNetworkPerfLogger();

  private final ShopRackAccess rackAccess = new ShopRackAccess(this);

  public TileEntityCreateShop(BlockPos pos, BlockState state) {
    super(ModBlockEntities.CREATE_SHOP_BUILDING.get(), pos, state);
    this.lastNotification = 0L;
  }

  /** Resolves the shop tile entity a building module is attached to, or null if there is none. */
  @Nullable
  public static TileEntityCreateShop fromBuilding(@Nullable IBuilding building) {
    if (building == null) {
      return null;
    }
    return building.getTileEntity() instanceof TileEntityCreateShop shop ? shop : null;
  }

  /** Resolves the shop tile entity at a remembered position, or null if there is none loaded. */
  @Nullable
  public static TileEntityCreateShop fromLevel(@Nullable Level level, @Nullable BlockPos pos) {
    if (level == null || pos == null) {
      return null;
    }
    return level.getBlockEntity(pos) instanceof TileEntityCreateShop shop ? shop : null;
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

  public CreateNetworkPerfLogger getPerfLogger() {
    return perfLogger;
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
    if (level == null || level.isClientSide) {
      return;
    }
    if ((level.getGameTime() % 10L) != 0L) {
      return;
    }
    if (!(getBuilding() instanceof BuildingCreateShop shop)) {
      return;
    }
    var resolver = shop.getShopResolver();
    if (resolver == null
        || shop.getColony() == null
        || shop.getColony().getRequestManager() == null) {
      return;
    }
    resolver.sweepFastOrphanRecoveries(shop.getColony().getRequestManager());
  }

  /** A shop container position paired with the rack block entity found there. */
  public record LoadedRack(BlockPos pos, AbstractTileEntityRack rack) {}

  /**
   * Collects every rack among this shop's containers that sits in a loaded chunk. Shared by every
   * method that needs to scan the shop's racks, so the loaded-chunk check and rack-type check only
   * need to be correct in one place.
   */
  public List<LoadedRack> getLoadedRacks() {
    return rackAccess.getLoadedRacks();
  }

  @Override
  public boolean hasMatchingItemStackInWarehouse(Predicate<ItemStack> filter, int count) {
    return rackAccess.hasMatchingItemStackInWarehouse(filter, count);
  }

  @Override
  public boolean hasMatchingItemStackInWarehouse(ItemStack stack, int count, boolean matchNBT) {
    return hasMatchingItemStackInWarehouse(stack, count, matchNBT, 0);
  }

  @Override
  public boolean hasMatchingItemStackInWarehouse(
      ItemStack stack, int count, boolean matchNBT, boolean matchDamage, int countExcluded) {
    return rackAccess.hasMatchingItemStackInWarehouse(
        stack, count, matchNBT, matchDamage, countExcluded);
  }

  @Override
  public boolean hasMatchingItemStackInWarehouse(
      ItemStack stack, int count, boolean matchNBT, int countExcluded) {
    return hasMatchingItemStackInWarehouse(stack, count, matchNBT, true, countExcluded);
  }

  @Override
  public List<Tuple<ItemStack, BlockPos>> getMatchingItemStacksInWarehouse(
      Predicate<ItemStack> filter) {
    return rackAccess.getMatchingItemStacksInWarehouse(filter);
  }

  @Override
  public void dumpInventoryIntoWareHouse(InventoryCitizen inventory) {
    rackAccess.dumpInventoryIntoWareHouse(inventory);
  }

  /**
   * Tries to insert stacks into shop racks and returns leftovers that did not fit.
   *
   * <p>Used for manual package handover recovery.
   */
  public List<ItemStack> insertIntoRacks(List<ItemStack> stacks) {
    return rackAccess.insertIntoRacks(stacks);
  }

  /**
   * Tries to insert stacks into shop racks only and returns leftovers that did not fit.
   *
   * <p>Used for lost-package handover so rack-only delivery flow stays consistent.
   */
  public List<ItemStack> insertIntoRacksOnly(List<ItemStack> stacks) {
    return rackAccess.insertIntoRacksOnly(stacks);
  }

  /**
   * True if at least one item of the given stack can currently be accepted by rack or hut buffer.
   */
  public boolean canAcceptInbound(ItemStack stack) {
    return rackAccess.canAcceptInbound(stack);
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
   * Returns the first rack position and item stack that is not reserved by pending requests, or
   * null if nothing needs to be moved.
   */
  @Nullable
  public Tuple<BlockPos, ItemStack> findNextUnreservedRackItem(
      @Nullable CreateShopBlockEntity pickup) {
    if (pickup == null || getBuilding() == null || getLevel() == null) {
      return null;
    }
    List<RackStackBudget> budgets = collectRackBudgets(pickup);
    if (budgets.isEmpty()) {
      return null;
    }
    for (AbstractTileEntityRack rack : collectRacksForHousekeeping()) {
      if (rack == null) {
        continue;
      }
      IItemHandler handler = rack.getItemHandlerCap();
      if (handler == null) {
        handler = rack.getInventory();
      }
      if (handler == null) {
        continue;
      }
      for (int slot = 0; slot < handler.getSlots(); slot++) {
        ItemStack inSlot = handler.getStackInSlot(slot);
        if (inSlot.isEmpty()) {
          continue;
        }
        RackStackBudget budget = findBudget(budgets, inSlot);
        if (budget == null || budget.remaining <= 0) {
          continue;
        }
        return new Tuple<>(rack.getBlockPos(), inSlot.copy());
      }
    }
    return null;
  }

  /**
   * Extracts one stack of {@code target} item type from the rack at {@code rackPos}, respecting the
   * remaining unreserved budget. Returns the extracted stack, or empty if nothing could be taken.
   */
  public ItemStack extractFromRack(
      BlockPos rackPos, ItemStack target, @Nullable CreateShopBlockEntity pickup) {
    if (rackPos == null || target == null || target.isEmpty() || getLevel() == null) {
      return ItemStack.EMPTY;
    }
    BlockEntity entity = getLevel().getBlockEntity(rackPos);
    if (!(entity instanceof AbstractTileEntityRack rack)) {
      return ItemStack.EMPTY;
    }
    IItemHandler handler = rack.getItemHandlerCap();
    if (handler == null) {
      handler = rack.getInventory();
    }
    if (handler == null) {
      return ItemStack.EMPTY;
    }
    int budget = Integer.MAX_VALUE;
    if (pickup != null) {
      List<RackStackBudget> budgets = collectRackBudgets(pickup);
      RackStackBudget b = findBudget(budgets, target);
      budget = b != null ? b.remaining : 0;
    }
    if (budget <= 0) {
      return ItemStack.EMPTY;
    }
    for (int slot = 0; slot < handler.getSlots(); slot++) {
      ItemStack inSlot = handler.getStackInSlot(slot);
      if (inSlot.isEmpty() || !ItemStack.isSameItemSameComponents(inSlot, target)) {
        continue;
      }
      int toExtract = Math.min(inSlot.getCount(), budget);
      if (toExtract <= 0) {
        continue;
      }
      ItemStack extracted = handler.extractItem(slot, toExtract, false);
      if (!extracted.isEmpty()) {
        setChanged();
        return extracted;
      }
    }
    return ItemStack.EMPTY;
  }

  /** Returns true when the hut-internal inventory contains items awaiting native pickup. */
  public boolean hasHutInventoryItems() {
    IItemHandler hut = getInventory();
    if (hut == null) {
      hut = getItemHandlerCap((Direction) null);
    }
    if (hut == null) {
      return false;
    }
    for (int slot = 0; slot < hut.getSlots(); slot++) {
      if (!hut.getStackInSlot(slot).isEmpty()) {
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
    // Housekeeping target must be hut-internal inventory, not aggregate warehouse views.
    IItemHandler hut = getInventory();
    if (hut == null) {
      hut = getItemHandlerCap((Direction) null);
    }
    if (hut == null) {
      return 0;
    }
    List<RackStackBudget> budgets = collectRackBudgets(pickup);
    if (budgets.isEmpty()) {
      logHousekeepingDebug("skip:budgets-empty");
      return 0;
    }
    List<AbstractTileEntityRack> racks = collectRacksForHousekeeping();
    if (racks.isEmpty()) {
      logHousekeepingDebug("skip:racks-empty");
      return 0;
    }
    if (com.thesettler_x_create.Config.DEBUG_LOGGING.getAsBoolean()) {
      int unreserved = 0;
      for (RackStackBudget budget : budgets) {
        if (budget != null) {
          unreserved += Math.max(0, budget.remaining);
        }
      }
      com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
          "[CreateShop] housekeeping start racks={} stackKeys={} unreserved={}",
          racks.size(),
          budgets.size(),
          unreserved);
    }
    int movedStacks = 0;
    for (AbstractTileEntityRack rack : racks) {
      if (movedStacks >= maxStacks || rack == null) {
        continue;
      }
      IItemHandler handler = rack.getItemHandlerCap();
      if (handler == null) {
        handler = rack.getInventory();
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
        int insertable = ShopRackAccess.simulateInsertCount(hut, inSlot, stackMoveTarget);
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
    logHousekeepingDebug("done:moved=" + movedStacks);
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
    return rackAccess.planInboundAcceptedStacks(requestedStacks);
  }

  private List<RackStackBudget> collectRackBudgets(CreateShopBlockEntity pickup) {
    List<RackStackBudget> totals = new ArrayList<>();
    if (getBuilding() == null || getLevel() == null || pickup == null) {
      return totals;
    }
    for (AbstractTileEntityRack rack : collectRacksForHousekeeping()) {
      IItemHandler handler = rack.getItemHandlerCap();
      if (handler == null) {
        handler = rack.getInventory();
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
    // Perma-items belong to the output block packager — never move them to the hut via cleanup.
    if (getBuilding() instanceof BuildingCreateShop shop && shop.canUsePermaRequests()) {
      java.util.Set<net.minecraft.resources.ResourceLocation> permaOres = shop.getPermaOres();
      if (!permaOres.isEmpty()) {
        totals.removeIf(
            budget ->
                permaOres.contains(
                    net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(
                        budget.key.getItem())));
      }
    }
    return totals;
  }

  private List<AbstractTileEntityRack> collectRacksForHousekeeping() {
    if (getBuilding() == null || getLevel() == null) {
      return new ArrayList<>();
    }
    if (getBuilding() instanceof BuildingCreateShop shop) {
      shop.ensureRackContainers();
    }
    List<AbstractTileEntityRack> racks = new ArrayList<>();
    for (LoadedRack loaded : getLoadedRacks()) {
      racks.add(loaded.rack());
    }
    return racks;
  }

  private void logHousekeepingDebug(String message) {
    if (!com.thesettler_x_create.Config.DEBUG_LOGGING.getAsBoolean()) {
      return;
    }
    com.thesettler_x_create.TheSettlerXCreate.LOGGER.info("[CreateShop] housekeeping {}", message);
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

  /** Warns the colony (chat, cooldown-gated) that a citizen found no matching rack to dump into. */
  void maybeNotifyFull() {
    Level world = getLevel();
    if (world == null) {
      return;
    }
    if (world.getGameTime() - lastNotification
        <= com.thesettler_x_create.Config.RACK_FULL_WARNING_COOLDOWN.getAsLong()) {
      return;
    }
    lastNotification = world.getGameTime();
    if (!com.thesettler_x_create.Config.CHAT_MESSAGES_ENABLED.getAsBoolean()) {
      return;
    }
    if (getBuilding() instanceof BuildingCreateShop shop && shop.getColony() != null) {
      com.minecolonies.api.util.MessageUtils.format(
              "com.thesettler_x_create.message.createshop.rack_full")
          .sendTo(shop.getColony())
          .forAllPlayers();
    }
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
