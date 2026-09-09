package com.thesettler_x_create.minecolonies.building;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.request.RequestState;
import com.minecolonies.api.colony.requestsystem.requestable.Stack;
import com.minecolonies.api.colony.requestsystem.requester.IRequester;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.core.colony.requestsystem.management.IStandardRequestManager;
import com.thesettler_x_create.Config;
import com.thesettler_x_create.TheSettlerXCreate;
import com.thesettler_x_create.blockentity.CreateShopBlockEntity;
import com.thesettler_x_create.minecolonies.building.BuildingCreateShop.GaugePackagingTask;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Owns the Colony Factory Gauge packaging pipeline for a {@link BuildingCreateShop}: the colony
 * request(s) placed on a Gauge's behalf, and the queue of items waiting to be extracted from racks
 * and packaged for delivery to a Gauge's address. Extracted from {@code BuildingCreateShop} (which
 * held this directly until the pre-1.0 hardening pass) to keep that class's size manageable.
 */
final class ShopGaugeQueue {
  private final BuildingCreateShop owner;

  /**
   * Persisted: maps pending colony-request token → gauge task (for cancellation cleanup). Kept in
   * NBT (see {@code PendingGaugeRequests}) so {@link #cancelPendingGaugeRequests(ItemStack,
   * String)} can always resolve which address a token belongs to, even after a world/server
   * restart, without falling back to an item-only scan that can't distinguish between two gauges
   * requesting the same item.
   */
  private final Map<IToken<?>, GaugePackagingTask> pendingGaugeRequests = new LinkedHashMap<>();

  /** Persisted: gauge packaging tasks waiting for items to arrive in racks. */
  private final List<GaugePackagingTask> gaugePackagingQueue = new java.util.ArrayList<>();

  ShopGaugeQueue(BuildingCreateShop owner) {
    this.owner = owner;
  }

  /**
   * Attempts to request {@code amount} of {@code item} for a Gauge, clamped to what the Colony
   * Warehouse actually holds (partial deliveries are allowed). Returns the amount actually
   * requested, or 0 if no request was created — the caller must use this returned amount (not the
   * requested {@code amount}) for "promised" UI display, since it can be smaller.
   */
  int requestForGauge(ItemStack item, int amount, String gaugeAddress) {
    if (item.isEmpty() || amount <= 0) {
      if (BuildingCreateShop.isDebugRequests()) {
        TheSettlerXCreate.LOGGER.info(
            "[ColonyGauge] requestForGauge skip reason=invalid-args item={} amount={}",
            item,
            amount);
      }
      return 0;
    }
    int minLevel = Config.PERMA_MIN_BUILDING_LEVEL.get();
    if (owner.getBuildingLevel() < minLevel) {
      if (BuildingCreateShop.isDebugRequests()) {
        TheSettlerXCreate.LOGGER.info(
            "[ColonyGauge] requestForGauge skip reason=building-level-too-low item={} level={} required={}",
            item.getItem(),
            owner.getBuildingLevel(),
            minLevel);
      }
      return 0;
    }
    IColony colony = owner.getColony();
    if (colony == null) {
      if (BuildingCreateShop.isDebugRequests()) {
        TheSettlerXCreate.LOGGER.info(
            "[ColonyGauge] requestForGauge skip reason=no-colony item={}", item.getItem());
      }
      return 0;
    }
    IRequester requester = owner.getRequester();
    if (requester == null) {
      if (BuildingCreateShop.isDebugRequests()) {
        TheSettlerXCreate.LOGGER.info(
            "[ColonyGauge] requestForGauge skip reason=no-requester item={}", item.getItem());
      }
      return 0;
    }
    if (!owner.isWorkerWorking()) {
      if (BuildingCreateShop.isDebugRequests()) {
        TheSettlerXCreate.LOGGER.info(
            "[ColonyGauge] requestForGauge skip reason=worker-not-working item={}", item.getItem());
      }
      return 0;
    }
    // Only place a colony request once we've confirmed the Colony Warehouse actually has the item
    // — same check the perma-request system already uses
    // (ShopPermaRequestManager.countInWarehouses).
    // This is the whole point of the Gauge: pull from the Colony Warehouse, not Create's stock
    // network (vanilla Create Factory Gauges already cover that case).
    int available = ShopPermaRequestManager.countInWarehouses(owner, item);
    if (available <= 0) {
      if (BuildingCreateShop.isDebugRequests()) {
        TheSettlerXCreate.LOGGER.info(
            "[ColonyGauge] requestForGauge skip reason=nothing-in-warehouse item={} requested={}",
            item.getItem(),
            amount);
      }
      return 0;
    }
    int actualAmount = Math.min(amount, available);

    IStandardRequestManager manager = (IStandardRequestManager) colony.getRequestManager();
    Stack deliverable = new Stack(item.copyWithCount(1), actualAmount, 1);
    IToken<?> token = manager.createAndAssignRequest(requester, deliverable);
    if (token != null) {
      UUID requestId = toRequestId(token);
      GaugePackagingTask task =
          new GaugePackagingTask(item.copy(), actualAmount, gaugeAddress, requestId);
      // Queue for packaging (deduplicated by item+address to avoid double-queuing on re-request).
      boolean alreadyQueued =
          gaugePackagingQueue.stream()
              .anyMatch(
                  t ->
                      ItemStack.isSameItem(t.item(), item)
                          && t.gaugeAddress().equals(gaugeAddress));
      if (!alreadyQueued) {
        gaugePackagingQueue.add(task);
        owner.markDirty();
      }
      pendingGaugeRequests.put(token, task);
      // Protect the delivered item from rack housekeeping (which sweeps "unreserved" rack stock
      // back to the warehouse) until CreateShopOutputBlockEntity actually packages it.
      CreateShopBlockEntity pickup = owner.getPickupBlockEntity();
      if (pickup != null) {
        pickup.reserve(requestId, item.copy(), actualAmount);
      }
      if (BuildingCreateShop.isDebugRequests()) {
        TheSettlerXCreate.LOGGER.info(
            "[ColonyGauge] request created token={} item={} amount={} available={} address={} queued={}",
            token,
            item.getItem(),
            actualAmount,
            available,
            gaugeAddress,
            !alreadyQueued);
      }
    } else if (BuildingCreateShop.isDebugRequests()) {
      TheSettlerXCreate.LOGGER.info(
          "[ColonyGauge] requestForGauge skip reason=createAndAssignRequest-returned-null item={} amount={}",
          item.getItem(),
          actualAmount);
    }
    return token != null ? actualAmount : 0;
  }

