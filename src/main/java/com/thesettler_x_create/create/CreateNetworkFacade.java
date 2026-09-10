package com.thesettler_x_create.create;

import com.minecolonies.api.colony.requestsystem.requestable.IDeliverable;
import com.minecolonies.api.colony.requestsystem.requestable.Tool;
import com.minecolonies.api.equipment.registry.EquipmentTypeEntry;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.packager.InventorySummary;
import com.simibubi.create.content.logistics.packagerLink.LogisticsManager;
import com.thesettler_x_create.ItemStackDataUtil;
import com.thesettler_x_create.blockentity.CreateShopBlockEntity;
import com.thesettler_x_create.minecolonies.building.BuildingCreateShop;
import com.thesettler_x_create.minecolonies.tileentity.TileEntityCreateShop;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public class CreateNetworkFacade implements ICreateNetworkFacade {
  private static final int MAX_PACKAGE_COUNT = 99;
  private final TileEntityCreateShop shop;
  // Owned by the shop, not this facade - the facade is constructed fresh at nearly every call
  // site, so a logger living here would never carry its cooldown state past a single call.
  private final CreateNetworkPerfLogger perfLogger;

  public CreateNetworkFacade(TileEntityCreateShop shop) {
    this.shop = shop;
    this.perfLogger = shop != null ? shop.getPerfLogger() : new CreateNetworkPerfLogger();
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
    // This facade only previews extractable stock; actual movement is requested via
    // LogisticsManager.broadcastPackageRequest in requestStacks.
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
    return planItemsFromSummary(deliverable, amount, summary);
  }

  /**
   * Same planning logic as {@link #planItems}, but reuses an already-fetched {@link
   * InventorySummary} instead of hitting the network-wide scan again - {@link #requestItems} needs
   * the summary for its own empty-check anyway, so fetching it twice per call wastes a full network
   * scan every time (and the perf logger's own same-tick cooldown hides that it happened).
   */
  private List<ItemStack> planItemsFromSummary(
      IDeliverable deliverable, int amount, InventorySummary summary) {
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
  public List<ItemStack> requestItems(
      IDeliverable deliverable, int amount, String requesterName, @Nullable UUID requestUuid) {
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

    List<ItemStack> orderedStacks = planItemsFromSummary(deliverable, amount, summary);
    return requestStacksWithUuid(orderedStacks, requesterName, requestUuid);
  }

  @Override
  public List<ItemStack> requestStacks(List<ItemStack> requestedStacks, String requesterName) {
    return requestStacksWithUuid(requestedStacks, requesterName, null);
  }

  private List<ItemStack> requestStacksWithUuid(
      List<ItemStack> requestedStacks, String requesterName, @Nullable UUID requestUuid) {
    List<ItemStack> normalized = normalizeRequestedStacks(requestedStacks);
    if (normalized.isEmpty()) {
      if (com.thesettler_x_create.Config.DEBUG_LOGGING.getAsBoolean()) {
        com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
            "[CreateShop] requestStacks computed empty order");
      }
      return Collections.emptyList();
    }
    CreateNetworkRequestQueue.queue(
        this,
        shop.getStockNetworkId(),
        shop.getShopAddress(),
        normalized,
        requesterName,
        requestUuid);
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
    return requestStacksImmediate(requestedStacks, requesterName, null);
  }

  public List<ItemStack> requestStacksImmediate(
      List<ItemStack> requestedStacks, String requesterName, @Nullable UUID requestUuid) {
    List<ItemStack> normalized = normalizeRequestedStacks(requestedStacks);
    if (normalized.isEmpty() || !hasNetwork() || shop == null) {
      return Collections.emptyList();
    }
    QueuedRequestKey key =
        new QueuedRequestKey(
            shop.getStockNetworkId(),
            shop.getShopAddress(),
            requesterName == null ? "" : requesterName,
            requestUuid);
    return broadcastQueuedRequest(key, normalized) ? normalized : Collections.emptyList();
  }

  public static void flushQueuedRequests() {
    CreateNetworkRequestQueue.flush();
  }

  private List<ItemStack> consolidateRequestedStacks(List<ItemStack> requestedStacks) {
    if (requestedStacks == null || requestedStacks.isEmpty()) {
      return Collections.emptyList();
    }
    List<ItemStack> consolidated = new ArrayList<>();
    for (ItemStack requestStack : requestedStacks) {
      ItemStackDataUtil.mergeIntoList(consolidated, requestStack);
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
      normalized.addAll(chunkForPackaging(requestStack));
    }
    boolean capacityStalled = false;
    for (ItemStack requested : consolidated) {
      int acceptedCount = countAccepted(acceptedByCapacity, requested);
      if (acceptedCount < requested.getCount()) {
        capacityStalled = true;
        shop.noteCapacityStall(requested, requested.getCount(), acceptedCount);
      }
      if (com.thesettler_x_create.Config.DEBUG_LOGGING.getAsBoolean()) {
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
    if (!capacityStalled) {
      shop.clearCapacityStall();
    }
    return normalized;
  }

  /**
   * Splits {@code stack} into copies of at most {@link #MAX_PACKAGE_COUNT}, further capped to the
   * item's own max stack size - without that second cap, an item whose max stack size is below 99
   * (most tools and many non-stackables) would get a chunk stamped with a count higher than the
   * item itself allows.
   */
  private static List<ItemStack> chunkForPackaging(ItemStack stack) {
    List<ItemStack> chunks = new ArrayList<>();
    if (stack == null || stack.isEmpty()) {
      return chunks;
    }
    int maxPer = Math.max(1, Math.min(MAX_PACKAGE_COUNT, stack.getMaxStackSize()));
    int available = stack.getCount();
    while (available > 0) {
      int chunk = Math.min(available, maxPer);
      ItemStack chunkStack = stack.copy();
      chunkStack.setCount(chunk);
      chunks.add(chunkStack);
      available -= chunk;
    }
    return chunks;
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

  private void recordInflight(
      List<ItemStack> orderedStacks, String requesterName, @Nullable UUID requestUuid) {
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
    pickup.recordInflight(
        orderedStacks, baseline, requesterName, shop.getShopAddress(), requestUuid);
  }

  /**
   * Seam-audit finding s2-1 (still-open half): called when {@link CreateNetworkRequestQueue}
   * definitively gives up on a bucket after {@code MAX_RETRY_ATTEMPTS} failed broadcasts. {@link
   * CreateShopAttemptResolveService} reserves the ordered amount via {@code pickup.reserve(...)} as
   * soon as a network order is attempted - before knowing whether the broadcast will ever succeed -
   * so a permanently-abandoned order used to leave that amount "spoken for" until the reservation's
   * own 5-minute TTL expired it. Consuming it here per-stack (not a blanket {@code
   * release(requestId)}) only removes the amount this specific failed attempt reserved, so a newer,
   * still-viable reservation for the same request (e.g. a later retry that reserved more) isn't
   * wiped alongside it.
   */
  void releaseAbandonedReservation(@Nullable UUID requestUuid, List<ItemStack> stacks) {
    if (requestUuid == null || stacks == null || stacks.isEmpty()) {
      return;
    }
    if (!(shop.getBuilding() instanceof BuildingCreateShop building)) {
      return;
    }
    CreateShopBlockEntity pickup = building.getPickupBlockEntity();
    if (pickup == null) {
      return;
    }
    for (ItemStack stack : stacks) {
      if (stack == null || stack.isEmpty()) {
        continue;
      }
      pickup.consumeReservedForRequest(requestUuid, stack, stack.getCount());
    }
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
      // Not gated behind Config.DEBUG_LOGGING - a server admin needs to see this even with debug
      // logging off, or the shop just quietly stops fulfilling requests with no visible cause.
      com.thesettler_x_create.TheSettlerXCreate.LOGGER.warn(
          "[CreateShop] Network summary lookup failed for {}: {}",
          shop.getStockNetworkId(),
          ex.getMessage() == null ? "<null>" : ex.getMessage());
      return null;
    } finally {
      perfLogger.recordSummary(System.nanoTime() - start, shop);
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

  boolean broadcastQueuedRequest(QueuedRequestKey key, List<ItemStack> stacks) {
    if (key == null
        || stacks == null
        || stacks.isEmpty()
        || shop == null
        || key.networkId() == null) {
      return true;
    }
    List<ItemStack> consolidated = consolidateRequestedStacks(stacks);
    if (consolidated.isEmpty()) {
      return true;
    }
    List<BigItemStack> order = new ArrayList<>();
    for (ItemStack requestStack : consolidated) {
      for (ItemStack chunkStack : chunkForPackaging(requestStack)) {
        order.add(new BigItemStack(chunkStack.copy(), chunkStack.getCount()));
      }
    }
    if (order.isEmpty()) {
      return true;
    }
    long start = System.nanoTime();
    try {
      CreateLogisticsBridge.broadcastPackageRequest(key.networkId(), order, key.address());
      if (com.thesettler_x_create.Config.DEBUG_LOGGING.getAsBoolean()) {
        com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
            "[CreateShop] broadcast grouped request stacks={} chunks={} network={} address='{}' requester='{}'",
            consolidated.size(),
            order.size(),
            key.networkId(),
            key.address(),
            key.requesterName());
      }
      recordInflight(consolidated, key.requesterName(), key.requestUuid());
      return true;
    } catch (Exception ex) {
      // Not gated behind Config.DEBUG_LOGGING - see the getSummary() catch above for why.
      com.thesettler_x_create.TheSettlerXCreate.LOGGER.warn(
          "[CreateShop] grouped request broadcast failed for {}: {}",
          key.networkId(),
          ex.getMessage() == null ? "<null>" : ex.getMessage());
      return false;
    } finally {
      perfLogger.recordBroadcast(System.nanoTime() - start, order.size(), shop);
    }
  }
}
