package com.thesettler_x_create.minecolonies.building;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.request.RequestState;
import com.minecolonies.api.colony.requestsystem.requestable.Stack;
import com.minecolonies.api.colony.requestsystem.requester.IRequester;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.core.colony.requestsystem.management.IStandardRequestManager;
import com.thesettler_x_create.Config;
import com.thesettler_x_create.DebugLog;
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
      DebugLog.info(
          "[ColonyGauge] requestForGauge skip reason=invalid-args item={} amount={}", item, amount);
      return 0;
    }
    int minLevel = Config.permaMinBuildingLevel();
    if (owner.getBuildingLevel() < minLevel) {
      if (DebugLog.enabled()) {
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
      if (DebugLog.enabled()) {
        TheSettlerXCreate.LOGGER.info(
            "[ColonyGauge] requestForGauge skip reason=no-colony item={}", item.getItem());
      }
      return 0;
    }
    IRequester requester = owner.getRequester();
    if (requester == null) {
      if (DebugLog.enabled()) {
        TheSettlerXCreate.LOGGER.info(
            "[ColonyGauge] requestForGauge skip reason=no-requester item={}", item.getItem());
      }
      return 0;
    }
    if (!owner.isWorkerWorking()) {
      if (DebugLog.enabled()) {
        TheSettlerXCreate.LOGGER.info(
            "[ColonyGauge] requestForGauge skip reason=worker-not-working item={}", item.getItem());
      }
      return 0;
    }
    // The gauge pulls from the colony, not from Create's stock network; vanilla Create Factory
    // Gauges already cover that case. So only ask for what the colony has a chance of providing:
    // stock in a warehouse, or a crafter who knows the recipe. Without either, an order would walk
    // the whole resolver chain and end up in the player's request list.
    int available = ShopWarehouseStockUtil.countInWarehouses(owner, item);
    boolean craftable = available < amount && ShopColonyCraftingUtil.canAnyoneCraft(colony, item);
    if (available <= 0 && !craftable) {
      if (DebugLog.enabled()) {
        TheSettlerXCreate.LOGGER.info(
            "[ColonyGauge] requestForGauge skip reason=colony-cannot-provide item={} requested={}",
            item.getItem(),
            amount);
      }
      return 0;
    }
    // Ask for everything that is wanted, and say the whole amount is the minimum. MineColonies
    // answers a warehouse that cannot cover that with a child request for the rest, which reaches
    // the crafters; asking only for what a warehouse holds would never get there. What a crafter
    // cannot make either comes back as a shortfall, not as a stuck order.
    int actualAmount = craftable ? amount : Math.min(amount, available);

    // A gauge asks again once its promise runs out, which happens long before a slow delivery
    // arrives. Anything still waiting to be packaged for this item and address is that earlier
    // ask, so report its amount back instead of placing a second colony request: two requests
    // would draw the warehouse twice and reserve rack stock twice for one gauge slot.
    GaugePackagingTask queued = findOpenTask(item, gaugeAddress);
    if (queued != null) {
      if (DebugLog.enabled()) {
        TheSettlerXCreate.LOGGER.info(
            "[ColonyGauge] requestForGauge skip reason=already-requested item={} amount={} openAmount={} address={}",
            item.getItem(),
            actualAmount,
            queued.amount(),
            gaugeAddress);
      }
      return queued.amount();
    }

    IStandardRequestManager manager = (IStandardRequestManager) colony.getRequestManager();
    // The full amount as the minimum is what makes a warehouse that cannot cover it hand the rest
    // to the crafters, so it belongs on the craftable branch alone. On the other branch the amount
    // is what a warehouse held a moment ago: asking for that much at minimum would turn a handful
    // of units drawn by someone else in between into a child request nobody can craft, where
    // before the gauge simply took what was left.
    int minimumCount = craftable ? actualAmount : 1;
    Stack deliverable = new Stack(item.copyWithCount(1), actualAmount, minimumCount);
    IToken<?> token = manager.createAndAssignRequest(requester, deliverable);
    if (token != null) {
      UUID requestId = toRequestId(token);
      GaugePackagingTask task =
          new GaugePackagingTask(item.copy(), actualAmount, gaugeAddress, requestId);
      gaugePackagingQueue.add(task);
      owner.markDirty();
      pendingGaugeRequests.put(token, task);
      // Protect the delivered item from rack housekeeping (which sweeps "unreserved" rack stock
      // back to the warehouse) until CreateShopOutputBlockEntity actually packages it.
      CreateShopBlockEntity pickup = owner.getPickupBlockEntity();
      if (pickup != null) {
        pickup.reserve(requestId, item.copy(), actualAmount);
      }
      if (DebugLog.enabled()) {
        TheSettlerXCreate.LOGGER.info(
            "[ColonyGauge] request created token={} item={} amount={} available={} address={}",
            token,
            item.getItem(),
            actualAmount,
            available,
            gaugeAddress);
      }
    } else if (DebugLog.enabled()) {
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
    return sweep(item, gaugeAddress, false).cancelled();
  }

  /**
   * Cancels only what nothing is happening on yet. A request a courier has already been assigned to
   * is left alone: taking it away mid-walk wastes the trip, and a gauge whose promise lifetime is
   * shorter than a courier's round trip would otherwise cancel and re-place an order every time the
   * lifetime runs out, forever.
   *
   * @return how many requests were left running
   */
  int cancelStalledGaugeRequests(ItemStack item, String gaugeAddress) {
    return sweep(item, gaugeAddress, true).keptRunning();
  }

  /** What one sweep over the tracked gauge requests did. */
  record GaugeSweepResult(int cancelled, int keptRunning) {}

  private GaugeSweepResult sweep(ItemStack item, String gaugeAddress, boolean keepRunning) {
    if (gaugeAddress == null || gaugeAddress.isBlank() || owner.getColony() == null) {
      return new GaugeSweepResult(0, 0);
    }
    if (!(owner.getColony().getRequestManager() instanceof IStandardRequestManager standard)) {
      return new GaugeSweepResult(0, 0);
    }
    Set<IToken<?>> toCancel = new LinkedHashSet<>();
    Set<UUID> keptRequestIds = new LinkedHashSet<>();
    for (var entry : pendingGaugeRequests.entrySet()) {
      GaugePackagingTask task = entry.getValue();
      if (!task.gaugeAddress().equals(gaugeAddress)
          || !(item == null || item.isEmpty() || ItemStack.isSameItem(task.item(), item))) {
        continue;
      }
      if (keepRunning && isBeingWorkedOn(standard, entry.getKey())) {
        keptRequestIds.add(task.requestId());
        continue;
      }
      toCancel.add(entry.getKey());
    }
    CreateShopBlockEntity pickup = owner.getPickupBlockEntity();
    int cancelled = 0;
    for (IToken<?> token : toCancel) {
      try {
        standard.updateRequestState(token, RequestState.CANCELLED);
        cancelled++;
      } catch (Exception ex) {
        if (DebugLog.enabled()) {
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
    // Same filter the tokens above were picked with, minus what stays running. Matching the address
    // alone would drop the queued task of a second panel that asked for a different item through
    // the same frogport, whose request is still open and whose reservation nobody would release.
    gaugePackagingQueue.removeIf(
        t ->
            t.gaugeAddress().equals(gaugeAddress)
                && (item == null || item.isEmpty() || ItemStack.isSameItem(t.item(), item))
                && !keptRequestIds.contains(t.requestId()));
    if (cancelled > 0) {
      owner.markDirty();
      DebugLog.info(
          "[ColonyGauge] cancelPendingGaugeRequests address={} cancelled={} keptRunning={}",
          gaugeAddress,
          cancelled,
          keptRequestIds.size());
    }
    return new GaugeSweepResult(cancelled, keptRequestIds.size());
  }

  /**
   * Whether MineColonies has put someone on this request already, either by state or by having
   * handed out a delivery child for it.
   */
  private static boolean isBeingWorkedOn(IStandardRequestManager standard, IToken<?> token) {
    try {
      IRequest<?> request = standard.getRequestHandler().getRequest(token);
      if (request == null) {
        return false;
      }
      if (request.hasChildren()) {
        return true;
      }
      RequestState state = request.getState();
      return state == RequestState.ASSIGNED
          || state == RequestState.IN_PROGRESS
          || state == RequestState.FOLLOWUP_IN_PROGRESS;
    } catch (Exception ignored) {
      // A token whose request cannot be read is not being worked on by anyone.
      return false;
    }
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

  /**
   * Books {@code packaged} items of the next task as sent. A gauge order is filled in parts, the
   * way Create fills one: what the racks hold travels now and the rest follows, instead of the
   * whole order waiting for the last item. The task keeps what is still owed, and only a task with
   * nothing left is removed.
   *
   * <p>The reservation shrinks by the same amount, so rack housekeeping may move on whatever is no
   * longer spoken for, and the rest stays protected until it is packaged too.
   */
  void deliverPartOfNextGaugeTask(int packaged) {
    if (packaged <= 0 || gaugePackagingQueue.isEmpty()) {
      return;
    }
    GaugePackagingTask task = gaugePackagingQueue.get(0);
    int sent = Math.min(packaged, task.amount());
    CreateShopBlockEntity pickup = owner.getPickupBlockEntity();
    int open = task.amount() - sent;
    if (open <= 0) {
      gaugePackagingQueue.remove(0);
      if (pickup != null) {
        pickup.release(task.requestId());
      }
    } else {
      gaugePackagingQueue.set(
          0, new GaugePackagingTask(task.item(), open, task.gaugeAddress(), task.requestId()));
      if (pickup != null) {
        pickup.consumeReservedForRequest(task.requestId(), task.item(), sent);
      }
    }
    owner.markDirty();
    DebugLog.info(
        "[ColonyGauge] packaged {} of {} item={} address={} stillOpen={}",
        sent,
        task.amount(),
        task.item().getItem(),
        task.gaugeAddress(),
        open);
  }

  /** Request ids whose pickup reservation must stay until the gauge task is packaged. */
  java.util.Set<java.util.UUID> getGaugeReservationRequestIds() {
    java.util.Set<java.util.UUID> ids = new java.util.HashSet<>();
    for (GaugePackagingTask task : gaugePackagingQueue) {
      ids.add(task.requestId());
    }
    for (GaugePackagingTask task : pendingGaugeRequests.values()) {
      ids.add(task.requestId());
    }
    return ids;
  }

  /**
   * Operator reset: forgets the gauge requests and the packaging queue. The colony requests stay
   * open in MineColonies; goods they still deliver are no longer packaged, and the gauges request
   * again once their promise runs out.
   *
   * @return number of tracked gauge requests and queued packaging tasks
   */
  int clear() {
    int cleared = pendingGaugeRequests.size() + gaugePackagingQueue.size();
    if (cleared > 0) {
      pendingGaugeRequests.clear();
      gaugePackagingQueue.clear();
      owner.markDirty();
    }
    return cleared;
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
    if (request == null) {
      return;
    }
    GaugePackagingTask task = pendingGaugeRequests.remove(request.getId());
    if (task == null) {
      return;
    }
    // The reservation protects the delivered goods from rack housekeeping until they are packaged,
    // so it stays while the task is still queued; completeNextGaugeTask releases it then. Without a
    // queued task nobody would release it anymore and it would sit there until its TTL runs out.
    if (gaugePackagingQueue.stream().noneMatch(t -> t.requestId().equals(task.requestId()))) {
      CreateShopBlockEntity pickup = owner.getPickupBlockEntity();
      if (pickup != null) {
        pickup.release(task.requestId());
      }
    }
  }

  /**
   * The gauge task for this item and address that is still waiting for its goods, or {@code null}
   * when nothing is open for it.
   */
  @Nullable
  private GaugePackagingTask findOpenTask(ItemStack item, String gaugeAddress) {
    for (GaugePackagingTask task : gaugePackagingQueue) {
      if (ItemStack.isSameItem(task.item(), item) && task.gaugeAddress().equals(gaugeAddress)) {
        return task;
      }
    }
    for (GaugePackagingTask task : pendingGaugeRequests.values()) {
      if (ItemStack.isSameItem(task.item(), item) && task.gaugeAddress().equals(gaugeAddress)) {
        return task;
      }
    }
    return null;
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
          if (DebugLog.enabled()) {
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
