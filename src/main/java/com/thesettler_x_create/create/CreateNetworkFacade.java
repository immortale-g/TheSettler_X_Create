package com.thesettler_x_create.create;

import com.minecolonies.api.colony.requestsystem.requestable.IDeliverable;
import com.minecolonies.api.colony.requestsystem.requestable.Tool;
import com.minecolonies.api.equipment.registry.EquipmentTypeEntry;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.packager.InventorySummary;
import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBehaviour;
import com.simibubi.create.content.logistics.packagerLink.LogisticsManager;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;
import com.thesettler_x_create.blockentity.CreateShopBlockEntity;
import com.thesettler_x_create.minecolonies.building.BuildingCreateShop;
import com.thesettler_x_create.minecolonies.tileentity.TileEntityCreateShop;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import net.minecraft.world.item.ItemStack;

public class CreateNetworkFacade implements ICreateNetworkFacade {
  private static final int MAX_PACKAGE_COUNT = 99;
  private static final java.util.Map<QueuedRequestKey, QueuedRequestBucket> QUEUED_REQUESTS =
      new java.util.concurrent.ConcurrentHashMap<>();
  private final TileEntityCreateShop shop;
  private long lastPerfLogTime = 0L;
  private long lastSummaryNanos = 0L;
  private long lastBroadcastNanos = 0L;
  private int lastBroadcastCount = 0;

  public CreateNetworkFacade(TileEntityCreateShop shop) {
    this.shop = shop;
  }

