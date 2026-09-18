package com.thesettler_x_create.minecolonies.building;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.modules.ICraftingBuildingModule;
import com.minecolonies.api.colony.managers.interfaces.IRegisteredStructureManager;
import com.minecolonies.api.crafting.IRecipeStorage;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The look ahead the gauge uses before it asks the colony to craft something. It errs towards yes,
 * but a recipe alone is not enough: an unbuilt hut or one nobody works in makes nothing. Needs real
 * ItemStacks, so it runs with a loaded FML.
 */
@Tag("fml")
class ShopColonyCraftingUtilFmlTest {
  private static final ItemStack TORCH = new ItemStack(Items.TORCH);
  private static final ItemStack COAL = new ItemStack(Items.COAL);

  private IColony colony;
  private Map<BlockPos, IBuilding> buildings;
  private int nextPos;

  @BeforeEach
  void setUp() {
    colony = mock(IColony.class);
    IRegisteredStructureManager manager = mock(IRegisteredStructureManager.class);
    buildings = new HashMap<>();
    when(colony.getServerBuildingManager()).thenReturn(manager);
    when(manager.getBuildings()).thenReturn(buildings);
  }

  @Test
  void aStaffedHutThatKnowsTheRecipeCanMakeIt() {
    addBuilding(1, 1, recipeFor(TORCH));

    assertTrue(ShopColonyCraftingUtil.canAnyoneCraft(colony, TORCH));
  }

  @Test
  void nobodyKnowsTheRecipeForSomethingElse() {
    addBuilding(1, 1, recipeFor(TORCH));

    assertFalse(ShopColonyCraftingUtil.canAnyoneCraft(colony, COAL));
  }

  @Test
  void aHutThatIsStillAConstructionSiteMakesNothing() {
    addBuilding(0, 1, recipeFor(TORCH));

    assertFalse(ShopColonyCraftingUtil.canAnyoneCraft(colony, TORCH));
  }

  @Test
  void aHutWithoutAWorkerMakesNothing() {
    addBuilding(3, 0, recipeFor(TORCH));

    assertFalse(ShopColonyCraftingUtil.canAnyoneCraft(colony, TORCH));
  }

  @Test
  @SuppressWarnings("unchecked")
  void oneHutThatCannotAnswerDoesNotHideAnotherThatCan() {
    ICraftingBuildingModule broken = mock(ICraftingBuildingModule.class);
    when(broken.getFirstRecipe(any(Predicate.class))).thenThrow(new IllegalStateException("boom"));
    addBuilding(1, 1, broken);
    addBuilding(1, 1, recipeFor(TORCH));

    assertTrue(ShopColonyCraftingUtil.canAnyoneCraft(colony, TORCH));
  }

  @Test
  void aColonyWithoutCraftersOrWithoutAnItemMakesNothing() {
    assertFalse(ShopColonyCraftingUtil.canAnyoneCraft(colony, TORCH));

    addBuilding(1, 1, recipeFor(TORCH));
    assertFalse(ShopColonyCraftingUtil.canAnyoneCraft(colony, ItemStack.EMPTY));
    assertFalse(ShopColonyCraftingUtil.canAnyoneCraft(null, TORCH));
  }

  /** A module that answers for {@code product} and for nothing else. */
  @SuppressWarnings("unchecked")
  private ICraftingBuildingModule recipeFor(ItemStack product) {
    ICraftingBuildingModule module = mock(ICraftingBuildingModule.class);
    when(module.getFirstRecipe(any(Predicate.class)))
        .thenAnswer(
            invocation -> {
              Predicate<ItemStack> wanted = invocation.getArgument(0);
              return wanted.test(product) ? mock(IRecipeStorage.class) : null;
            });
    return module;
  }

  private void addBuilding(int level, int citizens, ICraftingBuildingModule module) {
    IBuilding building = mock(IBuilding.class);
    when(building.getBuildingLevel()).thenReturn(level);
    when(building.getAllAssignedCitizen())
        .thenReturn(citizens == 0 ? Set.<ICitizenData>of() : Set.of(mock(ICitizenData.class)));
    when(building.getModulesByType(ICraftingBuildingModule.class)).thenReturn(List.of(module));
    buildings.put(new BlockPos(nextPos++, 64, 0), building);
  }
}
