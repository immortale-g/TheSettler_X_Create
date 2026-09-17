package com.thesettler_x_create.minecolonies.building;

import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.core.colony.buildings.modules.ItemListModule;
import com.thesettler_x_create.create.ShopSupplyPolicy;
import com.thesettler_x_create.minecolonies.module.CreateShopNetworkMinimumModule;
import com.thesettler_x_create.stock.ShopStockAccounting;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * What a shop lets the colony take out of its Create stock network: everything, unless the player
 * has blocked an item kind or asked the shop to keep a minimum of it in the network for the
 * production it feeds. Both settings live on the shop, so two shops on one network can answer
 * differently.
 */
public final class ShopColonySupplyPolicy {
  /** Module id of the per-shop block list; the list holds what the colony may NOT draw. */
  public static final String DENIED_LIST_ID = "createshop_colony_denied";

  private ShopColonySupplyPolicy() {}

  /** The policy of this shop, or an open one when there is no shop to ask. */
  public static ShopSupplyPolicy of(@Nullable BuildingCreateShop shop) {
    if (shop == null) {
      return ShopSupplyPolicy.ALLOW_EVERYTHING;
    }
    ItemListModule denied =
        shop.getModule(ItemListModule.class, module -> DENIED_LIST_ID.equals(module.getId()));
    CreateShopNetworkMinimumModule minimum = shop.getModule(CreateShopNetworkMinimumModule.class);
    return of(
        kind -> denied != null && denied.isItemInList(new ItemStorage(kind)),
        kind -> minimum == null ? 0 : minimum.getMinimum(kind));
  }

  /**
   * The same policy over plain lookups, so the decision can be exercised without a colony.
   *
   * @param denied whether the colony may not draw this item kind at all
   * @param keepInNetwork how many of it the shop keeps for itself
   */
  public static ShopSupplyPolicy of(
      Predicate<ItemStack> denied, ToIntFunction<ItemStack> keepInNetwork) {
    return (kind, inNetwork) -> {
      if (kind == null || kind.isEmpty() || inNetwork <= 0) {
        return 0;
      }
      if (denied.test(kind)) {
        return 0;
      }
      return ShopStockAccounting.drawableFromNetwork(inNetwork, keepInNetwork.applyAsInt(kind));
    };
  }
}
