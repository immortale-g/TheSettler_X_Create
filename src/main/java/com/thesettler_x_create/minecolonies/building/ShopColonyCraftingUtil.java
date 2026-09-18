package com.thesettler_x_create.minecolonies.building;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.modules.ICraftingBuildingModule;
import com.thesettler_x_create.DebugLog;
import com.thesettler_x_create.TheSettlerXCreate;
import net.minecraft.world.item.ItemStack;

/**
 * Asks whether anybody in the colony could make an item, so the shop only places an order the
 * colony has a chance of filling.
 *
 * <p>This is a look ahead, not the decision: MineColonies decides for itself whether a crafter
 * takes the request, and it weighs more than this does (crafting cycles, ingredients, the recipe's
 * own conditions). Erring towards yes here only costs an order that MineColonies then hands on;
 * erring towards no would keep the colony from ever crafting for the shop.
 */
final class ShopColonyCraftingUtil {
  private ShopColonyCraftingUtil() {}

  /**
   * Whether some building in the colony knows a recipe for {@code stack} and has someone to run it.
   */
  static boolean canAnyoneCraft(IColony colony, ItemStack stack) {
    if (colony == null
        || colony.getServerBuildingManager() == null
        || stack == null
        || stack.isEmpty()) {
      return false;
    }
    for (IBuilding building : colony.getServerBuildingManager().getBuildings().values()) {
      if (building == null || building.getBuildingLevel() <= 0) {
        continue;
      }
      if (building.getAllAssignedCitizen().isEmpty()) {
        continue;
      }
      for (ICraftingBuildingModule module :
          building.getModulesByType(ICraftingBuildingModule.class)) {
        if (module == null) {
          continue;
        }
        try {
          if (module.getFirstRecipe(candidate -> ItemStack.isSameItem(candidate, stack)) != null) {
            return true;
          }
        } catch (Exception ex) {
          // A module that cannot answer is simply not a candidate, but silence here means the
          // look-ahead says "nobody can craft this" with nothing to trace it back to.
          if (DebugLog.enabled()) {
            TheSettlerXCreate.LOGGER.info(
                "[ColonyGauge] canAnyoneCraft skipped a module of {}: {}",
                building.getBuildingDisplayName(),
                ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
          }
        }
      }
    }
    return false;
  }
}
