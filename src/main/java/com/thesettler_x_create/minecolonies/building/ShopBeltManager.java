package com.thesettler_x_create.minecolonies.building;

import com.thesettler_x_create.TheSettlerXCreate;
import net.minecraft.resources.ResourceLocation;

/** Handles belt blueprint setup/repair state for the Create Shop. */
final class ShopBeltManager {
  private final BuildingCreateShop shop;
  private boolean beltRebuildPending;

  ShopBeltManager(BuildingCreateShop shop) {
    this.shop = shop;
    this.beltRebuildPending = false;
  }

  void markPending() {
    beltRebuildPending = true;
  }

  void clearPending() {
    beltRebuildPending = false;
  }

  void onPlacement() {
    markPending();
    shop.trySpawnBeltBlueprint(shop.getColony());
  }

  void onUpgrade() {
    markPending();
    shop.trySpawnBeltBlueprint(shop.getColony());
  }

  void onRepair() {
    markPending();
  }

  void tick() {
    if (!beltRebuildPending) {
      return;
    }
    if (!shop.isBuilt() || shop.hasActiveWorkOrder(shop.getColony())) {
      return;
    }
    if (shop.trySpawnBeltBlueprint(shop.getColony())) {
      beltRebuildPending = false;
    }
  }

  static ResourceLocation beltBlueprintL1() {
    return ResourceLocation.fromNamespaceAndPath(
        TheSettlerXCreate.MODID, "blueprints_internal/createshop1_belt.blueprint");
  }

  static ResourceLocation beltBlueprintL2() {
    return ResourceLocation.fromNamespaceAndPath(
        TheSettlerXCreate.MODID, "blueprints_internal/createshop2_belt.blueprint");
  }
}
