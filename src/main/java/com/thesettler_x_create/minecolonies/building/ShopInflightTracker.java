package com.thesettler_x_create.minecolonies.building;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.interactionhandling.ChatPriority;
import com.minecolonies.core.colony.interactionhandling.SimpleNotificationInteraction;
import com.thesettler_x_create.Config;
import com.thesettler_x_create.blockentity.CreateShopBlockEntity;
import com.thesettler_x_create.minecolonies.job.JobCreateShop;
import java.util.List;
import net.minecraft.network.chat.Component;
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
      String requester =
          notice.requesterName == null || notice.requesterName.isBlank()
              ? "unknown requester"
              : notice.requesterName;
      String address =
          notice.address == null || notice.address.isBlank() ? "unknown address" : notice.address;
      String itemName = notice.stackKey.getHoverName().getString();
      String text =
          "Delivery seems lost for "
              + requester
              + ". Item: "
              + itemName
              + " x"
              + notice.remaining
              + " (address: "
              + address
              + ").";
      citizen.triggerInteraction(
          new SimpleNotificationInteraction(Component.literal(text), ChatPriority.IMPORTANT));
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
}
