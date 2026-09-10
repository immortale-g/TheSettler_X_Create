package com.thesettler_x_create.create;

import com.minecolonies.api.tileentities.AbstractTileEntityRack;
import com.minecolonies.api.util.WorldUtil;
import com.thesettler_x_create.Config;
import com.thesettler_x_create.ItemStackDataUtil;
import com.thesettler_x_create.TheSettlerXCreate;
import com.thesettler_x_create.blockentity.CreateShopBlockEntity;
import com.thesettler_x_create.minecolonies.tileentity.TileEntityCreateShop;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * Read-mostly {@link IItemHandler} view of everything currently sitting in this shop's racks, so
 * Create's automation (Stock Ticker, hoppers, etc.) can see and pull from rack contents through the
 * same interface it uses for any other inventory. It is a snapshot, not a live index: {@code
 * cachedStacks} is rebuilt from a fresh rack scan every {@link #CACHE_TTL_TICKS}, and a slot index
 * only identifies "the Nth distinct item as of the last refresh" - it is not a stable handle to a
 * particular item across refreshes. A caller that reads {@link #getStackInSlot(int)} to decide what
 * to extract and then calls {@link #extractItem(int, int, boolean)} with that same index some time
 * later (rather than in the same synchronous step) can therefore end up extracting a different item
 * than the one it saw, if a refresh reordered the list in between - the same well-known caveat
 * every dynamic/virtual slot-index IItemHandler has (no different from a chest's contents changing
 * between two ticks). The list is kept in a deterministic order (by registry name, not rack-scan
 * order) so a slot's meaning stays stable across refreshes for as long as the same set of distinct
 * items remains present, minimizing - though not eliminating - the practical window for this.
 */
public class VirtualCreateNetworkItemHandler implements IItemHandler {
  private static final int MAX_DISPLAY = 64;
  private static final long CACHE_TTL_TICKS = 20L;

  private final CreateShopBlockEntity shopBlockEntity;
  private List<ItemStack> cachedStacks = new ArrayList<>();
  private long lastRefreshTime;

  public VirtualCreateNetworkItemHandler(CreateShopBlockEntity shopBlockEntity) {
    this.shopBlockEntity = shopBlockEntity;
    this.lastRefreshTime = -CACHE_TTL_TICKS;
  }

  @Override
  public int getSlots() {
    refreshCacheIfNeeded();
    return cachedStacks.size();
  }

  @Override
  public ItemStack getStackInSlot(int slot) {
    refreshCacheIfNeeded();
    if (slot < 0 || slot >= cachedStacks.size()) {
      return ItemStack.EMPTY;
    }
    ItemStack base = cachedStacks.get(slot);
    ItemStack display = base.copy();
    int count = Math.min(MAX_DISPLAY, Math.max(1, base.getCount()));
    display.setCount(count);
    return display;
  }

  @Override
  public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
    // Optional: push into Create network. Not needed for delivery flow.
    return stack;
  }

  @Override
  public ItemStack extractItem(int slot, int amount, boolean simulate) {
    refreshCacheIfNeeded();
    if (amount <= 0 || slot < 0 || slot >= cachedStacks.size()) {
      return ItemStack.EMPTY;
    }

    ItemStack key = cachedStacks.get(slot);
    if (key.isEmpty()) {
      return ItemStack.EMPTY;
    }

    int available = getAvailableFromRacks(key);
    if (available <= 0) {
      if (Config.DEBUG_LOGGING.getAsBoolean()) {
        TheSettlerXCreate.LOGGER.info(
            "[CreateShop] extractItem failed (no rack stock) item={} reserved={} available={} simulate={}",
            key.getHoverName().getString(),
            shopBlockEntity.getReservedFor(key),
            available,
            simulate);
      }
      return ItemStack.EMPTY;
    }

    int reserved = shopBlockEntity.getReservedFor(key);
    int extractable = reserved > 0 ? Math.min(amount, reserved) : Math.min(amount, available);
    if (extractable <= 0) {
      if (Config.DEBUG_LOGGING.getAsBoolean()) {
        TheSettlerXCreate.LOGGER.info(
            "[CreateShop] extractItem failed (not extractable) item={} reserved={} available={} request={} simulate={}",
            key.getHoverName().getString(),
            reserved,
            available,
            amount,
            simulate);
      }
      return ItemStack.EMPTY;
    }

    ItemStack extracted = tryExtractFromRacks(key, extractable, simulate);
    if (extracted.isEmpty()) {
      if (Config.DEBUG_LOGGING.getAsBoolean()) {
        TheSettlerXCreate.LOGGER.info(
            "[CreateShop] extractItem failed (rack extract empty) item={} reserved={} available={} request={} simulate={}",
            key.getHoverName().getString(),
            reserved,
            available,
            extractable,
            simulate);
      }
      return ItemStack.EMPTY;
    }

    // Keep reservations until the parent request completes (or TTL expires) to avoid re-requests
    // mid-delivery.

    if (Config.DEBUG_LOGGING.getAsBoolean()) {
      TheSettlerXCreate.LOGGER.info(
          "[CreateShop] extractItem {}x {} (simulate={})",
          extracted.getCount(),
          extracted.getHoverName().getString(),
          simulate);
    }

    return extracted;
  }

  @Override
  public int getSlotLimit(int slot) {
    return MAX_DISPLAY;
  }

  @Override
  public boolean isItemValid(int slot, ItemStack stack) {
    return false;
  }

  private void refreshCacheIfNeeded() {
    if (shopBlockEntity.getLevel() == null) {
      return;
    }
    long now = shopBlockEntity.getLevel().getGameTime();
    if (now - lastRefreshTime < CACHE_TTL_TICKS) {
      return;
    }
    lastRefreshTime = now;

    cachedStacks = getAvailableStacksFromRacks();
    mergeReservedStacks(shopBlockEntity.getReservedStacksSnapshot());
    // Deterministic order (not rack-scan order) so a slot keeps meaning the same item across
    // refreshes for as long as the same set of distinct items is present - see class Javadoc.
    cachedStacks.sort(
        Comparator.comparing(stack -> BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()));
  }

  private void mergeReservedStacks(List<ItemStack> reservedStacks) {
    if (reservedStacks == null || reservedStacks.isEmpty()) {
      return;
    }
    for (ItemStack reserved : reservedStacks) {
      ItemStackDataUtil.mergeIntoList(cachedStacks, reserved);
    }
  }

  /** Visits one rack slot during {@link #scanRacks}. Return {@code true} to stop the scan early. */
  @FunctionalInterface
  private interface RackSlotVisitor {
    boolean visit(IItemHandler handler, int slot, ItemStack stack);
  }

  /**
   * Shared traversal for every loaded rack in this shop's containers, visiting every occupied slot
   * of every rack in order until either the containers are exhausted or {@code visitor} asks to
   * stop early.
   */
  private void scanRacks(RackSlotVisitor visitor) {
    var shop = getReadyShop();
    if (shop == null) {
      return;
    }
    for (BlockPos pos : shop.getBuilding().getContainers()) {
      if (!WorldUtil.isBlockLoaded(shop.getLevel(), pos)) {
        continue;
      }
      BlockEntity entity = shop.getLevel().getBlockEntity(pos);
      if (!(entity instanceof AbstractTileEntityRack rack)) {
        continue;
      }
      IItemHandler handler = rack.getItemHandlerCap();
      if (handler == null) {
        continue;
      }
      for (int slot = 0; slot < handler.getSlots(); slot++) {
        if (visitor.visit(handler, slot, handler.getStackInSlot(slot))) {
          return;
        }
      }
    }
  }

  private ItemStack tryExtractFromRacks(ItemStack key, int amount, boolean simulate) {
    int[] remaining = {amount};
    ItemStack[] extracted = {ItemStack.EMPTY};
    scanRacks(
        (handler, slot, stack) -> {
          if (stack.isEmpty() || !ItemStack.isSameItemSameComponents(stack, key)) {
            return false;
          }
          int toTake = Math.min(remaining[0], stack.getCount());
          ItemStack taken = handler.extractItem(slot, toTake, simulate);
          if (!taken.isEmpty()) {
            if (extracted[0].isEmpty()) {
              extracted[0] = taken.copy();
            } else {
              extracted[0].grow(taken.getCount());
            }
            remaining[0] -= taken.getCount();
          }
          return remaining[0] <= 0;
        });
    return extracted[0];
  }

  private int getAvailableFromRacks(ItemStack key) {
    int[] total = {0};
    scanRacks(
        (handler, slot, stack) -> {
          if (!stack.isEmpty() && ItemStack.isSameItemSameComponents(stack, key)) {
            total[0] += stack.getCount();
          }
          return false;
        });
    return Math.max(0, total[0]);
  }

  private List<ItemStack> getAvailableStacksFromRacks() {
    List<ItemStack> stacks = new ArrayList<>();
    scanRacks(
        (handler, slot, stack) -> {
          if (!stack.isEmpty()) {
            ItemStackDataUtil.mergeIntoList(stacks, stack);
          }
          return false;
        });
    return stacks;
  }

  private TileEntityCreateShop getReadyShop() {
    var shop = shopBlockEntity.getShopTile();
    if (shop == null || shop.getBuilding() == null || shop.getLevel() == null) {
      return null;
    }
    return shop;
  }
}
