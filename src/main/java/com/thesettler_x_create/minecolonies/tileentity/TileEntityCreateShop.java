package com.thesettler_x_create.minecolonies.tileentity;

import com.minecolonies.api.inventory.InventoryCitizen;
import com.minecolonies.api.tileentities.AbstractTileEntityRack;
import com.minecolonies.api.tileentities.AbstractTileEntityWareHouse;
import com.minecolonies.api.util.InventoryUtils;
import com.minecolonies.api.util.ItemStackUtils;
import com.minecolonies.api.util.Tuple;
import com.minecolonies.api.util.WorldUtil;
import com.minecolonies.core.tileentities.TileEntityRack;
import com.thesettler_x_create.init.ModBlockEntities;
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
  private UUID stockNetworkId;
  private String shopAddress = "";
  private long lastNotification;

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
}
