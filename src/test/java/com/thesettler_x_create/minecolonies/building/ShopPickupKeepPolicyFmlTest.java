package com.thesettler_x_create.minecolonies.building;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.minecolonies.api.crafting.ItemStorage;
import com.thesettler_x_create.blockentity.CreateShopBlockEntity;
import com.thesettler_x_create.minecolonies.tileentity.TileEntityCreateShop;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.items.IItemHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * What a warehouse pickup may carry off from the shop. Three stores meet in one inventory here: the
 * racks, which are Create's side and never leave; the reservations of the requests the shop
 * resolves; and the gauge orders waiting in the hut buffer for a package. Needs real ItemStacks, so
 * it runs with a loaded FML.
 */
@Tag("fml")
class ShopPickupKeepPolicyFmlTest {
  private static final ItemStack TORCH = new ItemStack(Items.TORCH);
  private static final ItemStack STONE = new ItemStack(Items.STONE);

  private BuildingCreateShop shop;
  private TileEntityCreateShop tile;
  private CreateShopBlockEntity pickup;
  private ShopPickupKeepPolicy policy;

  @BeforeEach
  void setUp() {
    shop = mock(BuildingCreateShop.class);
    tile = mock(TileEntityCreateShop.class);
    pickup = mock(CreateShopBlockEntity.class);
    when(shop.getCreateShopTileEntity()).thenReturn(tile);
    when(shop.getPickupBlockEntity()).thenReturn(pickup);
    policy = new ShopPickupKeepPolicy(shop);
  }

  @Test
  void whatIsNeitherRackStockNorReservedNorOwedGoesToTheWarehouse() {
    assertEquals(32, takeable(TORCH, 32));
  }

  @Test
  void rackStockNeverLeaves() {
    racksHold(TORCH, 64);

    List<ItemStorage> kept = new ArrayList<>();
    // The racks are visited first and use the keep amount up; the hut buffer behind them is free.
    assertEquals(0, policy.takeableForPickup(TORCH.copyWithCount(64), kept));
    assertEquals(10, policy.takeableForPickup(TORCH.copyWithCount(10), kept));
  }

  @Test
  void whatTheShopOwesAGaugeStaysInTheHutBuffer() {
    owesGauges(TORCH, 20);

    assertEquals(32 - 20, takeable(TORCH, 32));
  }

  @Test
  void theGaugeOrderComesOnTopOfTheRackStock() {
    racksHold(TORCH, 64);
    owesGauges(TORCH, 20);

    List<ItemStorage> kept = new ArrayList<>();
    assertEquals(0, policy.takeableForPickup(TORCH.copyWithCount(64), kept));
    assertEquals(32 - 20, policy.takeableForPickup(TORCH.copyWithCount(32), kept));
  }

  @Test
  void aGaugeOrderHoldsBackNothingOfAnotherItemKind() {
    owesGauges(TORCH, 20);

    assertEquals(16, takeable(STONE, 16));
  }

  @Test
  void aShopWithNothingToSpareDoesNotCallACourier() {
    owesGauges(TORCH, 20);

    assertFalse(policy.anythingToPickUp(inventoryOf(TORCH.copyWithCount(20))));
    assertTrue(policy.anythingToPickUp(inventoryOf(TORCH.copyWithCount(21))));
    assertFalse(policy.anythingToPickUp(null));
  }

  private int takeable(ItemStack stack, int count) {
    return policy.takeableForPickup(stack.copyWithCount(count), new ArrayList<>());
  }

  private void racksHold(ItemStack stack, int count) {
    when(tile.countInRacks(any(ItemStack.class)))
        .thenAnswer(
            invocation ->
                ItemStack.isSameItemSameComponents(invocation.getArgument(0), stack) ? count : 0);
  }

  @SuppressWarnings("unchecked")
  private void owesGauges(ItemStack stack, int amount) {
    when(shop.getOwedToGauges(any()))
        .thenAnswer(
            invocation ->
                ((Predicate<ItemStack>) invocation.getArgument(0)).test(stack) ? amount : 0);
  }

  private static IItemHandler inventoryOf(ItemStack... stacks) {
    IItemHandler handler = mock(IItemHandler.class);
    when(handler.getSlots()).thenReturn(stacks.length);
    for (int slot = 0; slot < stacks.length; slot++) {
      when(handler.getStackInSlot(slot)).thenReturn(stacks[slot]);
    }
    return handler;
  }
}
