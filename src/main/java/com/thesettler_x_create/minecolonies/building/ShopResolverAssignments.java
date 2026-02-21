package com.thesettler_x_create.minecolonies.building;

import com.thesettler_x_create.blockentity.CreateShopBlockEntity;
import com.thesettler_x_create.init.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Registers Create Shop resolvers and ensures pickup link blocks. */
final class ShopResolverAssignments {
  private final BuildingCreateShop shop;

  ShopResolverAssignments(BuildingCreateShop shop) {
    this.shop = shop;
  }

  void ensurePickupLink() {
    Level level = shop.getColony() == null ? null : shop.getColony().getWorld();
    if (level == null) {
      return;
    }
    BlockPos pickupPos = shop.getPickupPos();
    if (pickupPos != null) {
      BlockEntity existing = level.getBlockEntity(pickupPos);
      if (existing instanceof CreateShopBlockEntity shopBlock) {
        if (shopBlock.getShopPos() == null) {
          shopBlock.setShopPos(shop.getLocation().getInDimensionLocation());
        }
        return;
      }
      shop.setPickupPos(null);
    }
    BlockPos hutPos = shop.getLocation().getInDimensionLocation();
    // First, try to discover an existing pickup block nearby.
    int radius = 2;
    for (int dx = -radius; dx <= radius; dx++) {
      for (int dy = 0; dy <= 2; dy++) {
        for (int dz = -radius; dz <= radius; dz++) {
          BlockPos candidate = hutPos.offset(dx, dy, dz);
          BlockEntity entity = level.getBlockEntity(candidate);
          if (entity instanceof CreateShopBlockEntity shopBlock) {
            shop.setPickupPos(candidate);
            if (shopBlock.getShopPos() == null) {
              shopBlock.setShopPos(hutPos);
            }
            return;
          }
        }
      }
    }
    BlockPos above = hutPos.above();
    if (level.isEmptyBlock(above)) {
      level.setBlockAndUpdate(above, ModBlocks.CREATE_SHOP_PICKUP.get().defaultBlockState());
    }
    if (level.getBlockState(above).is(ModBlocks.CREATE_SHOP_PICKUP.get())) {
      shop.setPickupPos(above);
    }
    pickupPos = shop.getPickupPos();
    if (pickupPos == null) {
      return;
    }
    BlockEntity entity = level.getBlockEntity(pickupPos);
    if (entity instanceof CreateShopBlockEntity shopBlock) {
      if (shopBlock.getShopPos() == null) {
        shopBlock.setShopPos(shop.getLocation().getInDimensionLocation());
      }
    }
  }
}
