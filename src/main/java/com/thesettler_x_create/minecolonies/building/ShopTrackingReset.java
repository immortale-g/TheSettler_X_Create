package com.thesettler_x_create.minecolonies.building;

import com.thesettler_x_create.TheSettlerXCreate;
import com.thesettler_x_create.blockentity.CreateShopBlockEntity;
import com.thesettler_x_create.create.CreateNetworkFacade;
import com.thesettler_x_create.minecolonies.requestsystem.resolver.CreateShopRequestResolver;
import com.thesettler_x_create.minecolonies.tileentity.TileEntityCreateShop;
import java.util.Set;

/**
 * Resets the tracking a {@link BuildingCreateShop} keeps on its own, scope by scope, for the
 * operator {@code tracking-reset} command. Like MineColonies' request system reset it is an
 * emergency brake: requests are not cancelled, they re-derive what they need on their next tick.
 */
final class ShopTrackingReset {
  private final BuildingCreateShop shop;

  ShopTrackingReset(BuildingCreateShop shop) {
    this.shop = shop;
  }

  ShopTrackingResetReport reset(Set<ShopTrackingScope> scopes) {
    ShopTrackingResetReport report = new ShopTrackingResetReport();
    if (scopes == null || scopes.isEmpty()) {
      return report;
    }
    report.countShop();
    CreateShopBlockEntity pickup = shop.getPickupBlockEntity();
    TileEntityCreateShop tile = shop.getCreateShopTileEntity();
    CreateShopRequestResolver resolver = shop.getShopResolver();

    if (scopes.contains(ShopTrackingScope.RESERVATIONS) && pickup != null) {
      report.add(ShopTrackingScope.RESERVATIONS, pickup.clearReservations());
    }
    if (scopes.contains(ShopTrackingScope.INFLIGHT) && pickup != null) {
      report.noteInflightBeforeReset(pickup.getInflightEntryCount());
      report.add(ShopTrackingScope.INFLIGHT, pickup.clearInflight());
      // Interactions already shown for the forgotten orders must not act on them anymore.
      shop.advanceLostPackageInteractionEpoch("tracking-reset");
    }
    if (scopes.contains(ShopTrackingScope.STOCK_AGES) && tile != null) {
      report.add(ShopTrackingScope.STOCK_AGES, tile.clearStockAges());
    }
    if (scopes.contains(ShopTrackingScope.FLOW_STATES)) {
      int cleared = shop.clearPendingFlowStates();
      if (resolver != null) {
        cleared += resolver.resetFlowStates();
      }
      report.add(ShopTrackingScope.FLOW_STATES, cleared);
    }
    if (scopes.contains(ShopTrackingScope.GAUGE)) {
      report.add(ShopTrackingScope.GAUGE, shop.clearGaugeTracking());
    }
    if (scopes.contains(ShopTrackingScope.RUNTIME)) {
      int cleared = CreateNetworkFacade.discardQueuedRequests(tile);
      if (resolver != null) {
        cleared += resolver.resetRuntimeTracking();
      }
      report.add(ShopTrackingScope.RUNTIME, cleared);
    }
    shop.markDirty();
    TheSettlerXCreate.LOGGER.info(
        "[CreateShop] tracking reset shop={} scopes={} {}",
        shop.getLocation() == null ? "<unknown>" : shop.getLocation().getInDimensionLocation(),
        scopes,
        report.summary());
    return report;
  }
}
