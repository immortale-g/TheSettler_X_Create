package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import com.minecolonies.api.colony.requestsystem.requestable.IDeliverable;
import com.thesettler_x_create.blockentity.CreateShopBlockEntity;
import com.thesettler_x_create.minecolonies.tileentity.TileEntityCreateShop;
import com.thesettler_x_create.stock.ShopStockAccounting;
import java.util.List;
import java.util.UUID;
import java.util.function.IntSupplier;
import net.minecraft.world.item.ItemStack;

/**
 * The one place that orders from the Create network for a request, used by the first attempt and by
 * every top-up.
 *
 * <p>Before ordering it counts what is already coming: the request's own orders, then orders nobody
 * owns anymore (a cancelled request's goods), which the request takes over. Only the rest is
 * ordered, and that order is tracked as on its way as soon as it is queued. Ordering does not
 * reserve anything; the goods are reserved for the request when they arrive in the racks.
 */
final class CreateShopNetworkOrderService {
  private final CreateShopStockResolver stockResolver;

  CreateShopNetworkOrderService(CreateShopStockResolver stockResolver) {
    this.stockResolver = stockResolver;
  }

  /**
   * What happened to a missing amount.
   *
   * @param ordered stacks ordered now
   * @param ownInflight what was already on its way for the request
   * @param claimed unowned incoming stock the request took over
   */
  record OrderResult(List<ItemStack> ordered, int ownInflight, int claimed) {
    int orderedCount() {
      return ordered.stream().mapToInt(ItemStack::getCount).sum();
    }

    /** Whether goods are on their way for the request, from before or from now. */
    boolean somethingOnItsWay() {
      return ownInflight > 0 || claimed > 0 || !ordered.isEmpty();
    }
  }

  OrderResult orderMissing(
      TileEntityCreateShop tile,
      CreateShopBlockEntity pickup,
      IDeliverable deliverable,
      UUID requestId,
      int missing,
      IntSupplier networkAvailable,
      String requesterName) {
    if (tile == null || pickup == null || deliverable == null || requestId == null) {
      return new OrderResult(List.of(), 0, 0);
    }
    int ownInflight = pickup.getInflightRemainingFor(requestId, deliverable::matches);
    int need = ShopStockAccounting.networkOrderAmount(missing, ownInflight);
    int claimed = need > 0 ? pickup.claimFreeInflight(requestId, deliverable::matches, need) : 0;
    int stillMissing = Math.max(0, need - claimed);
    // The network summary is a network-wide scan; only look when something is left to order.
    int toOrder =
        stillMissing > 0 ? Math.min(stillMissing, Math.max(0, networkAvailable.getAsInt())) : 0;
    List<ItemStack> ordered =
        toOrder > 0
            ? stockResolver.requestFromNetwork(tile, deliverable, toOrder, requesterName, requestId)
            : List.of();
    return new OrderResult(ordered, ownInflight, claimed);
  }
}
