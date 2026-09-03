package com.thesettler_x_create.minecolonies.building;

import com.minecolonies.api.util.InventoryUtils;
import com.thesettler_x_create.blockentity.CreateShopBlockEntity;
import com.thesettler_x_create.minecolonies.tileentity.TileEntityCreateShop;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Scans a player's inventory for packages matching a lost delivery and hands them over to the
 * shop's racks, consuming the matching amount of inflight tracking.
 */
final class ShopLostPackageHandoverProcessor {
  private final BuildingCreateShop shop;

  ShopLostPackageHandoverProcessor(BuildingCreateShop shop) {
    this.shop = shop;
  }

  int acceptFromPlayer(
      Player player,
      ItemStack stackKey,
      int remaining,
      String requesterName,
      String address,
      long requestedAt) {
    if (BuildingCreateShop.isDebugRequests()) {
      com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
          "[CreateShop] lost-package handover requested player={} item={} remaining={} requester='{}' address='{}'",
          player == null ? "<null>" : player.getName().getString(),
          stackKey == null || stackKey.isEmpty() ? "<empty>" : stackKey.getHoverName().getString(),
          remaining,
          requesterName,
          address);
    }
    if (player == null || stackKey == null || stackKey.isEmpty()) {
      if (BuildingCreateShop.isDebugRequests()) {
        com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
            "[CreateShop] lost-package handover rejected: invalid input");
      }
      return 0;
    }
    TileEntityCreateShop tile = shop.getCreateShopTileEntity();
    CreateShopBlockEntity pickup = shop.getPickupBlockEntity();
    if (tile == null || pickup == null) {
      if (BuildingCreateShop.isDebugRequests()) {
        com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
            "[CreateShop] lost-package handover rejected: tilePresent={} pickupPresent={}",
            tile != null,
            pickup != null);
      }
      return 0;
    }
    var inventory = player.getInventory();
    shop.ensureRackContainers();
    int targetAmount = Math.max(1, remaining);
    int inflightBefore = pickup.getInflightRemaining(stackKey, requesterName, address, requestedAt);
    if (BuildingCreateShop.isDebugRequests()) {
      com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
          "[CreateShop] lost-package handover precheck inventorySlots={} target={} inflightBefore={} requester='{}' address='{}'",
          inventory.getContainerSize(),
          targetAmount,
          inflightBefore,
          requesterName,
          address);
    }
    int totalConsumed = 0;
    int totalInsertedMatching = 0;
    int scannedPackages = 0;
    int matchedPackages = 0;
    int removedPackages = 0;
    for (int slot = 0;
        slot < inventory.getContainerSize() && totalConsumed < targetAmount;
        slot++) {
      ItemStack candidate = inventory.getItem(slot);
      boolean isPackage =
          candidate != null
              && !candidate.isEmpty()
              && com.simibubi.create.content.logistics.box.PackageItem.isPackage(candidate);
      if (isPackage) {
        scannedPackages++;
      }
      int matching = ShopLostPackageInteraction.countMatchingInPackage(candidate, stackKey);
      if (BuildingCreateShop.isDebugRequests() && candidate != null && !candidate.isEmpty()) {
        if (isPackage || matching > 0) {
          com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
              "[CreateShop] lost-package handover scan slot={} stack={} isPackage={} matchingCount={}",
              slot,
              candidate.getHoverName().getString(),
              isPackage,
              matching);
        }
      }
      if (matching <= 0) {
        continue;
      }
      matchedPackages++;
      List<ItemStack> previewUnpacked = ShopLostPackageInteraction.unpackPackage(candidate);
      if (BuildingCreateShop.isDebugRequests()) {
        com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
            "[CreateShop] lost-package handover slot={} previewUnpackedStacks={} matching={}",
            slot,
            previewUnpacked.size(),
            matching);
      }
      if (previewUnpacked.isEmpty()) {
        if (BuildingCreateShop.isDebugRequests()) {
          com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
              "[CreateShop] lost-package handover slot={} skip: preview unpack empty", slot);
        }
        continue;
      }
      List<ItemStack> previewAccepted = tile.planInboundAcceptedStacks(previewUnpacked);
      int previewInsertedMatching = countMatching(previewAccepted, stackKey);
      int consumeTarget =
          Math.min(targetAmount - totalConsumed, Math.max(0, previewInsertedMatching));
      if (consumeTarget <= 0) {
        if (BuildingCreateShop.isDebugRequests()) {
          com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
              "[CreateShop] lost-package handover slot={} skip: preview accepted no matching items",
              slot);
        }
        continue;
      }
      int strictRemaining =
          pickup.getInflightRemaining(stackKey, requesterName, address, requestedAt);
      int looseRemaining = pickup.getInflightRemaining(stackKey, "", "");
      if (strictRemaining < consumeTarget && looseRemaining < consumeTarget) {
        if (BuildingCreateShop.isDebugRequests()) {
          com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
              "[CreateShop] lost-package handover slot={} skip: no inflight remainder for consumeTarget={} strictRemaining={} looseRemaining={}",
              slot,
              consumeTarget,
              strictRemaining,
              looseRemaining);
        }
        continue;
      }
      ItemStack removedPackage = inventory.removeItem(slot, 1);
      if (removedPackage.isEmpty()) {
        if (BuildingCreateShop.isDebugRequests()) {
          com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
              "[CreateShop] lost-package handover slot={} failed: package remove returned empty",
              slot);
        }
        continue;
      }
      removedPackages++;
      List<ItemStack> unpacked = ShopLostPackageInteraction.unpackPackage(removedPackage);
      if (unpacked.isEmpty() && !previewUnpacked.isEmpty()) {
        unpacked = new ArrayList<>(previewUnpacked.size());
        for (ItemStack stack : previewUnpacked) {
          if (stack != null && !stack.isEmpty()) {
            unpacked.add(stack.copy());
          }
        }
      }
      if (BuildingCreateShop.isDebugRequests()) {
        com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
            "[CreateShop] lost-package handover slot={} unpackedStacks={}", slot, unpacked.size());
      }
      if (unpacked.isEmpty()) {
        if (BuildingCreateShop.isDebugRequests()) {
          com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
              "[CreateShop] lost-package handover slot={} skipped: package unpacked empty", slot);
        }
        continue;
      }
      List<ItemStack> leftovers = tile.insertIntoRacksOnly(unpacked);
      if (BuildingCreateShop.isDebugRequests()) {
        com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
            "[CreateShop] lost-package handover slot={} insertedStacks={} leftoverStacks={}",
            slot,
            unpacked.size() - leftovers.size(),
            leftovers.size());
      }
      for (ItemStack leftover : leftovers) {
        if (!leftover.isEmpty()) {
          Level level = shop.getColony() == null ? null : shop.getColony().getWorld();
          BlockPos dropPos = shop.getLocation().getInDimensionLocation();
          if (level != null) {
            InventoryUtils.spawnItemStack(
                level,
                dropPos.getX() + 0.5D,
                dropPos.getY() + 1.0D,
                dropPos.getZ() + 0.5D,
                leftover);
          }
        }
      }
      int insertedMatching = countMatching(unpacked, stackKey) - countMatching(leftovers, stackKey);
      totalInsertedMatching += Math.max(0, insertedMatching);
      consumeTarget = Math.min(targetAmount - totalConsumed, Math.max(0, insertedMatching));
      int consumed =
          pickup.consumeInflight(stackKey, consumeTarget, requesterName, address, requestedAt);
      totalConsumed += Math.max(0, consumed);
      if (BuildingCreateShop.isDebugRequests()) {
        com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
            "[CreateShop] lost-package handover requester={} item={} inserted={} consumedOld={} totalConsumed={} target={}",
            requesterName,
            stackKey.getHoverName().getString(),
            insertedMatching,
            consumed,
            totalConsumed,
            targetAmount);
        if (consumed <= 0 && consumeTarget > 0) {
          strictRemaining =
              pickup.getInflightRemaining(stackKey, requesterName, address, requestedAt);
          looseRemaining = pickup.getInflightRemaining(stackKey, "", "");
          com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
              "[CreateShop] lost-package handover consume-miss slot={} consumeTarget={} strictRemaining={} looseRemaining={}",
              slot,
              consumeTarget,
              strictRemaining,
              looseRemaining);
        }
      }
      if (consumeTarget > 0 && consumed <= 0) {
        // Avoid draining additional player packages when inflight tuple cannot be consumed.
        break;
      }
    }
    int inflightAfter = pickup.getInflightRemaining(stackKey, requesterName, address, requestedAt);
    if (BuildingCreateShop.isDebugRequests()) {
      com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
          "[CreateShop] lost-package handover summary scannedPackages={} matchedPackages={} removedPackages={} insertedMatchingTotal={} consumedTotal={} target={} inflightBefore={} inflightAfter={}",
          scannedPackages,
          matchedPackages,
          removedPackages,
          totalInsertedMatching,
          totalConsumed,
          targetAmount,
          inflightBefore,
          inflightAfter);
    }
    if (totalConsumed > 0) {
      return totalConsumed;
    }
    if (BuildingCreateShop.isDebugRequests()) {
      com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
          "[CreateShop] lost-package handover failed: no matching package found in player inventory or no inflight consumed (insertedMatchingTotal={})",
          totalInsertedMatching);
    }
    return 0;
  }

  private static int countMatching(List<ItemStack> stacks, ItemStack key) {
    if (stacks == null || stacks.isEmpty() || key == null || key.isEmpty()) {
      return 0;
    }
    int count = 0;
    for (ItemStack stack : stacks) {
      if (stack == null || stack.isEmpty()) {
        continue;
      }
      if (ItemStack.isSameItemSameComponents(stack, key) || ItemStack.isSameItem(stack, key)) {
        count += stack.getCount();
      }
    }
    return count;
  }
}
