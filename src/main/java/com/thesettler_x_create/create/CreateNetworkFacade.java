package com.thesettler_x_create.create;

import com.minecolonies.api.colony.requestsystem.requestable.IDeliverable;
import com.minecolonies.api.colony.requestsystem.requestable.Tool;
import com.minecolonies.api.equipment.registry.EquipmentTypeEntry;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.packager.InventorySummary;
import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBehaviour;
import com.simibubi.create.content.logistics.packagerLink.LogisticsManager;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;
import com.thesettler_x_create.minecolonies.tileentity.TileEntityCreateShop;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import net.minecraft.world.item.ItemStack;

public class CreateNetworkFacade implements ICreateNetworkFacade {
  private static final int MAX_PACKAGE_COUNT = 99;
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
  public List<ItemStack> requestItems(IDeliverable deliverable, int amount) {
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
    List<BigItemStack> order = new ArrayList<>();

    for (ItemStack requestStack : orderedStacks) {
      if (requestStack.isEmpty()) {
        continue;
      }
      order.add(new BigItemStack(requestStack.copy(), requestStack.getCount()));
    }

    if (!order.isEmpty()) {
      PackageOrderWithCrafts request = PackageOrderWithCrafts.simple(order);
      long start = System.nanoTime();
      try {
        LogisticsManager.broadcastPackageRequest(
            shop.getStockNetworkId(),
            LogisticallyLinkedBehaviour.RequestType.PLAYER,
            request,
            null,
            shop.getShopAddress());
        if (com.thesettler_x_create.Config.DEBUG_LOGGING.getAsBoolean()) {
          com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
              "[CreateShop] Requested {} stack(s) from network {} to address '{}'",
              order.size(),
              shop.getStockNetworkId(),
              shop.getShopAddress());
        }
      } catch (Exception ex) {
        if (com.thesettler_x_create.Config.DEBUG_LOGGING.getAsBoolean()) {
          com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
              "[CreateShop] requestItems broadcast failed for {}: {}",
              shop.getStockNetworkId(),
              ex.getMessage() == null ? "<null>" : ex.getMessage());
        }
        return Collections.emptyList();
      } finally {
        lastBroadcastNanos = System.nanoTime() - start;
        lastBroadcastCount = order.size();
        maybeLogPerf();
      }
    } else if (com.thesettler_x_create.Config.DEBUG_LOGGING.getAsBoolean()) {
      com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
          "[CreateShop] requestItems computed empty order for {}", deliverable);
    }

    return orderedStacks;
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
}