  @Override
  public int getAvailable(IDeliverable deliverable) {
    InventorySummary summary = getSummaryWithLogging();
    if (summary == null || summary.isEmpty()) {
      return 0;
    }
    int total = 0;
    for (BigItemStack stack : summary.getStacks()) {
      if (stack == null || stack.stack == null || stack.stack.isEmpty()) {
        continue;
      }
      if (deliverable.matches(stack.stack)) {
        total += stack.count;
      }
    }
    int result = Math.max(0, total);
    if (com.thesettler_x_create.Config.DEBUG_LOGGING.getAsBoolean()) {
      com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
          "[CreateShop] getAvailable={} for {}", result, deliverable);
    }
    return result;
  }

  @Override
  public int getAvailable(ItemStack stack) {
    if (!hasNetwork() || stack == null || stack.isEmpty()) {
      return 0;
    }
    InventorySummary summary = getSummary();
    if (summary == null || summary.isEmpty()) {
      return 0;
    }
    int total = 0;
    for (BigItemStack entry : summary.getStacks()) {
      if (entry == null || entry.stack == null || entry.stack.isEmpty()) {
        continue;
      }
      if (ItemStack.isSameItemSameComponents(stack, entry.stack)) {
        total += entry.count;
      }
    }
    return Math.max(0, total);
  }

  @Override
  public List<ItemStack> getAvailableStacks() {
    if (!hasNetwork()) {
      return Collections.emptyList();
    }
    InventorySummary summary = getSummary();
    if (summary == null || summary.isEmpty()) {
      return Collections.emptyList();
    }
    List<ItemStack> stacks = new ArrayList<>();
    for (BigItemStack entry : summary.getStacks()) {
      if (entry == null || entry.stack == null || entry.stack.isEmpty()) {
        continue;
      }
      ItemStack copy = entry.stack.copy();
      copy.setCount(entry.count);
      stacks.add(copy);
    }
    return stacks;
  }

  @Override
  public ItemStack extract(ItemStack stack, int amount, boolean simulate) {
    if (stack == null || stack.isEmpty() || amount <= 0) {
      return ItemStack.EMPTY;
    }
    // TODO: Replace with Create API call that removes items from the network.
    int available = getAvailable(stack);
    int toExtract = Math.min(amount, available);
    if (toExtract <= 0) {
      return ItemStack.EMPTY;
    }
    ItemStack result = stack.copy();
    result.setCount(toExtract);
    return result;
  }

  @Override
  public List<ItemStack> planItems(IDeliverable deliverable, int amount) {
    if (!hasNetwork() || amount <= 0) {
      return Collections.emptyList();
    }
    InventorySummary summary = getSummary();
    if (summary == null || summary.isEmpty()) {
      return Collections.emptyList();
    }

    int remaining = amount;
    List<ItemStack> orderedStacks = new ArrayList<>();
    List<BigItemStack> candidates = new ArrayList<>();

    for (BigItemStack stack : summary.getStacks()) {
      if (remaining <= 0) {
        break;
      }
      if (stack == null || stack.stack == null || stack.stack.isEmpty()) {
        continue;
      }
      if (!deliverable.matches(stack.stack)) {
        continue;
      }
      candidates.add(stack);
    }

    if (deliverable instanceof Tool tool) {
      candidates.sort(
          Comparator.comparingInt((BigItemStack entry) -> getToolLevel(tool, entry.stack))
              .reversed());
    }

    for (BigItemStack stack : candidates) {
      if (remaining <= 0) {
        break;
      }
      int available = Math.min(remaining, stack.count);
      if (available <= 0) {
        continue;
      }
      int maxPer = Math.max(1, Math.min(MAX_PACKAGE_COUNT, stack.stack.getMaxStackSize()));
      while (available > 0 && remaining > 0) {
        int chunk = Math.min(available, maxPer);
        ItemStack requestStack = stack.stack.copy();
        requestStack.setCount(chunk);
        orderedStacks.add(requestStack);
        available -= chunk;
        remaining -= chunk;
      }
    }

    return orderedStacks;
  }

  @Override
  public List<ItemStack> requestItems(IDeliverable deliverable, int amount, String requesterName) {
    if (!hasNetwork() || amount <= 0) {
      if (com.thesettler_x_create.Config.DEBUG_LOGGING.getAsBoolean()) {
        com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
            "[CreateShop] requestItems skipped (amount={}, shop={}, network={})",
            amount,
            shop != null,
            shop == null ? null : shop.getStockNetworkId());
      }
      return Collections.emptyList();
    }
    InventorySummary summary = getSummary();
    if (summary == null || summary.isEmpty()) {
      if (com.thesettler_x_create.Config.DEBUG_LOGGING.getAsBoolean()) {
        com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
            "[CreateShop] requestItems skipped (summary empty) for {}", shop.getStockNetworkId());
      }
      return Collections.emptyList();
    }

    List<ItemStack> orderedStacks = planItems(deliverable, amount);
    return requestStacks(orderedStacks, requesterName);
  }

  @Override
  public List<ItemStack> requestStacks(List<ItemStack> requestedStacks, String requesterName) {
    List<ItemStack> normalized = normalizeRequestedStacks(requestedStacks);
    if (normalized.isEmpty()) {
      if (com.thesettler_x_create.Config.DEBUG_LOGGING.getAsBoolean()) {
        com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
            "[CreateShop] requestStacks computed empty order");
      }
      return Collections.emptyList();
    }
    queueRequestStacks(normalized, requesterName);
    if (com.thesettler_x_create.Config.DEBUG_LOGGING.getAsBoolean()) {
      com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
          "[CreateShop] queued {} stack(s) for grouped network broadcast {} -> '{}'",
          normalized.size(),
          shop.getStockNetworkId(),
          shop.getShopAddress());
    }

    return normalized;
  }

  public List<ItemStack> requestStacksImmediate(
      List<ItemStack> requestedStacks, String requesterName) {
    List<ItemStack> normalized = normalizeRequestedStacks(requestedStacks);
    if (normalized.isEmpty() || !hasNetwork() || shop == null) {
      return Collections.emptyList();
    }
    QueuedRequestKey key =
        new QueuedRequestKey(
            shop.getStockNetworkId(),
            shop.getShopAddress(),
            requesterName == null ? "" : requesterName);
    return broadcastQueuedRequest(key, normalized) ? normalized : Collections.emptyList();
  }

  public static void flushQueuedRequests() {
    if (QUEUED_REQUESTS.isEmpty()) {
      return;
    }
    var snapshot = new java.util.ArrayList<>(QUEUED_REQUESTS.entrySet());
    QUEUED_REQUESTS.clear();
    for (var entry : snapshot) {
      QueuedRequestKey key = entry.getKey();
      QueuedRequestBucket bucket = entry.getValue();
      if (bucket == null || bucket.facade == null || bucket.stacks.isEmpty()) {
        continue;
      }
      if (!bucket.facade.broadcastQueuedRequest(key, bucket.stacks)) {
        requeueFailedBucket(key, bucket);
      }
    }
  }

  private List<ItemStack> consolidateRequestedStacks(List<ItemStack> requestedStacks) {
    if (requestedStacks == null || requestedStacks.isEmpty()) {
      return Collections.emptyList();
    }
    List<ItemStack> consolidated = new ArrayList<>();
    for (ItemStack requestStack : requestedStacks) {
      if (requestStack == null || requestStack.isEmpty()) {
        continue;
      }
      ItemStack existing = null;
      for (ItemStack candidate : consolidated) {
        if (ItemStack.isSameItemSameComponents(candidate, requestStack)) {
          existing = candidate;
          break;
        }
      }
      if (existing == null) {
        consolidated.add(requestStack.copy());
      } else {
        existing.setCount(existing.getCount() + requestStack.getCount());
      }
    }
    return consolidated;
  }

  private List<ItemStack> normalizeRequestedStacks(List<ItemStack> requestedStacks) {
    if (!hasNetwork() || requestedStacks == null || requestedStacks.isEmpty()) {
      return Collections.emptyList();
    }
    List<ItemStack> consolidated = consolidateRequestedStacks(requestedStacks);
    List<ItemStack> acceptedByCapacity = shop.planInboundAcceptedStacks(consolidated);
    List<ItemStack> normalized = new ArrayList<>();
    for (ItemStack requestStack : acceptedByCapacity) {
      if (requestStack.isEmpty()) {
        continue;
      }
      int available = requestStack.getCount();
      while (available > 0) {
        int chunk = Math.min(available, MAX_PACKAGE_COUNT);
        ItemStack chunkStack = requestStack.copy();
        chunkStack.setCount(chunk);
        normalized.add(chunkStack);
        available -= chunk;
      }
    }
    if (com.thesettler_x_create.Config.DEBUG_LOGGING.getAsBoolean()) {
      for (ItemStack requested : consolidated) {
        int acceptedCount = countAccepted(acceptedByCapacity, requested);
        if (acceptedCount <= 0) {
          com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
              "[CreateShop] requestStacks skipped '{}' x{} (no rack/hut capacity)",
              requested.getHoverName().getString(),
              requested.getCount());
          continue;
        }
        if (acceptedCount < requested.getCount()) {
          com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
              "[CreateShop] requestStacks clamped '{}' requested={} accepted={} (capacity-limited)",
              requested.getHoverName().getString(),
              requested.getCount(),
              acceptedCount);
        }
      }
    }
    return normalized;
  }

  private static int countAccepted(List<ItemStack> accepted, ItemStack requested) {
    if (accepted == null || accepted.isEmpty() || requested == null || requested.isEmpty()) {
      return 0;
    }
    int total = 0;
    for (ItemStack candidate : accepted) {
      if (candidate == null || candidate.isEmpty()) {
        continue;
      }
      if (ItemStack.isSameItemSameComponents(candidate, requested)) {
        total += candidate.getCount();
      }
    }
    return total;
  }

  private void recordInflight(List<ItemStack> orderedStacks, String requesterName) {
    if (orderedStacks == null || orderedStacks.isEmpty()) {
      return;
    }
    if (!(shop.getBuilding() instanceof BuildingCreateShop building)) {
      return;
    }
    CreateShopBlockEntity pickup = building.getPickupBlockEntity();
    if (pickup == null) {
      return;
    }
    var baseline = building.getStockCountsForKeys(orderedStacks);
    pickup.recordInflight(orderedStacks, baseline, requesterName, shop.getShopAddress());
  }

  private int getToolLevel(Tool tool, ItemStack stack) {
    if (tool == null || stack == null || stack.isEmpty()) {
      return 0;
    }
    EquipmentTypeEntry type = tool.getEquipmentType();
    if (type == null) {
      return 0;
    }
    return type.getMiningLevel(stack);
  }

  private boolean hasNetwork() {
    return shop != null && shop.getStockNetworkId() != null;
  }

  private InventorySummary getSummary() {
    if (!hasNetwork()) {
      return null;
    }
    long start = System.nanoTime();
    try {
      return LogisticsManager.getSummaryOfNetwork(shop.getStockNetworkId(), true);
    } catch (Exception ex) {
      if (com.thesettler_x_create.Config.DEBUG_LOGGING.getAsBoolean()) {
        com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
            "[CreateShop] Network summary lookup failed for {}: {}",
            shop.getStockNetworkId(),
            ex.getMessage() == null ? "<null>" : ex.getMessage());
      }
      return null;
    } finally {
      lastSummaryNanos = System.nanoTime() - start;
      maybeLogPerf();
    }
  }

  private InventorySummary getSummaryWithLogging() {
    if (!hasNetwork()) {
      if (com.thesettler_x_create.Config.DEBUG_LOGGING.getAsBoolean()) {
        com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
            "[CreateShop] getAvailable skipped (no shop/network)");
      }
      return null;
    }
    InventorySummary summary = getSummary();
    if (summary == null || summary.isEmpty()) {
      if (com.thesettler_x_create.Config.DEBUG_LOGGING.getAsBoolean()) {
        com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
            "[CreateShop] Network summary empty for {}", shop.getStockNetworkId());
      }
    }
    return summary;
  }

  private void maybeLogPerf() {
    if (!com.thesettler_x_create.Config.DEBUG_LOGGING.getAsBoolean()) {
      return;
    }
    if (shop == null || shop.getLevel() == null) {
      return;
    }
    long now = shop.getLevel().getGameTime();
    if (now != 0L
        && now - lastPerfLogTime < com.thesettler_x_create.Config.PERF_LOG_COOLDOWN.getAsLong()) {
      return;
    }
    lastPerfLogTime = now;
    com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
        "[CreateShop] perf summary: getSummary={}us broadcast={}us items={}",
        lastSummaryNanos / 1000L,
        lastBroadcastNanos / 1000L,
        lastBroadcastCount);
  }

  private void queueRequestStacks(List<ItemStack> stacks, String requesterName) {
    if (stacks == null || stacks.isEmpty() || shop == null || shop.getStockNetworkId() == null) {
      return;
    }
    QueuedRequestKey key =
        new QueuedRequestKey(
            shop.getStockNetworkId(),
            shop.getShopAddress(),
            requesterName == null ? "" : requesterName);
    QueuedRequestBucket bucket =
        QUEUED_REQUESTS.computeIfAbsent(key, k -> new QueuedRequestBucket(this));
    bucket.facade = this;
    for (ItemStack stack : stacks) {
      mergeInto(bucket.stacks, stack);
    }
  }

  private static void requeueFailedBucket(QueuedRequestKey key, QueuedRequestBucket failed) {
    if (key == null || failed == null || failed.facade == null || failed.stacks.isEmpty()) {
      return;
    }
    QueuedRequestBucket target =
        QUEUED_REQUESTS.computeIfAbsent(key, ignored -> new QueuedRequestBucket(failed.facade));
    if (target.facade == null) {
      target.facade = failed.facade;
    }
    for (ItemStack stack : failed.stacks) {
      mergeInto(target.stacks, stack);
    }
  }

  private boolean broadcastQueuedRequest(QueuedRequestKey key, List<ItemStack> stacks) {
    if (key == null
        || stacks == null
        || stacks.isEmpty()
        || shop == null
        || key.networkId == null) {
      return true;
    }
    List<ItemStack> consolidated = consolidateRequestedStacks(stacks);
    if (consolidated.isEmpty()) {
      return true;
    }
    List<BigItemStack> order = new ArrayList<>();
    for (ItemStack requestStack : consolidated) {
      int available = requestStack.getCount();
      while (available > 0) {
        int chunk = Math.min(available, MAX_PACKAGE_COUNT);
        ItemStack chunkStack = requestStack.copy();
        chunkStack.setCount(chunk);
        order.add(new BigItemStack(chunkStack.copy(), chunk));
        available -= chunk;
      }
    }
    if (order.isEmpty()) {
      return true;
    }
    PackageOrderWithCrafts request = PackageOrderWithCrafts.simple(order);
    long start = System.nanoTime();
    try {
      LogisticsManager.broadcastPackageRequest(
          key.networkId,
          LogisticallyLinkedBehaviour.RequestType.PLAYER,
          request,
          null,
          key.address == null ? "" : key.address);
      if (com.thesettler_x_create.Config.DEBUG_LOGGING.getAsBoolean()) {
        com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
            "[CreateShop] broadcast grouped request stacks={} chunks={} network={} address='{}' requester='{}'",
            consolidated.size(),
            order.size(),
            key.networkId,
            key.address,
            key.requesterName);
      }
      recordInflight(consolidated, key.requesterName);
      return true;
    } catch (Exception ex) {
      if (com.thesettler_x_create.Config.DEBUG_LOGGING.getAsBoolean()) {
        com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
            "[CreateShop] grouped request broadcast failed for {}: {}",
            key.networkId,
            ex.getMessage() == null ? "<null>" : ex.getMessage());
      }
      return false;
    } finally {
      lastBroadcastNanos = System.nanoTime() - start;
      lastBroadcastCount = order.size();
      maybeLogPerf();
    }
  }

  private static void mergeInto(List<ItemStack> target, ItemStack stack) {
    if (target == null || stack == null || stack.isEmpty()) {
      return;
    }
    for (ItemStack existing : target) {
      if (ItemStack.isSameItemSameComponents(existing, stack)) {
        existing.setCount(existing.getCount() + stack.getCount());
        return;
      }
    }
    target.add(stack.copy());
  }

  private static final class QueuedRequestBucket {
    private CreateNetworkFacade facade;
    private final List<ItemStack> stacks = new ArrayList<>();

    private QueuedRequestBucket(CreateNetworkFacade facade) {
      this.facade = facade;
    }
  }

  private static final class QueuedRequestKey {
    private final java.util.UUID networkId;
    private final String address;
    private final String requesterName;

    private QueuedRequestKey(java.util.UUID networkId, String address, String requesterName) {
      this.networkId = networkId;
      this.address = address == null ? "" : address;
      this.requesterName = requesterName == null ? "" : requesterName;
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }
      if (!(obj instanceof QueuedRequestKey other)) {
        return false;
      }
      return java.util.Objects.equals(networkId, other.networkId)
          && java.util.Objects.equals(address, other.address)
          && java.util.Objects.equals(requesterName, other.requesterName);
    }

    @Override
    public int hashCode() {
      return java.util.Objects.hash(networkId, address, requesterName);
    }
  }
}
