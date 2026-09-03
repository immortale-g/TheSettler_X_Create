package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import com.minecolonies.api.colony.requestsystem.requestable.IDeliverable;
import com.thesettler_x_create.blockentity.CreateShopBlockEntity;
import com.thesettler_x_create.create.CreateNetworkFacade;
import com.thesettler_x_create.create.ICreateNetworkFacade;
import com.thesettler_x_create.minecolonies.tileentity.TileEntityCreateShop;
import java.util.List;
import java.util.UUID;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

final class CreateShopStockResolver {
  CreateShopStockSnapshot getAvailability(
      TileEntityCreateShop tile,
      CreateShopBlockEntity pickup,
      IDeliverable deliverable,
      int reservedForOthers,
      CreateShopResolverPlanning planning) {
    ICreateNetworkFacade network = new CreateNetworkFacade(tile);
    int networkAvailable = network.getAvailable(deliverable);
    int rackAvailable = planning.getAvailableFromRacks(tile, deliverable);
    int pickupAvailable = planning.getAvailableFromPickup(pickup, deliverable);
    int rackUsable = Math.max(0, rackAvailable - reservedForOthers);
    int available = Math.max(0, networkAvailable + rackUsable + pickupAvailable);
    return new CreateShopStockSnapshot(
        networkAvailable, rackAvailable, pickupAvailable, rackUsable, available);
  }

  List<ItemStack> requestFromNetwork(
      TileEntityCreateShop tile, IDeliverable deliverable, int count, String requesterName) {
    return requestFromNetwork(tile, deliverable, count, requesterName, null);
  }

  List<ItemStack> requestFromNetwork(
      TileEntityCreateShop tile,
      IDeliverable deliverable,
      int count,
      String requesterName,
      @Nullable UUID requestUuid) {
    if (count <= 0) {
      return java.util.Collections.emptyList();
    }
    ICreateNetworkFacade network = new CreateNetworkFacade(tile);
    return network.requestItems(deliverable, count, requesterName, requestUuid);
  }

  int getNetworkAvailable(TileEntityCreateShop tile, IDeliverable deliverable) {
    ICreateNetworkFacade network = new CreateNetworkFacade(tile);
    return network.getAvailable(deliverable);
  }
}
