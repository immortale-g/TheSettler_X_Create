package com.thesettler_x_create.blockentity;

import com.minecolonies.api.colony.requestsystem.requestable.IDeliverable;
import com.thesettler_x_create.Config;
import com.thesettler_x_create.TheSettlerXCreate;
import com.thesettler_x_create.create.VirtualCreateNetworkItemHandler;
import com.thesettler_x_create.init.ModBlockEntities;
import com.thesettler_x_create.minecolonies.tileentity.TileEntityCreateShop;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CreateShopBlockEntity extends BlockEntity {
  private static final String TAG_SHOP_POS = "ShopPos";
  private static final String TAG_RESERVATIONS = "Reservations";
  private static final String TAG_INFLIGHT = "Inflight";
  private static final String TAG_INFLIGHT_BASELINES = "InflightBaselines";
  private static final long RESERVATION_TTL = 20L * 60L * 5L;

  private final IItemHandler itemHandler = new VirtualCreateNetworkItemHandler(this);
  private final Map<UUID, Reservation> reservations = new HashMap<>();
  private final List<InflightEntry> inflightEntries = new ArrayList<>();
  private final List<BaselineEntry> inflightBaselines = new ArrayList<>();
  private BlockPos shopPos;
  private long lastInflightLogTime;

  public CreateShopBlockEntity(BlockPos pos, BlockState state) {
    super(ModBlockEntities.CREATE_SHOP_PICKUP.get(), pos, state);
  }

  public void setShopPos(BlockPos pos) {
    shopPos = pos;
    setChanged();
  }

  @Nullable
  public BlockPos getShopPos() {
    return shopPos;
  }

  @Nullable
  public TileEntityCreateShop getShopTile() {
    if (level == null || shopPos == null) {
      return null;
    }
    BlockEntity be = level.getBlockEntity(shopPos);
    if (be instanceof TileEntityCreateShop shop) {
      return shop;
    }
    return null;
  }

  public void reserve(UUID requestId, ItemStack key, int amount) {
    if (amount <= 0) {
      return;
    }
    cleanExpired();
    Reservation reservation = reservations.get(requestId);
    if (reservation == null) {
      reservations.put(
          requestId, new Reservation(requestId, makeKey(key), amount, getExpireTime()));
    } else {
      reservation.stackKey = makeKey(key);
      reservation.reservedAmount += amount;
      reservation.expiresAtGameTime = getExpireTime();
    }
    if (Config.DEBUG_LOGGING.getAsBoolean()) {
      TheSettlerXCreate.LOGGER.info(
          "[CreateShop] Reserved {}x {} for {}", amount, key.getHoverName().getString(), requestId);
    }
    setChanged();
  }

  public void release(UUID requestId) {
    cleanExpired();
    if (reservations.remove(requestId) != null) {
      setChanged();
    }
  }

  public int getReservedFor(ItemStack key) {
    cleanExpired();
    int total = 0;
    for (Reservation reservation : reservations.values()) {
      if (matches(reservation.stackKey, key)) {
        total += reservation.reservedAmount;
      }
    }
    return total;
  }

  public int getReservedForDeliverable(IDeliverable deliverable) {
    if (deliverable == null) {
      return 0;
    }
    cleanExpired();
    int total = 0;
    for (Reservation reservation : reservations.values()) {
      if (deliverable.matches(reservation.stackKey)) {
        total += reservation.reservedAmount;
      }
    }
    return total;
  }

  public int getReservedForRequest(UUID requestId) {
    if (requestId == null) {
      return 0;
    }
    cleanExpired();
    int total = 0;
    for (Reservation reservation : reservations.values()) {
      if (requestId.equals(reservation.requestId)) {
        total += reservation.reservedAmount;
      }
    }
    return total;
  }

  public int consumeReservedForRequest(UUID requestId, ItemStack key, int amount) {
    if (requestId == null || key == null || key.isEmpty() || amount <= 0) {
      return 0;
    }
    cleanExpired();
    Reservation reservation = reservations.get(requestId);
    if (reservation == null || !matches(reservation.stackKey, key)) {
      return 0;
    }
    int taken = Math.min(amount, reservation.reservedAmount);
    reservation.reservedAmount -= taken;
    if (reservation.reservedAmount <= 0) {
      reservations.remove(requestId);
    }
    if (taken > 0) {
      setChanged();
    }
    return taken;
  }

  public List<ItemStack> getInflightKeys() {
    List<ItemStack> keys = new ArrayList<>();
    for (InflightEntry entry : inflightEntries) {
      if (entry.stackKey == null || entry.stackKey.isEmpty()) {
        continue;
      }
      if (!containsKey(keys, entry.stackKey)) {
        keys.add(entry.stackKey.copy());
      }
    }
    return keys;
  }

  public void recordInflight(List<ItemStack> stacks, Map<ItemStack, Integer> baselines) {
    if (stacks == null || stacks.isEmpty()) {
      return;
    }
    long now = getGameTimeSafe();
    boolean changed = false;
    for (ItemStack stack : stacks) {
      if (stack == null || stack.isEmpty() || stack.getCount() <= 0) {
        continue;
      }
      ItemStack key = makeKey(stack);
      int baseline = findCount(baselines, key);
      upsertBaseline(key, baseline);
      inflightEntries.add(new InflightEntry(key, stack.getCount(), now));
      changed = true;
    }
    if (changed) {
      setChanged();
    }
  }

  public void reconcileInflight(Map<ItemStack, Integer> currentCounts) {
    if (inflightEntries.isEmpty()) {
      return;
    }
    ensureBaselines(currentCounts);
    long now = getGameTimeSafe();
    boolean changed = false;
    for (BaselineEntry baseline : inflightBaselines) {
      int current = findCount(currentCounts, baseline.stackKey);
      int delta = Math.max(0, current - baseline.count);
      if (baseline.count != current) {
        baseline.count = current;
        changed = true;
      }
      if (delta <= 0) {
        continue;
      }
      int remaining = delta;
      Iterator<InflightEntry> iterator = inflightEntries.iterator();
      while (iterator.hasNext() && remaining > 0) {
        InflightEntry entry = iterator.next();
        if (!matches(entry.stackKey, baseline.stackKey)) {
          continue;
        }
        int applied = Math.min(remaining, entry.remaining);
        entry.remaining -= applied;
        remaining -= applied;
        if (entry.remaining <= 0) {
          iterator.remove();
          changed = true;
        } else if (applied > 0) {
          changed = true;
        }
      }
    }
    if (pruneBaselines()) {
      changed = true;
    }
    if (shouldLogOverdue(now)) {
      logOverdue(now);
    }
    if (changed) {
      setChanged();
    }
  }

  //    public int consumeReserved(ItemStack key, int amount) {
  //        if (amount <= 0) {
  //            return 0;
  //        }
  //        cleanExpired();
  //        int remaining = amount;
  //        Iterator<Map.Entry<UUID, Reservation>> iterator = reservations.entrySet().iterator();
  //        while (iterator.hasNext() && remaining > 0) {
  //            Reservation reservation = iterator.next().getValue();
  //            if (!matches(reservation.stackKey, key)) {
  //                continue;
  //            }
  //            int taken = Math.min(remaining, reservation.reservedAmount);
  //            reservation.reservedAmount -= taken;
  //            remaining -= taken;
  //            if (reservation.reservedAmount <= 0) {
  //                iterator.remove();
  //            }
  //        }
  //        if (remaining != amount) {
  //            setChanged();
  //        }
  //        return amount - remaining;
  //    }

  public java.util.List<ItemStack> getReservedStacksSnapshot() {
    cleanExpired();
    java.util.List<ItemStack> stacks = new java.util.ArrayList<>();
    for (Reservation reservation : reservations.values()) {
      if (reservation.stackKey == null || reservation.stackKey.isEmpty()) {
        continue;
      }
      ItemStack stack = reservation.stackKey.copy();
      stack.setCount(Math.max(1, reservation.reservedAmount));
      stacks.add(stack);
    }
    return stacks;
  }

  private void cleanExpired() {
    long now = getGameTimeSafe();
    Iterator<Map.Entry<UUID, Reservation>> iterator = reservations.entrySet().iterator();
    while (iterator.hasNext()) {
      Reservation reservation = iterator.next().getValue();
      if (reservation.expiresAtGameTime <= now) {
        iterator.remove();
      }
    }
  }

  private void ensureBaselines(Map<ItemStack, Integer> currentCounts) {
    for (InflightEntry entry : inflightEntries) {
      if (entry.stackKey == null || entry.stackKey.isEmpty()) {
        continue;
      }
      if (findBaseline(entry.stackKey) == null) {
        int current = findCount(currentCounts, entry.stackKey);
        inflightBaselines.add(new BaselineEntry(entry.stackKey.copy(), current));
      }
    }
  }

  private boolean pruneBaselines() {
    boolean changed = false;
    Iterator<BaselineEntry> iterator = inflightBaselines.iterator();
    while (iterator.hasNext()) {
      BaselineEntry baseline = iterator.next();
      if (!hasInflightFor(baseline.stackKey)) {
        iterator.remove();
        changed = true;
      }
    }
    return changed;
  }

  private boolean hasInflightFor(ItemStack key) {
    for (InflightEntry entry : inflightEntries) {
      if (matches(entry.stackKey, key)) {
        return true;
      }
    }
    return false;
  }

  private BaselineEntry findBaseline(ItemStack key) {
    for (BaselineEntry baseline : inflightBaselines) {
      if (matches(baseline.stackKey, key)) {
        return baseline;
      }
    }
    return null;
  }

  private void upsertBaseline(ItemStack key, int count) {
    BaselineEntry existing = findBaseline(key);
    if (existing != null) {
      existing.count = count;
      return;
    }
    inflightBaselines.add(new BaselineEntry(key.copy(), count));
  }

  private boolean shouldLogOverdue(long now) {
    if (!Config.DEBUG_LOGGING.getAsBoolean()) {
      return false;
    }
    if (now == 0L) {
      return false;
    }
    return now - lastInflightLogTime >= Config.INFLIGHT_LOG_COOLDOWN.getAsLong();
  }

  private void logOverdue(long now) {
    long timeout = Config.INFLIGHT_TIMEOUT_TICKS.getAsLong();
    if (timeout <= 0L) {
      return;
    }
    List<String> entries = new ArrayList<>();
    for (InflightEntry entry : inflightEntries) {
      if (entry.remaining <= 0) {
        continue;
      }
      long age = now - entry.requestedAt;
      if (age < timeout) {
        continue;
      }
      String label =
          entry.stackKey.getHoverName().getString() + " x" + entry.remaining + " age=" + age;
      entries.add(label);
    }
    if (entries.isEmpty()) {
      return;
    }
    lastInflightLogTime = now;
    TheSettlerXCreate.LOGGER.info(
        "[CreateShop] inflight overdue: {}", String.join(" | ", entries));
  }

  private long getExpireTime() {
    return getGameTimeSafe() + RESERVATION_TTL;
  }

  private long getGameTimeSafe() {
    return level == null ? 0L : level.getGameTime();
  }

  private static boolean matches(ItemStack a, ItemStack b) {
    return ItemStack.isSameItemSameComponents(a, b);
  }

  private static boolean containsKey(List<ItemStack> keys, ItemStack key) {
    for (ItemStack existing : keys) {
      if (matches(existing, key)) {
        return true;
      }
    }
    return false;
  }

  private static int findCount(Map<ItemStack, Integer> counts, ItemStack key) {
    if (counts == null || counts.isEmpty() || key == null || key.isEmpty()) {
      return 0;
    }
    for (Map.Entry<ItemStack, Integer> entry : counts.entrySet()) {
      if (matches(entry.getKey(), key)) {
        return entry.getValue();
      }
    }
    return 0;
  }

  private static ItemStack makeKey(ItemStack stack) {
    ItemStack copy = stack.copy();
    copy.setCount(1);
    return copy;
  }

  @Override
  public void loadAdditional(
      @NotNull CompoundTag tag, @NotNull net.minecraft.core.HolderLookup.Provider registries) {
    super.loadAdditional(tag, registries);
    if (tag.contains(TAG_SHOP_POS)) {
      shopPos = BlockPos.of(tag.getLong(TAG_SHOP_POS));
    }
    reservations.clear();
    if (tag.contains(TAG_RESERVATIONS)) {
      CompoundTag resTag = tag.getCompound(TAG_RESERVATIONS);
      for (String key : resTag.getAllKeys()) {
        CompoundTag entry = resTag.getCompound(key);
        try {
          UUID id = UUID.fromString(key);
          ItemStack stack =
              ItemStack.parse(registries, entry.getCompound("stack")).orElse(ItemStack.EMPTY);
          int amount = entry.getInt("amount");
          long expires = entry.getLong("expires");
          if (!stack.isEmpty() && amount > 0) {
            reservations.put(id, new Reservation(id, stack, amount, expires));
          }
        } catch (IllegalArgumentException ignored) {
          // Ignore malformed reservation keys.
        }
      }
    }
    inflightEntries.clear();
    if (tag.contains(TAG_INFLIGHT)) {
      var list = tag.getList(TAG_INFLIGHT, net.minecraft.nbt.Tag.TAG_COMPOUND);
      for (int i = 0; i < list.size(); i++) {
        CompoundTag entry = list.getCompound(i);
        ItemStack stack =
            ItemStack.parse(registries, entry.getCompound("stack")).orElse(ItemStack.EMPTY);
        int remaining = entry.getInt("remaining");
        long requestedAt = entry.getLong("requestedAt");
        if (!stack.isEmpty() && remaining > 0) {
          inflightEntries.add(new InflightEntry(makeKey(stack), remaining, requestedAt));
        }
      }
    }
    inflightBaselines.clear();
    if (tag.contains(TAG_INFLIGHT_BASELINES)) {
      var list = tag.getList(TAG_INFLIGHT_BASELINES, net.minecraft.nbt.Tag.TAG_COMPOUND);
      for (int i = 0; i < list.size(); i++) {
        CompoundTag entry = list.getCompound(i);
        ItemStack stack =
            ItemStack.parse(registries, entry.getCompound("stack")).orElse(ItemStack.EMPTY);
        int count = entry.getInt("count");
        if (!stack.isEmpty()) {
          inflightBaselines.add(new BaselineEntry(makeKey(stack), Math.max(0, count)));
        }
      }
    }
  }

  @Override
  public void saveAdditional(
      @NotNull CompoundTag tag, @NotNull net.minecraft.core.HolderLookup.Provider registries) {
    super.saveAdditional(tag, registries);
    if (shopPos != null) {
      tag.putLong(TAG_SHOP_POS, shopPos.asLong());
    }
    CompoundTag resTag = new CompoundTag();
    for (Map.Entry<UUID, Reservation> entry : reservations.entrySet()) {
      Reservation reservation = entry.getValue();
      CompoundTag data = new CompoundTag();
      data.put("stack", reservation.stackKey.save(registries));
      data.putInt("amount", reservation.reservedAmount);
      data.putLong("expires", reservation.expiresAtGameTime);
      resTag.put(entry.getKey().toString(), data);
    }
    tag.put(TAG_RESERVATIONS, resTag);
    if (!inflightEntries.isEmpty()) {
      net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
      for (InflightEntry entry : inflightEntries) {
        CompoundTag data = new CompoundTag();
        data.put("stack", entry.stackKey.save(registries));
        data.putInt("remaining", entry.remaining);
        data.putLong("requestedAt", entry.requestedAt);
        list.add(data);
      }
      tag.put(TAG_INFLIGHT, list);
    }
    if (!inflightBaselines.isEmpty()) {
      net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
      for (BaselineEntry entry : inflightBaselines) {
        CompoundTag data = new CompoundTag();
        data.put("stack", entry.stackKey.save(registries));
        data.putInt("count", entry.count);
        list.add(data);
      }
      tag.put(TAG_INFLIGHT_BASELINES, list);
    }
  }

  @SuppressWarnings("unused")
  public IItemHandler getItemHandler(@Nullable Direction side) {
    return itemHandler;
  }

  public static class Reservation {
    public final UUID requestId;
    public ItemStack stackKey;
    public int reservedAmount;
    public long expiresAtGameTime;

    public Reservation(
        UUID requestId, ItemStack stackKey, int reservedAmount, long expiresAtGameTime) {
      this.requestId = requestId;
      this.stackKey = stackKey;
      this.reservedAmount = reservedAmount;
      this.expiresAtGameTime = expiresAtGameTime;
    }
  }

  public static class InflightEntry {
    public final ItemStack stackKey;
    public int remaining;
    public final long requestedAt;

    public InflightEntry(ItemStack stackKey, int remaining, long requestedAt) {
      this.stackKey = stackKey;
      this.remaining = remaining;
      this.requestedAt = requestedAt;
    }
  }

  public static class BaselineEntry {
    public final ItemStack stackKey;
    public int count;

    public BaselineEntry(ItemStack stackKey, int count) {
      this.stackKey = stackKey;
      this.count = count;
    }
  }
}