  /**
   * Cancels any still-open colony request(s) for the given Gauge item/address — called when a
   * Gauge's promise is cleared or its filter is reset, so a request left unresolved (e.g. no
   * courier assigned to the warehouse) doesn't keep piling up as a duplicate on the next request
   * attempt. Matches the NBT-persisted {@code pendingGaugeRequests} tracking by address (with
   * {@code item} as an extra safety filter) — deliberately does not fall back to a live, item-only
   * scan of this shop's other open requests, since two different Gauges requesting the same item
   * from the same shop would then be indistinguishable and cancelling one could cancel the other's
   * still-wanted request too.
   */
  int cancelPendingGaugeRequests(ItemStack item, String gaugeAddress) {
    if (gaugeAddress == null || gaugeAddress.isBlank() || owner.getColony() == null) {
      return 0;
    }
    if (!(owner.getColony().getRequestManager() instanceof IStandardRequestManager standard)) {
      return 0;
    }
    Set<IToken<?>> toCancel = new LinkedHashSet<>();
    for (var entry : pendingGaugeRequests.entrySet()) {
      GaugePackagingTask task = entry.getValue();
      if (task.gaugeAddress().equals(gaugeAddress)
          && (item == null || item.isEmpty() || ItemStack.isSameItem(task.item(), item))) {
        toCancel.add(entry.getKey());
      }
    }
    CreateShopBlockEntity pickup = owner.getPickupBlockEntity();
    int cancelled = 0;
    for (IToken<?> token : toCancel) {
      try {
        standard.updateRequestState(token, RequestState.CANCELLED);
        cancelled++;
      } catch (Exception ex) {
        if (BuildingCreateShop.isDebugRequests()) {
          TheSettlerXCreate.LOGGER.info(
              "[ColonyGauge] cancelPendingGaugeRequests failed token={} error={}",
              token,
              ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
        }
      }
      pendingGaugeRequests.remove(token);
      if (pickup != null) {
        pickup.release(toRequestId(token));
      }
    }
    gaugePackagingQueue.removeIf(t -> t.gaugeAddress().equals(gaugeAddress));
    if (cancelled > 0) {
      owner.markDirty();
      if (BuildingCreateShop.isDebugRequests()) {
        TheSettlerXCreate.LOGGER.info(
            "[ColonyGauge] cancelPendingGaugeRequests address={} cancelled={}",
            gaugeAddress,
            cancelled);
      }
    }
    return cancelled;
  }

  /**
   * Tokens of colony requests this shop currently has open as a requester on behalf of a Colony
   * Factory Gauge (delivery-to-shop, not the shop resolving a customer request) — surfaced in the
   * shop's task UI, which otherwise only shows requests where the shop is the resolver.
   */
  List<IToken<?>> getPendingGaugeRequestTokens() {
    return List.copyOf(pendingGaugeRequests.keySet());
  }

  /** Returns the next gauge packaging task without removing it, or null if queue is empty. */
  @Nullable
  GaugePackagingTask peekNextGaugeTask() {
    return gaugePackagingQueue.isEmpty() ? null : gaugePackagingQueue.get(0);
  }

  /** Removes and returns the next gauge packaging task (call after successfully packaging). */
  void completeNextGaugeTask() {
    if (!gaugePackagingQueue.isEmpty()) {
      GaugePackagingTask completed = gaugePackagingQueue.remove(0);
      CreateShopBlockEntity pickup = owner.getPickupBlockEntity();
      if (pickup != null) {
        pickup.release(completed.requestId());
      }
      owner.markDirty();
    }
  }

  boolean hasGaugeTask() {
    return !gaugePackagingQueue.isEmpty();
  }

  /** Cleans up gauge tracking when a request this shop placed for a Gauge is cancelled. */
  void onRequestCancelled(@Nullable IRequest<?> request) {
    if (request == null) {
      return;
    }
    GaugePackagingTask task = pendingGaugeRequests.remove(request.getId());
    if (task != null) {
      gaugePackagingQueue.removeIf(t -> t.requestId().equals(task.requestId()));
      CreateShopBlockEntity pickup = owner.getPickupBlockEntity();
      if (pickup != null) {
        pickup.release(task.requestId());
      }
      owner.markDirty();
    }
  }

  /** Cleans up gauge tracking when a request this shop placed for a Gauge completes. */
  void onRequestComplete(@Nullable IRequest<?> request) {
    if (request != null) {
      pendingGaugeRequests.remove(request.getId());
    }
  }

  void load(net.minecraft.core.HolderLookup.Provider provider, CompoundTag compound) {
    gaugePackagingQueue.clear();
    if (compound.contains("GaugePackagingQueue", 9)) {
      ListTag list = compound.getList("GaugePackagingQueue", 10);
      for (int i = 0; i < list.size(); i++) {
        CompoundTag t = list.getCompound(i);
        ItemStack item = ItemStack.parseOptional(provider, t.getCompound("Item"));
        int amount = t.getInt("Amount");
        String address = t.getString("Address");
        if (!item.isEmpty() && amount > 0 && !address.isEmpty()) {
          UUID requestId =
              t.contains("RequestId")
                  ? UUID.fromString(t.getString("RequestId"))
                  : UUID.randomUUID();
          gaugePackagingQueue.add(new GaugePackagingTask(item, amount, address, requestId));
        }
      }
    }
    pendingGaugeRequests.clear();
    if (compound.contains("PendingGaugeRequests", 9)) {
      ListTag list = compound.getList("PendingGaugeRequests", 10);
      var factoryController =
          com.minecolonies.api.colony.requestsystem.StandardFactoryController.getInstance();
      for (int i = 0; i < list.size(); i++) {
        CompoundTag t = list.getCompound(i);
        try {
          IToken<?> token = factoryController.deserializeTag(provider, t.getCompound("Token"));
          ItemStack item = ItemStack.parseOptional(provider, t.getCompound("Item"));
          int amount = t.getInt("Amount");
          String address = t.getString("Address");
          if (token != null && !item.isEmpty() && amount > 0 && !address.isEmpty()) {
            UUID requestId =
                t.contains("RequestId")
                    ? UUID.fromString(t.getString("RequestId"))
                    : UUID.randomUUID();
            pendingGaugeRequests.put(
                token, new GaugePackagingTask(item, amount, address, requestId));
          }
        } catch (Exception ex) {
          if (BuildingCreateShop.isDebugRequests()) {
            TheSettlerXCreate.LOGGER.info(
                "[ColonyGauge] failed to restore pendingGaugeRequests entry {}: {}",
                i,
                ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
          }
        }
      }
    }
  }

  void save(net.minecraft.core.HolderLookup.Provider provider, CompoundTag tag) {
    if (!gaugePackagingQueue.isEmpty()) {
      ListTag list = new ListTag();
      for (GaugePackagingTask task : gaugePackagingQueue) {
        CompoundTag t = new CompoundTag();
        t.put("Item", task.item().save(provider));
        t.putInt("Amount", task.amount());
        t.putString("Address", task.gaugeAddress());
        t.putString("RequestId", task.requestId().toString());
        list.add(t);
      }
      tag.put("GaugePackagingQueue", list);
    }
    if (!pendingGaugeRequests.isEmpty()) {
      ListTag list = new ListTag();
      var factoryController =
          com.minecolonies.api.colony.requestsystem.StandardFactoryController.getInstance();
      for (var entry : pendingGaugeRequests.entrySet()) {
        GaugePackagingTask task = entry.getValue();
        CompoundTag t = new CompoundTag();
        t.put("Token", factoryController.serializeTag(provider, entry.getKey()));
        t.put("Item", task.item().save(provider));
        t.putInt("Amount", task.amount());
        t.putString("Address", task.gaugeAddress());
        t.putString("RequestId", task.requestId().toString());
        list.add(t);
      }
      tag.put("PendingGaugeRequests", list);
    }
  }

  private static UUID toRequestId(IToken<?> token) {
    Object id = token == null ? null : token.getIdentifier();
    if (id instanceof UUID uuid) {
      return uuid;
    }
    return UUID.nameUUIDFromBytes(String.valueOf(id).getBytes(StandardCharsets.UTF_8));
  }
}
