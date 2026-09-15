package com.thesettler_x_create.minecolonies.building;

import com.minecolonies.api.crafting.ItemStorage;
import com.thesettler_x_create.blockentity.CreateShopBlockEntity;
import com.thesettler_x_create.minecolonies.tileentity.TileEntityCreateShop;
import com.thesettler_x_create.stock.ShopStockAccounting;
import java.util.List;
import net.minecraft.world.item.ItemStack;

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
