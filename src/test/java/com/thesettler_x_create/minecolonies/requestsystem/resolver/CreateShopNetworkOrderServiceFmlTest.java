package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.minecolonies.api.colony.requestsystem.requestable.IDeliverable;
import com.thesettler_x_create.blockentity.CreateShopBlockEntity;
import com.thesettler_x_create.minecolonies.tileentity.TileEntityCreateShop;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntSupplier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * What the shop actually orders from the Create network for a request. The point of the service is
 * that nothing is ordered twice: goods already on their way for the request count first, goods on
 * their way for nobody are taken over next, and only the rest is ordered. Needs real ItemStacks, so
 * it runs with a loaded FML.
 */
@Tag("fml")
class CreateShopNetworkOrderServiceFmlTest {
  private static final int MISSING = 32;
  private static final String REQUESTER = "Bob";

  private final CreateShopStockResolver stockResolver = mock(CreateShopStockResolver.class);
  private final CreateShopNetworkOrderService service =
      new CreateShopNetworkOrderService(stockResolver);

  private TileEntityCreateShop tile;
  private CreateShopBlockEntity pickup;
  private IDeliverable deliverable;
  private UUID requestId;
  private AtomicInteger networkScans;
  private IntSupplier network;

  @BeforeEach
  void setUp() {
    tile = mock(TileEntityCreateShop.class);
    pickup = mock(CreateShopBlockEntity.class);
    deliverable = mock(IDeliverable.class);
    requestId = UUID.randomUUID();
    networkScans = new AtomicInteger();
    network = networkHolding(64);
  }

  @Test
  void withNothingOnItsWayTheWholeMissingAmountIsOrdered() {
    List<ItemStack> ordered = networkAnswers(MISSING);

    CreateShopNetworkOrderService.OrderResult result = order(MISSING);

    assertEquals(ordered, result.ordered());
    assertEquals(MISSING, result.orderedCount());
    assertEquals(0, result.ownInflight());
    assertEquals(0, result.claimed());
    assertTrue(result.somethingOnItsWay());
    verify(stockResolver).requestFromNetwork(tile, deliverable, MISSING, REQUESTER, requestId);
  }

  @Test
  void theOrderCarriesTheRequestIdSoTheGoodsCountAsOnItsWayRightAfterIt() {
    // The order is booked as inflight under this id the moment it is queued, so the very next
    // decision of the same request sees it and orders nothing more.
    networkAnswers(MISSING);
    order(MISSING);
    verify(stockResolver).requestFromNetwork(tile, deliverable, MISSING, REQUESTER, requestId);
    int scansAfterOrdering = networkScans.get();

    onItsWayForTheRequest(MISSING);
    CreateShopNetworkOrderService.OrderResult second = order(MISSING);

    assertTrue(second.ordered().isEmpty());
    assertEquals(MISSING, second.ownInflight());
    assertTrue(second.somethingOnItsWay());
    verify(stockResolver, times(1))
        .requestFromNetwork(any(), any(), anyInt(), anyString(), any(UUID.class));
    assertEquals(scansAfterOrdering, networkScans.get());
  }

  @Test
  void whatIsAlreadyOnItsWayForTheRequestIsNotOrderedAgain() {
    onItsWayForTheRequest(20);
    networkAnswers(12);

    CreateShopNetworkOrderService.OrderResult result = order(MISSING);

    assertEquals(20, result.ownInflight());
    assertEquals(12, result.orderedCount());
    verify(stockResolver).requestFromNetwork(tile, deliverable, 12, REQUESTER, requestId);
  }

  @Test
  void goodsNobodyOwnsAreTakenOverBeforeAnythingIsOrdered() {
    onItsWayForNobody(12);
    networkAnswers(20);

    CreateShopNetworkOrderService.OrderResult result = order(MISSING);

    assertEquals(12, result.claimed());
    assertEquals(20, result.orderedCount());
    verify(pickup).claimFreeInflight(any(UUID.class), any(), anyInt());
    verify(stockResolver).requestFromNetwork(tile, deliverable, 20, REQUESTER, requestId);
  }

  @Test
  void takenOverGoodsThatCoverTheRestSkipTheNetworkEntirely() {
    onItsWayForNobody(MISSING);

    CreateShopNetworkOrderService.OrderResult result = order(MISSING);

    assertEquals(MISSING, result.claimed());
    assertTrue(result.ordered().isEmpty());
    assertTrue(result.somethingOnItsWay());
    assertEquals(0, networkScans.get());
    verify(stockResolver, never())
        .requestFromNetwork(any(), any(), anyInt(), anyString(), any(UUID.class));
  }

  @Test
  void withEverythingOnItsWayTheNetworkIsNotEvenAsked() {
    onItsWayForTheRequest(MISSING);

    CreateShopNetworkOrderService.OrderResult result = order(MISSING);

    assertEquals(MISSING, result.ownInflight());
    assertEquals(0, result.claimed());
    assertTrue(result.ordered().isEmpty());
    assertEquals(0, networkScans.get());
    verify(pickup, never()).claimFreeInflight(any(), any(), anyInt());
    verify(stockResolver, never())
        .requestFromNetwork(any(), any(), anyInt(), anyString(), any(UUID.class));
  }

  @Test
  void theOrderIsCutDownToWhatTheNetworkHolds() {
    network = networkHolding(10);
    networkAnswers(10);

    CreateShopNetworkOrderService.OrderResult result = order(MISSING);

    assertEquals(10, result.orderedCount());
    assertEquals(1, networkScans.get());
    verify(stockResolver).requestFromNetwork(tile, deliverable, 10, REQUESTER, requestId);
  }

  @Test
  void anEmptyNetworkOrdersNothingAndLeavesNothingOnItsWay() {
    network = networkHolding(0);

    CreateShopNetworkOrderService.OrderResult result = order(MISSING);

    assertTrue(result.ordered().isEmpty());
    assertFalse(result.somethingOnItsWay());
    verify(stockResolver, never())
        .requestFromNetwork(any(), any(), anyInt(), anyString(), any(UUID.class));
  }

  @Test
  void aShopWithoutItsPickupBlockOrdersNothing() {
    CreateShopNetworkOrderService.OrderResult result =
        service.orderMissing(tile, null, deliverable, requestId, MISSING, network, REQUESTER);

    assertTrue(result.ordered().isEmpty());
    assertFalse(result.somethingOnItsWay());
    assertEquals(0, networkScans.get());
    verifyNoInteractions(stockResolver);
  }

  private CreateShopNetworkOrderService.OrderResult order(int missing) {
    return service.orderMissing(tile, pickup, deliverable, requestId, missing, network, REQUESTER);
  }

  private void onItsWayForTheRequest(int amount) {
    when(pickup.getInflightRemainingFor(any(UUID.class), any())).thenReturn(amount);
  }

  private void onItsWayForNobody(int amount) {
    when(pickup.claimFreeInflight(any(UUID.class), any(), anyInt()))
        .thenAnswer(invocation -> Math.min(amount, (int) invocation.getArgument(2)));
  }

  private List<ItemStack> networkAnswers(int amount) {
    List<ItemStack> ordered = List.of(new ItemStack(Items.OAK_LOG, amount));
    when(stockResolver.requestFromNetwork(tile, deliverable, amount, REQUESTER, requestId))
        .thenReturn(ordered);
    return ordered;
  }

  private IntSupplier networkHolding(int amount) {
    return () -> {
      networkScans.incrementAndGet();
      return amount;
    };
  }
}
