package com.thesettler_x_create.minecolonies.building;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.workerbuildings.IWareHouse;
import com.minecolonies.api.colony.requestsystem.manager.IRequestManager;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.requestable.Stack;
import com.minecolonies.api.colony.requestsystem.requester.IRequester;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.api.tileentities.AbstractTileEntityWareHouse;
import com.thesettler_x_create.Config;
import com.thesettler_x_create.TheSettlerXCreate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Manages Create Shop perma requests (ore auto-requests). */
final class ShopPermaRequestManager {
  private final BuildingCreateShop shop;
  private final Set<ResourceLocation> permaOres = new HashSet<>();
  private boolean permaWaitFullStack;
  private long lastPermaRequestTick;
  private final Map<IToken<?>, PendingPermaRequest> permaPendingRequests = new HashMap<>();
  private final Map<ResourceLocation, Integer> permaPendingCounts = new HashMap<>();

  ShopPermaRequestManager(BuildingCreateShop shop) {
    this.shop = shop;
    this.permaWaitFullStack = false;
    this.lastPermaRequestTick = 0L;
  }

  boolean isPermaWaitFullStack() {
    return permaWaitFullStack;
  }

  Set<ResourceLocation> getPermaOres() {
    return java.util.Collections.unmodifiableSet(permaOres);
  }

  void setPermaWaitFullStack(boolean enabled) {
    permaWaitFullStack = enabled;
    setDirty();
  }

  void setPermaOre(ResourceLocation itemId, boolean enabled) {
    if (itemId == null) {
      return;
    }
    if (enabled) {
      permaOres.add(itemId);
    } else {
      permaOres.remove(itemId);
    }
    setDirty();
  }

  void tickPermaRequests(IColony colony) {
    if (colony == null
        || permaOres.isEmpty()
        || !shop.canUsePermaRequests()
        || !shop.isWorkerWorking()) {
      if (BuildingCreateShop.isDebugRequests()) {
        TheSettlerXCreate.LOGGER.info(
            "[CreateShop] perma tick skipped: colony={} permaOres={} canUse={}",
            colony == null ? "<null>" : colony.getID(),
            permaOres.size(),
            shop.canUsePermaRequests());
      }
      return;
    }
    Level level = colony.getWorld();
    if (level == null || level.isClientSide) {
      if (BuildingCreateShop.isDebugRequests()) {
        TheSettlerXCreate.LOGGER.info("[CreateShop] perma tick skipped: level={}", level);
      }
      return;
    }
    long now = level.getGameTime();
    if (now - lastPermaRequestTick < Config.PERMA_REQUEST_INTERVAL_TICKS.getAsLong()) {
      if (BuildingCreateShop.isDebugRequests()) {
        TheSettlerXCreate.LOGGER.info(
            "[CreateShop] perma tick throttled: now={} last={} diff={}",
            now,
            lastPermaRequestTick,
            now - lastPermaRequestTick);
      }
      return;
    }
    lastPermaRequestTick = now;

    IRequestManager manager = colony.getRequestManager();
    if (manager == null) {
      if (BuildingCreateShop.isDebugRequests()) {
        TheSettlerXCreate.LOGGER.info("[CreateShop] perma tick skipped: request manager null");
      }
      return;
    }
    IRequester requester = shop.getRequester();
    if (requester == null) {
      if (BuildingCreateShop.isDebugRequests()) {
        TheSettlerXCreate.LOGGER.info("[CreateShop] perma tick skipped: requester null");
      }
      return;
    }

    List<ResourceLocation> ordered = new ArrayList<>(permaOres);
    ordered.sort(Comparator.comparing(ResourceLocation::toString));

    for (ResourceLocation itemId : ordered) {
      Item item = BuiltInRegistries.ITEM.get(itemId);
      if (item == null || item == net.minecraft.world.item.Items.AIR) {
        if (BuildingCreateShop.isDebugRequests()) {
          TheSettlerXCreate.LOGGER.info("[CreateShop] perma skip: missing item {}", itemId);
        }
        continue;
      }
      ItemStack stack = new ItemStack(item, 1);
      int available = countInWarehouses(stack);
      int pending = permaPendingCounts.getOrDefault(itemId, 0);
      int requestable = Math.max(0, available - pending);
      if (BuildingCreateShop.isDebugRequests()) {
        TheSettlerXCreate.LOGGER.info(
            "[CreateShop] perma eval item={} available={} pending={} requestable={} waitFull={}",
            itemId,
            available,
            pending,
            requestable,
            permaWaitFullStack);
      }
      if (requestable <= 0) {
        continue;
      }
      int maxStack = Math.max(1, stack.getMaxStackSize());
      int amount = permaWaitFullStack ? (requestable / maxStack) * maxStack : requestable;
      if (amount <= 0) {
        continue;
      }
      Stack deliverable = new Stack(stack, amount, 1);
      IToken<?> token = manager.createRequest(requester, deliverable);
      if (token != null) {
        permaPendingRequests.put(token, new PendingPermaRequest(itemId, amount));
        permaPendingCounts.merge(itemId, amount, Integer::sum);
        if (BuildingCreateShop.isDebugRequests()) {
          TheSettlerXCreate.LOGGER.info(
              "[CreateShop] perma request created token={} item={} amount={}",
              token,
              itemId,
              amount);
        }
      }
    }
  }

