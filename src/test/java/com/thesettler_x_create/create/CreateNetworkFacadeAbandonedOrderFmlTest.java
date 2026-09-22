package com.thesettler_x_create.create;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.thesettler_x_create.blockentity.CreateShopBlockEntity;
import com.thesettler_x_create.minecolonies.building.BuildingCreateShop;
import com.thesettler_x_create.minecolonies.tileentity.TileEntityCreateShop;
import java.util.List;
import java.util.UUID;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * What happens to an order the network never took. An order counts as on its way from the moment it
 * is queued, so a broadcast the queue finally gives up on has to take exactly that amount back off
 * the books: the request sees the need again on its next tick, while a newer order of the same
 * request that did go out stays. Needs real ItemStacks, so it runs with a loaded FML.
 */
@Tag("fml")
class CreateNetworkFacadeAbandonedOrderFmlTest {
  private static final String REQUESTER = "Bob";
  private static final String ADDRESS = "shop-one";

  private TileEntityCreateShop shop;
  private CreateShopBlockEntity pickup;
  private CreateNetworkFacade facade;

  @BeforeEach
  void setUp() {
    shop = mock(TileEntityCreateShop.class);
    BuildingCreateShop building = mock(BuildingCreateShop.class);
    pickup = mock(CreateShopBlockEntity.class);
    when(shop.getBuilding()).thenAnswer(invocation -> building);
    when(shop.getShopAddress()).thenReturn(ADDRESS);
    when(building.getPickupBlockEntity()).thenReturn(pickup);
    facade = new CreateNetworkFacade(shop);
  }

  @Test
  void anAbandonedOrderIsTakenOffTheBooksOfExactlyThatRequest() {
    UUID requestId = UUID.randomUUID();
    ItemStack logs = new ItemStack(Items.OAK_LOG, 24);

    facade.forgetAbandonedOrder(requestId, REQUESTER, List.of(logs));

    verify(pickup).cancelInflight(requestId, logs, 24);
    // Not the request's other orders: one that did go out is still on its way.
    verify(pickup, never()).cancelInflightByUuid(any());
    verify(pickup, never()).consumeInflight(any(), anyInt(), anyString(), anyString(), anyLong());
  }

  @Test
  void everyStackOfTheAbandonedBucketIsDroppedWithItsOwnAmount() {
    UUID requestId = UUID.randomUUID();
    ItemStack logs = new ItemStack(Items.OAK_LOG, 24);
    ItemStack torches = new ItemStack(Items.TORCH, 8);

    facade.forgetAbandonedOrder(requestId, REQUESTER, List.of(logs, torches));

    verify(pickup).cancelInflight(requestId, logs, 24);
    verify(pickup).cancelInflight(requestId, torches, 8);
  }

  @Test
  void anOrderOfTheShopItselfIsDroppedByRequesterAndAddress() {
    ItemStack logs = new ItemStack(Items.OAK_LOG, 24);

    facade.forgetAbandonedOrder(null, REQUESTER, List.of(logs));

    verify(pickup).consumeInflight(logs, 24, REQUESTER, ADDRESS, -1L);
    verify(pickup, never()).cancelInflight(any(), any(), anyInt());
  }

  @Test
  void anEmptyBucketDropsNothing() {
    facade.forgetAbandonedOrder(UUID.randomUUID(), REQUESTER, List.of());

    verifyNoInteractions(pickup);
  }

  @Test
  void anEmptyStackInTheBucketIsSkipped() {
    UUID requestId = UUID.randomUUID();
    ItemStack logs = new ItemStack(Items.OAK_LOG, 24);

    facade.forgetAbandonedOrder(requestId, REQUESTER, List.of(ItemStack.EMPTY, logs));

    verify(pickup).cancelInflight(requestId, logs, 24);
    verify(pickup, never()).cancelInflight(eq(requestId), eq(ItemStack.EMPTY), anyInt());
  }
}
