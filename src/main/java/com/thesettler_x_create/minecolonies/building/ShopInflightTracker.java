package com.thesettler_x_create.minecolonies.building;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.thesettler_x_create.Config;
import com.thesettler_x_create.DebugLog;
import com.thesettler_x_create.blockentity.CreateShopBlockEntity;
import com.thesettler_x_create.minecolonies.job.JobCreateShop;
import com.thesettler_x_create.minecolonies.tileentity.TileEntityCreateShop;
import com.thesettler_x_create.stock.InflightBook;
import com.thesettler_x_create.stock.ShopStockAccounting;
import java.util.List;
import java.util.Map;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Handles inflight Create stock order tracking and overdue notifications. */
final class ShopInflightTracker {
  private final BuildingCreateShop shop;
  private long lastInflightTick = -1L;

  ShopInflightTracker(BuildingCreateShop shop) {
    this.shop = shop;
  }

  /**
   * Books arrived Create orders and reserves them for the request that ordered them. Runs every
   * colony tick before the resolver plans, so goods that arrived are already spoken for when other
   * requests look at the free rack stock.
   */
  void reconcileArrivals(IColony colony) {
    if (colony == null || colony.getWorld() == null || colony.getWorld().isClientSide) {
      return;
    }
    CreateShopBlockEntity pickup = shop.getPickupBlockEntity();
    if (pickup == null) {
      return;
    }
    List<ItemStack> inflightKeys = pickup.getInflightKeys();
    if (inflightKeys.isEmpty()) {
      return;
    }
    Map<ItemStack, Integer> currentCounts = shop.getStockCountsForKeys(inflightKeys);
    List<InflightBook.Arrival<ItemStack>> arrivals = pickup.reconcileInflight(currentCounts);
    if (arrivals.isEmpty()) {
      return;
    }
    for (InflightBook.Arrival<ItemStack> arrival : arrivals) {
      int reserved = 0;
      if (arrival.owner() != null) {
        int rackStock = countFor(currentCounts, arrival.key());
        int reservedByRequests = pickup.getReservedFor(arrival.key());
        reserved =
            ShopStockAccounting.arrivalReservation(arrival.amount(), rackStock, reservedByRequests);
        if (reserved > 0) {
          pickup.reserve(arrival.owner(), arrival.key(), reserved);
        }
      }
      if (DebugLog.enabled()) {
        com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
            "[CreateShop] inflight arrival item={} amount={} owner={} reserved={}",
            arrival.key().getHoverName().getString(),
            arrival.amount(),
            arrival.owner(),
            reserved);
      }
    }
  }

  /** Overdue orders: unowned ones are dropped, owned ones are asked about. */
  void tick(IColony colony) {
    if (colony == null) {
      return;
    }
    Level level = colony.getWorld();
    if (level == null) {
      return;
    }
    long now = level.getGameTime();
    if (now != 0L
        && lastInflightTick >= 0
        && now - lastInflightTick < Config.INFLIGHT_CHECK_INTERVAL_TICKS.getAsLong()) {
      return;
    }
    lastInflightTick = now;
    CreateShopBlockEntity pickup = shop.getPickupBlockEntity();
    if (pickup == null) {
      return;
    }
    for (InflightBook.StoredEntry<ItemStack> expired :
        pickup.expireFreeInflight(now, Config.INFLIGHT_TIMEOUT_TICKS.getAsLong())) {
      if (DebugLog.enabled()) {
        com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
            "[CreateShop] inflight dropped unowned overdue order item={} remaining={} age={}",
            expired.key().getHoverName().getString(),
            expired.remaining(),
            now - expired.requestedAt());
      }
    }
    if (pickup.getInflightKeys().isEmpty()) {
      return;
    }
    if (shop.hasActiveLocalDeliveryChildrenForInflight(colony)) {
      if (DebugLog.enabled()) {
        com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
            "[CreateShop] lost-package interaction skipped: local delivery still active");
      }
      notifyShopkeeperCapacityStall();
      return;
    }
    List<CreateShopBlockEntity.InflightNotice> notices =
        pickup.consumeOverdueNotices(now, Config.INFLIGHT_TIMEOUT_TICKS.getAsLong());
    if (!notices.isEmpty()) {
      notifyShopkeeperOverdue(notices);
    }
    notifyShopkeeperCapacityStall();
  }

  private void notifyShopkeeperOverdue(List<CreateShopBlockEntity.InflightNotice> notices) {
    if (notices == null || notices.isEmpty()) {
      return;
    }
    ICitizenData citizen = getShopkeeperCitizen();
    if (citizen == null) {
      if (DebugLog.enabled()) {
        com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
            "[CreateShop] lost-package interaction skipped: no shopkeeper citizen found");
      }
      return;
    }
    for (CreateShopBlockEntity.InflightNotice notice : notices) {
      if (notice == null || notice.stackKey == null || notice.stackKey.isEmpty()) {
        continue;
      }
      if (DebugLog.enabled()) {
        com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
            "[CreateShop] lost-package interaction trigger item={} remaining={} age={} requester='{}' address='{}'",
            notice.stackKey.getHoverName().getString(),
            notice.remaining,
            notice.age,
            notice.requesterName,
            notice.address);
      }
      ShopLostPackageInteraction interaction =
          new ShopLostPackageInteraction(
              notice.stackKey.copy(),
              notice.remaining,
              notice.requesterName,
              notice.address,
              notice.requestedAt,
              shop.getLostPackageInteractionEpoch(),
              notice.requestUuid);
      if (DebugLog.enabled()) {
        com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
            "[CreateShop] lost-package interaction trigger dispatch citizen={} interactionId={}",
            citizen.getName(),
            interaction.getId().getString());
      }
      citizen.triggerInteraction(interaction);
      // Hard gate: only one lost-package interaction should be triggered per tracker tick.
      break;
    }
  }

  private static int countFor(Map<ItemStack, Integer> counts, ItemStack key) {
    for (Map.Entry<ItemStack, Integer> entry : counts.entrySet()) {
      if (ItemStack.isSameItemSameComponents(entry.getKey(), key)) {
        return entry.getValue();
      }
    }
    return 0;
  }

  private ICitizenData getShopkeeperCitizen() {
    for (ICitizenData citizen : shop.getAllAssignedCitizen()) {
      if (citizen == null) {
        continue;
      }
      if (citizen.getJob() instanceof JobCreateShop) {
        return citizen;
      }
    }
    return null;
  }

  private void notifyShopkeeperCapacityStall() {
    ICitizenData citizen = getShopkeeperCitizen();
    if (citizen == null) {
      return;
    }
    TileEntityCreateShop.CapacityStallNotice notice = shop.consumeCapacityStallNotice();
    if (notice == null || notice.stackKey == null || notice.stackKey.isEmpty()) {
      return;
    }
    citizen.triggerInteraction(
        new ShopCapacityStallInteraction(
            notice.stackKey, notice.requested, notice.accepted, shop.getPickUpPriority() <= 0));
  }
}
