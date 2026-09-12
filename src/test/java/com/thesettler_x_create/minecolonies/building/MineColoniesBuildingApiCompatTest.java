package com.thesettler_x_create.minecolonies.building;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.core.colony.buildings.AbstractBuilding;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Runs against whichever MineColonies is on the test classpath: the pinned version in the normal
 * build, the newest release in the latest compat check.
 */
class MineColoniesBuildingApiCompatTest {
  @Test
  void createShopOverridesEveryPickupSignatureOfTheInstalledMineColonies() throws Exception {
    List<Method> pickupMethods =
        Arrays.stream(IBuilding.class.getMethods())
            .filter(method -> method.getName().equals("createPickupRequest"))
            .toList();
    assertFalse(pickupMethods.isEmpty());

    for (Method method : pickupMethods) {
      Method declared =
          BuildingCreateShop.class.getDeclaredMethod(method.getName(), method.getParameterTypes());
      assertTrue(Modifier.isPublic(declared.getModifiers()), declared.toString());
    }
  }

  @Test
  void pickupApiDetectionMatchesTheInstalledMineColonies() {
    boolean hasQuantityForce =
        Arrays.stream(AbstractBuilding.class.getDeclaredMethods())
            .anyMatch(
                method ->
                    method.getName().equals("createPickupRequest")
                        && Arrays.equals(
                            method.getParameterTypes(), new Class<?>[] {int.class, boolean.class}));

    assertEquals(hasQuantityForce, BuildingCreateShop.SuperPickupRequest.QUANTITY_FORCE != null);
    assertEquals(!hasQuantityForce, BuildingCreateShop.SuperPickupRequest.PRIORITY != null);
  }

  @Test
  void createShopDoesNotOverrideVersionSpecificUpgradeHooks() {
    assertTrue(
        Arrays.stream(BuildingCreateShop.class.getDeclaredMethods())
            .noneMatch(method -> method.getName().equals("onUpgradeComplete")));
  }
}
