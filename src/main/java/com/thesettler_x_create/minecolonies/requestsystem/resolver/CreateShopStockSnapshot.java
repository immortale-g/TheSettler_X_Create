package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import com.minecolonies.api.colony.requestsystem.location.ILocation;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.requestable.IDeliverable;
import com.minecolonies.api.colony.requestsystem.requestable.INonExhaustiveDeliverable;
import com.thesettler_x_create.blockentity.CreateShopBlockEntity;
import com.thesettler_x_create.create.CreateNetworkFacade;
import com.thesettler_x_create.create.ICreateNetworkFacade;
import com.thesettler_x_create.minecolonies.building.BuildingCreateShop;
import com.thesettler_x_create.minecolonies.tileentity.TileEntityCreateShop;
import java.util.List;
import net.minecraft.world.item.ItemStack;

final class CreateShopStockSnapshot {
  private final int networkAvailable;
  private final int rackAvailable;
  private final int pickupAvailable;
  private final int rackUsable;
  private final int available;

  CreateShopStockSnapshot(
      int networkAvailable, int rackAvailable, int pickupAvailable, int rackUsable, int available) {
    this.networkAvailable = networkAvailable;
    this.rackAvailable = rackAvailable;
    this.pickupAvailable = pickupAvailable;
    this.rackUsable = rackUsable;
    this.available = available;
  }

  int getNetworkAvailable() {
    return networkAvailable;
  }

  int getRackAvailable() {
    return rackAvailable;
  }

  int getPickupAvailable() {
    return pickupAvailable;
  }

  int getRackUsable() {
    return rackUsable;
  }

  int getAvailable() {
    return available;
  }
}

/** Shared stack formatting/count helpers for Create Shop resolver services. */
final class CreateShopStackMetrics {
  private CreateShopStackMetrics() {}

  static int countStackList(List<ItemStack> stacks) {
    if (stacks == null || stacks.isEmpty()) {
      return 0;
    }
    int total = 0;
    for (ItemStack stack : stacks) {
      if (stack == null || stack.isEmpty()) {
        continue;
      }
      total += stack.getCount();
    }
    return total;
  }

  static String describeStack(ItemStack stack) {
    if (stack == null || stack.isEmpty()) {
      return "";
    }
    return stack.getHoverName().getString();
  }
}

/** Computes outstanding required amount for a request after reservation/non-exhaustive offsets. */
final class CreateShopOutstandingNeededService {
  int compute(IRequest<?> request, IDeliverable deliverable, int reservedForRequest) {
    if (request == null || deliverable == null) {
      return 0;
    }
    int needed = deliverable.getCount();
    if (deliverable instanceof INonExhaustiveDeliverable nonExhaustive) {
      needed -= nonExhaustive.getLeftOver();
    }
    return Math.max(0, needed - Math.max(0, reservedForRequest));
  }
}

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
    if (count <= 0) {
      return java.util.Collections.emptyList();
    }
    ICreateNetworkFacade network = new CreateNetworkFacade(tile);
    return network.requestItems(deliverable, count, requesterName);
  }

  int getNetworkAvailable(TileEntityCreateShop tile, IDeliverable deliverable) {
    ICreateNetworkFacade network = new CreateNetworkFacade(tile);
    return network.getAvailable(deliverable);
  }
}

/**
 * Resolves effective Create network stock count exposed to MineColonies warehouse resolver hooks.
 */
final class CreateShopWarehouseCountService {
  int getWarehouseInternalCount(
      ILocation resolverLocation,
      IRequest<? extends IDeliverable> request,
      CreateShopStockResolver stockResolver) {
    if (request == null || resolverLocation == null) {
      return 0;
    }
    IDeliverable deliverable = request.getRequest();
    if (deliverable == null) {
      return 0;
    }
    var colonyManager = com.minecolonies.api.colony.IColonyManager.getInstance();
    if (colonyManager == null) {
      return 0;
    }
    var colony =
        colonyManager.getColonyByPosFromDim(
            resolverLocation.getDimension(), resolverLocation.getInDimensionLocation());
    if (colony == null || colony.getServerBuildingManager() == null) {
      return 0;
    }
    var building =
        colony.getServerBuildingManager().getBuilding(resolverLocation.getInDimensionLocation());
    BuildingCreateShop shop = building instanceof BuildingCreateShop createShop ? createShop : null;
    if (shop == null) {
      return 0;
    }
    TileEntityCreateShop tile = shop.getCreateShopTileEntity();
    if (tile == null || tile.getStockNetworkId() == null) {
      return 0;
    }
    CreateShopBlockEntity pickup = shop.getPickupBlockEntity();
    if (pickup == null) {
      return 0;
    }
    int available = stockResolver.getNetworkAvailable(tile, deliverable);
    int reserved = pickup.getReservedForDeliverable(deliverable);
    return Math.max(0, available - reserved);
  }
}