  void clearPermaPending(IRequest<?> request) {
    if (request == null || request.getId() == null) {
      return;
    }
    PendingPermaRequest pending = permaPendingRequests.remove(request.getId());
    if (pending == null) {
      return;
    }
    permaPendingCounts.merge(pending.itemId, -pending.count, Integer::sum);
    if (permaPendingCounts.getOrDefault(pending.itemId, 0) <= 0) {
      permaPendingCounts.remove(pending.itemId);
    }
  }

  void loadPerma(CompoundTag compound) {
    permaOres.clear();
    if (compound.contains(BuildingCreateShop.TAG_PERMA_ORES)) {
      var list =
          compound.getList(BuildingCreateShop.TAG_PERMA_ORES, net.minecraft.nbt.Tag.TAG_STRING);
      for (int i = 0; i < list.size(); i++) {
        String key = list.getString(i);
        if (key == null || key.isBlank()) {
          continue;
        }
        ResourceLocation id = ResourceLocation.tryParse(key);
        if (id != null) {
          permaOres.add(id);
        }
      }
    }
    permaWaitFullStack = compound.getBoolean(BuildingCreateShop.TAG_PERMA_WAIT_FULL);
  }

  void savePerma(CompoundTag tag) {
    if (!permaOres.isEmpty()) {
      net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
      for (ResourceLocation id : permaOres) {
        list.add(net.minecraft.nbt.StringTag.valueOf(id.toString()));
      }
      tag.put(BuildingCreateShop.TAG_PERMA_ORES, list);
    }
    if (permaWaitFullStack) {
      tag.putBoolean(BuildingCreateShop.TAG_PERMA_WAIT_FULL, true);
    }
  }

  private void setDirty() {
    if (shop.getColony() != null) {
      shop.getColony().markDirty();
    }
  }

  private int countInWarehouses(ItemStack stack) {
    if (stack == null || stack.isEmpty() || shop.getColony() == null) {
      return 0;
    }
    var manager = shop.getColony().getServerBuildingManager();
    if (manager == null) {
      return 0;
    }
    List<IWareHouse> warehouses = manager.getWareHouses();
    if (warehouses == null || warehouses.isEmpty()) {
      return 0;
    }
    int total = 0;
    for (IWareHouse warehouse : warehouses) {
      if (warehouse == null || warehouse == shop) {
        continue;
      }
      if (!(warehouse.getTileEntity() instanceof AbstractTileEntityWareHouse wareHouse)) {
        continue;
      }
      for (var entry :
          wareHouse.getMatchingItemStacksInWarehouse(match -> matchesStack(match, stack))) {
        ItemStack found = entry.getA();
        if (found == null || found.isEmpty()) {
          continue;
        }
        total += found.getCount();
      }
    }
    return Math.max(0, total);
  }

  private boolean matchesStack(ItemStack candidate, ItemStack target) {
    if (candidate == null || target == null) {
      return false;
    }
    return ItemStack.isSameItemSameComponents(candidate, target);
  }

  private static final class PendingPermaRequest {
    private final ResourceLocation itemId;
    private final int count;

    private PendingPermaRequest(ResourceLocation itemId, int count) {
      this.itemId = itemId;
      this.count = count;
    }
  }
}
