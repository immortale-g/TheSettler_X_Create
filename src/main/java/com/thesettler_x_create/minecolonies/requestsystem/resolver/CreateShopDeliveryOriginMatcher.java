package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import com.minecolonies.api.colony.requestsystem.location.ILocation;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.requestable.deliveryman.Delivery;
import com.thesettler_x_create.blockentity.CreateShopBlockEntity;
import com.thesettler_x_create.minecolonies.building.BuildingCreateShop;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/** Utility matcher for deciding whether deliveries originate from the local Create Shop. */
final class CreateShopDeliveryOriginMatcher {
  private CreateShopDeliveryOriginMatcher() {}

  static boolean isLocalShopDeliveryChild(
      IRequest<?> childRequest, BuildingCreateShop shop, CreateShopBlockEntity pickup) {
    if (childRequest == null || shop == null || pickup == null) {
      return false;
    }
    if (!(childRequest.getRequest() instanceof Delivery delivery)) {
      return false;
    }
    ILocation start = delivery.getStart();
    Level level = pickup.getLevel();
    if (start == null || level == null || start.getDimension() == null) {
      return false;
    }
    if (!level.dimension().equals(start.getDimension())) {
      return false;
    }
    BlockPos startPos = start.getInDimensionLocation();
    if (startPos == null) {
      return false;
    }
    if (pickup.getBlockPos().equals(startPos)) {
      return true;
    }
    return shop.hasContainerPosition(startPos);
  }

  static boolean isDeliveryFromLocalShopStart(
      Delivery delivery, BuildingCreateShop shop, CreateShopBlockEntity pickup) {
    if (delivery == null || pickup == null || pickup.getLevel() == null) {
      return false;
    }
    ILocation start = delivery.getStart();
    if (start == null || start.getDimension() == null) {
      return false;
    }
    if (!pickup.getLevel().dimension().equals(start.getDimension())) {
      return false;
    }
    BlockPos startPos = start.getInDimensionLocation();
    if (startPos == null) {
      return false;
    }
    if (pickup.getBlockPos().equals(startPos)) {
      return true;
    }
    return shop != null && shop.hasContainerPosition(startPos);
  }
}
