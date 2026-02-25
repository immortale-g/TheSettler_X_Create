package com.thesettler_x_create.minecolonies.building;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.thesettler_x_create.Config;
import com.thesettler_x_create.blockentity.CreateShopBlockEntity;
import com.thesettler_x_create.minecolonies.job.JobCreateShop;
import com.thesettler_x_create.minecolonies.tileentity.TileEntityCreateShop;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Handles inflight Create stock order tracking and overdue notifications. */
final class ShopInflightTracker {
  private final BuildingCreateShop shop;
  private long lastInflightTick = -1L;

  ShopInflightTracker(BuildingCreateShop shop) {
    this.shop = shop;
  }

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
    List<ItemStack> inflightKeys = pickup.getInflightKeys();
    if (inflightKeys.isEmpty()) {
      return;
    }
    var currentCounts = shop.getStockCountsForKeys(inflightKeys);
    pickup.reconcileInflight(currentCounts);
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
      return;
    }
    for (CreateShopBlockEntity.InflightNotice notice : notices) {
      if (notice == null || notice.stackKey == null || notice.stackKey.isEmpty()) {
        continue;
      }
      if (BuildingCreateShop.isDebugRequests()) {
        com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
            "[CreateShop] lost-package interaction trigger item={} remaining={} age={} requester='{}' address='{}'",
            notice.stackKey.getHoverName().getString(),
            notice.remaining,
            notice.age,
            notice.requesterName,
            notice.address);
      }
      citizen.triggerInteraction(
          new ShopLostPackageInteraction(
              notice.stackKey.copy(), notice.remaining, notice.requesterName, notice.address));
      // Hard gate: only one lost-package interaction should be triggered per tracker tick.
      break;
    }
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
        new ShopCapacityStallInteraction(notice.stackKey, notice.requested, notice.accepted));
  }
}
