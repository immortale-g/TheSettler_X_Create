package com.thesettler_x_create.minecolonies.building;

import com.minecolonies.api.crafting.ItemStorage;
import com.thesettler_x_create.DebugLog;
import com.thesettler_x_create.TheSettlerXCreate;
import com.thesettler_x_create.blockentity.CreateShopBlockEntity;
import com.thesettler_x_create.minecolonies.tileentity.TileEntityCreateShop;
import com.thesettler_x_create.stock.ShopStockAccounting;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * Decides what a warehouse pickup may take from the Create Shop.
 *
 * <p>A courier doing a pickup walks through the hut's whole combined inventory, racks included, and
 * takes whatever the building does not ask to keep. The shopkeeper is the one who decides what
 * leaves the racks (after it sat there unreserved long enough, into the hut buffer), so a pickup
 * keeps all rack stock and at least everything reserved, and only takes the rest from the hut
 * buffer. MineColonies visits the racks before the hut inventory and passes the running keep count
 * per item kind in {@code localAlreadyKept}.
 */
final class ShopPickupKeepPolicy {
  private final BuildingCreateShop shop;

  ShopPickupKeepPolicy(BuildingCreateShop shop) {
    this.shop = shop;
  }

  /**
   * @return how much of {@code stack} the pickup may take
   */
  /**
   * Whether a courier called to this shop would find anything it may take.
   *
   * <p>The hut's inventory is the racks and the hut buffer combined, and the racks are the Create
   * side: their stock is kept here, not handed to a courier. Asking for a pickup because the
   * combined inventory is not empty therefore calls a courier for goods this very policy then
   * refuses, every time the housekeeping ticks. The courier walks over, takes nothing, and comes
   * back twenty-five seconds later.
   */
  boolean anythingToPickUp(IItemHandler inventory) {
    if (inventory == null) {
      return false;
    }
    List<ItemStorage> kept = new ArrayList<>();
    for (int slot = 0; slot < inventory.getSlots(); slot++) {
      ItemStack stack = inventory.getStackInSlot(slot);
      if (stack.isEmpty()) {
        continue;
      }
      if (takeableForPickup(stack, kept) > 0) {
        return true;
      }
    }
    return false;
  }

  int takeableForPickup(ItemStack stack, List<ItemStorage> localAlreadyKept) {
    if (stack == null || stack.isEmpty()) {
      return 0;
    }
    TileEntityCreateShop tile = shop.getCreateShopTileEntity();
    CreateShopBlockEntity pickup = shop.getPickupBlockEntity();
    int rackStock = tile == null ? 0 : tile.countInRacks(stack);
    int reserved = pickup == null ? 0 : pickup.getReservedFor(stack);
    int keepAmount = ShopStockAccounting.pickupKeepAmount(rackStock, reserved);

    ItemStorage kept = findKept(localAlreadyKept, stack);
    int alreadyKept = kept == null ? 0 : kept.getAmount();
    int takeable = ShopStockAccounting.pickupTakeable(stack.getCount(), keepAmount, alreadyKept);
    // One line per occupied slot per housekeeping pass is a lot with debug logging on by default.
    // What is worth reading is the case that keeps goods where they are.
    if (takeable <= 0 && DebugLog.enabled()) {
      TheSettlerXCreate.LOGGER.info(
          "[CreateShop] pickup keep item={} inSlot={} rackStock={} reserved={} keep={} alreadyKept={} takeable={}",
          stack.getItem(),
          stack.getCount(),
          rackStock,
          reserved,
          keepAmount,
          alreadyKept,
          takeable);
    }
    int keptHere = stack.getCount() - takeable;
    if (keptHere > 0 && localAlreadyKept != null) {
      if (kept == null) {
        kept = new ItemStorage(stack.copy());
        kept.setAmount(keptHere);
        localAlreadyKept.add(kept);
      } else {
        kept.setAmount(alreadyKept + keptHere);
      }
    }
    return takeable;
  }

  private static ItemStorage findKept(List<ItemStorage> localAlreadyKept, ItemStack stack) {
    if (localAlreadyKept == null) {
      return null;
    }
    for (ItemStorage kept : localAlreadyKept) {
      if (kept != null && ItemStack.isSameItemSameComponents(kept.getItemStack(), stack)) {
        return kept;
      }
    }
    return null;
  }
}
