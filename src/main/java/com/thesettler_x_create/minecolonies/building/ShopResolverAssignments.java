package com.thesettler_x_create.minecolonies.building;

import com.minecolonies.api.colony.requestsystem.resolver.IRequestResolver;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.api.util.constant.TypeConstants;
import com.thesettler_x_create.TheSettlerXCreate;
import com.thesettler_x_create.blockentity.CreateShopBlockEntity;
import com.thesettler_x_create.init.ModBlocks;
import com.thesettler_x_create.minecolonies.requestsystem.resolver.CreateShopRequestResolver;
import java.util.ArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Registers Create Shop resolvers and ensures pickup link blocks. */
final class ShopResolverAssignments {
  private final BuildingCreateShop shop;

  ShopResolverAssignments(BuildingCreateShop shop) {
    this.shop = shop;
  }

  void ensureDeliverableAssignment() {
    if (shop.getColony() == null) {
      return;
    }
    CreateShopRequestResolver resolver = shop.getOrCreateShopResolver();
    if (resolver == null) {
      return;
    }
    if (!(shop.getColony().getRequestManager()
        instanceof
        com.minecolonies.core.colony.requestsystem.management.IStandardRequestManager
        manager)) {
      return;
    }
    var resolverHandler = manager.getResolverHandler();
    boolean registered = false;
    try {
      resolverHandler.getResolver(resolver.getId());
      registered = true;
    } catch (IllegalArgumentException ignored) {
      // Not registered yet.
    }
    if (!registered) {
      try {
        resolverHandler.registerResolver(resolver);
        registered = true;
        if (BuildingCreateShop.isDebugRequests()) {
          TheSettlerXCreate.LOGGER.info("[CreateShop] registered resolver {}", resolver.getId());
        }
      } catch (Exception ex) {
        if (BuildingCreateShop.isDebugRequests()) {
          TheSettlerXCreate.LOGGER.info(
              "[CreateShop] resolver registration failed: {}", ex.getMessage());
        }
      }
    }
    if (!registered) {
      return;
    }
    var store = manager.getRequestableTypeRequestResolverAssignmentDataStore();
    var assignments = store.getAssignments();
    var deliverableList =
        assignments.computeIfAbsent(TypeConstants.DELIVERABLE, key -> new ArrayList<>());
    var requestableList =
        assignments.computeIfAbsent(TypeConstants.REQUESTABLE, key -> new ArrayList<>());
    var toolList = assignments.computeIfAbsent(TypeConstants.TOOL, key -> new ArrayList<>());
    ensureAssignment(deliverableList, resolver, "DELIVERABLE");
    ensureAssignment(requestableList, resolver, "REQUESTABLE");
    ensureAssignment(toolList, resolver, "TOOL");
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

  private void ensureAssignment(
      java.util.List<IToken<?>> list, IRequestResolver<?> resolver, String label) {
    if (list.contains(resolver.getId())) {
      return;
    }
    list.add(resolver.getId());
    if (BuildingCreateShop.isDebugRequests()) {
      TheSettlerXCreate.LOGGER.info(
          "[CreateShop] added resolver {} to {} assignment list", resolver.getId(), label);
    }
  }
}
