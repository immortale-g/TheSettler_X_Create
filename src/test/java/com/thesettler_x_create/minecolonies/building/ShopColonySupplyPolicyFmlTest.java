package com.thesettler_x_create.minecolonies.building;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.thesettler_x_create.create.ShopSupplyPolicy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * What the colony may take out of a shop's Create network. Needs real ItemStacks, so it runs with a
 * loaded FML.
 */
@Tag("fml")
class ShopColonySupplyPolicyFmlTest {
  private static final ItemStack TORCH = new ItemStack(Items.TORCH);
  private static final ItemStack COAL = new ItemStack(Items.COAL);

  @Test
  void withoutSettingsEverythingIsAvailable() {
    ShopSupplyPolicy policy = ShopColonySupplyPolicy.of(kind -> false, kind -> 0);

    assertEquals(100, policy.allowanceFor(TORCH, 100));
  }

  @Test
  void aBlockedItemGivesNothingAtAll() {
    ShopSupplyPolicy policy =
        ShopColonySupplyPolicy.of(kind -> ItemStack.isSameItem(kind, TORCH), kind -> 0);

    assertEquals(0, policy.allowanceFor(TORCH, 100));
    assertEquals(100, policy.allowanceFor(COAL, 100));
  }

  @Test
  void theMinimumStaysInTheNetwork() {
    ShopSupplyPolicy policy =
        ShopColonySupplyPolicy.of(
            kind -> false, kind -> ItemStack.isSameItem(kind, TORCH) ? 64 : 0);

    assertEquals(36, policy.allowanceFor(TORCH, 100));
    assertEquals(0, policy.allowanceFor(TORCH, 64));
    assertEquals(0, policy.allowanceFor(TORCH, 10));
    assertEquals(10, policy.allowanceFor(COAL, 10));
  }

  @Test
  void anEmptyKindOrAnEmptyNetworkGivesNothing() {
    ShopSupplyPolicy policy = ShopColonySupplyPolicy.of(kind -> false, kind -> 0);

    assertEquals(0, policy.allowanceFor(ItemStack.EMPTY, 100));
    assertEquals(0, policy.allowanceFor(TORCH, 0));
  }
}
