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
    int minLevel = Config.gaugeMinBuildingLevel();
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
    // A gauge asks again once its promise runs out, which happens long before a slow delivery
    // arrives. Anything still waiting to be packaged for this item and address is that earlier
    // ask, so report its amount back instead of placing a second colony request: two requests
    // would draw the warehouse twice and hold twice as much in the shop for one gauge slot.
    //
    // This comes before the two scans below on purpose. With a promise lifetime set on the gauge,
    // the promise expires again and again while the task waits for rack stock, and every one of
    // those asks would otherwise walk every warehouse and every crafting module first, only to
    // land here.
    GaugePackagingTask queued = findOpenTask(item, gaugeAddress);
    if (queued != null) {
      if (DebugLog.enabled()) {
        TheSettlerXCreate.LOGGER.info(
            "[ColonyGauge] requestForGauge skip reason=already-requested item={} amount={} openAmount={} address={}",
            item.getItem(),
            amount,
            queued.amount(),
            gaugeAddress);
      }
      return queued.amount();
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
    }
    // Same filter the tokens above were picked with, minus what stays running. Matching the address
    // alone would drop the queued task of a second panel that asked for a different item through
    // the same frogport, whose request is still open and whose goods nobody would package.
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

  /** Returns the first gauge packaging task without removing it, or null if queue is empty. */
  @Nullable
  GaugePackagingTask peekNextGaugeTask() {
    return gaugePackagingQueue.isEmpty() ? null : gaugePackagingQueue.get(0);
  }

  /**
   * Every task waiting to be packaged, oldest first.
   *
   * <p>The shop serves whichever of them the racks can cover, not simply the first. A task is
   * queued when its colony request is placed, long before its goods arrive, so the order of the
   * queue is the order of asking. Serving only its head meant one order waiting on a crafter held
   * back every order behind it, including ones whose goods a courier had already brought in.
   */
  List<GaugePackagingTask> getGaugeTasks() {
    return List.copyOf(gaugePackagingQueue);
  }

  /**
   * Books {@code packaged} items of the task {@code requestId} belongs to as sent, and answers how
   * much of it is still owed afterwards, or {@code -1} when no such task is queued.
   *
   * <p>A gauge order is filled in parts, the way Create fills one: what the racks hold travels now
   * and the rest follows, instead of the whole order waiting for the last item. The task keeps what
   * is still owed, and only a task with nothing left is removed.
   *
   * <p>The task is addressed by its request rather than by its place in the queue, because the shop
   * serves the task whose goods are there and that is not always the first one.
   *
   * <p>What the task still owes shrinks by the same amount, which is what a pickup leaves standing
   * in the hut buffer; goods nothing owes anymore are free again.
   */
  int deliverPartOfGaugeTask(UUID requestId, int packaged) {
    if (packaged <= 0 || requestId == null) {
      return -1;
    }
    int index = indexOfTask(requestId);
    if (index < 0) {
      return -1;
    }
    GaugePackagingTask task = gaugePackagingQueue.get(index);
    int sent = Math.min(packaged, task.amount());
    int open = task.amount() - sent;
    if (open <= 0) {
      gaugePackagingQueue.remove(index);
    } else {
      gaugePackagingQueue.set(
          index, new GaugePackagingTask(task.item(), open, task.gaugeAddress(), task.requestId()));
    }
    owner.markDirty();
    DebugLog.info(
        "[ColonyGauge] packaged {} of {} item={} address={} stillOpen={}",
        sent,
        task.amount(),
        task.item().getItem(),
        task.gaugeAddress(),
        open);
    return Math.max(0, open);
  }

  /** Where the task belonging to {@code requestId} sits in the queue, or {@code -1}. */
  private int indexOfTask(UUID requestId) {
    for (int i = 0; i < gaugePackagingQueue.size(); i++) {
      if (gaugePackagingQueue.get(i).requestId().equals(requestId)) {
        return i;
      }
    }
    return -1;
  }

  /**
   * How much of one item kind the shop still owes its gauges: the open amount of every queued
   * packaging task the matcher accepts.
   *
   * <p>This is what used to be a pickup reservation per gauge order. Those reservations sat in the
   * same ledger the Create requests use, where every reader had to know that some of them stood for
   * goods that are not rack stock at all. The amount is read straight off the queue instead, and
   * the two readers that need it say so themselves: a warehouse pickup leaves that much standing,
   * and the resolver does not count it as rack stock it may hand out.
   *
   * <p>Only the queue is walked. A task enters it when its order is placed and leaves it when it is
   * packaged, so a task tracked in {@code pendingGaugeRequests} is in the queue as well, and adding
   * both would count it twice.
   */
  int owedToGaugeTasks(java.util.function.Predicate<ItemStack> matches) {
    if (matches == null) {
      return 0;
    }
    int owed = 0;
    for (GaugePackagingTask task : gaugePackagingQueue) {
      ItemStack item = task.item();
      if (item.isEmpty() || !matches.test(item)) {
        continue;
      }
      owed += Math.max(0, task.amount());
    }
    return owed;
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
      owner.markDirty();
    }
  }

  /**
   * Cleans up gauge tracking when a request this shop placed for a Gauge completes, and shrinks its
   * task to what the colony actually handed over.
   *
   * <p>An order can close short of what was asked. The non-craftable branch asks with a minimum of
   * 1 on purpose, so a warehouse someone else drained in between still gives up what is left, and
   * MineColonies closes such a request as soon as it delivered that minimum (see {@code
   * AbstractWarehouseRequestResolver}, which stops making child requests once {@code totalAvailable
   * >= getMinimumCount()}). Nothing further will arrive for it.
   *
   * <p>A task left at its original amount would then wait for goods nobody owes it: it would stay
   * in the queue, keep that much standing in the shop, and the gauge behind it would keep its
   * promise for the missing rest and never ask again. Shrinking it here is what lets the last
   * package of the order say that the order is closed.
   */
  void onRequestComplete(@Nullable IRequest<?> request) {
    if (request == null) {
      return;
    }
    GaugePackagingTask task = pendingGaugeRequests.remove(request.getId());
    if (task == null) {
      return;
    }
    shrinkTaskToDelivered(task, deliveredAmount(request, task.item()));
  }

  /**
   * How much of {@code item} the completed {@code request} actually delivered.
   *
   * <p>Every resolver that hands goods over records them on the request, the warehouse one with the
   * exact count it could spare. A request that records nothing tells us nothing, which is answered
   * with {@code -1} rather than with zero: a task is only shrunk on a number that was really read.
   */
  private static int deliveredAmount(IRequest<?> request, ItemStack item) {
    List<ItemStack> deliveries = request.getDeliveries();
    if (deliveries == null || deliveries.isEmpty()) {
      return -1;
    }
    int delivered = 0;
    for (ItemStack stack : deliveries) {
      if (!stack.isEmpty() && ItemStack.isSameItem(stack, item)) {
        delivered += stack.getCount();
      }
    }
    return delivered;
  }

  /**
   * Cuts the queued task down to {@code delivered}, or drops it when the order brought nothing.
   * Leaves a task alone when the request did not say what it delivered, or when it covered the
   * whole amount.
   */
  private void shrinkTaskToDelivered(GaugePackagingTask task, int delivered) {
    if (delivered < 0) {
      return;
    }
    int index = indexOfTask(task.requestId());
    if (index < 0) {
      return;
    }
    GaugePackagingTask queued = gaugePackagingQueue.get(index);
    if (delivered >= queued.amount()) {
      return;
    }
    if (delivered <= 0) {
      gaugePackagingQueue.remove(index);
    } else {
      gaugePackagingQueue.set(
          index,
          new GaugePackagingTask(
              queued.item(), delivered, queued.gaugeAddress(), queued.requestId()));
    }
    owner.markDirty();
    DebugLog.info(
        "[ColonyGauge] order closed short item={} asked={} delivered={} address={}",
        queued.item().getItem(),
        queued.amount(),
        delivered,
        queued.gaugeAddress());
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
