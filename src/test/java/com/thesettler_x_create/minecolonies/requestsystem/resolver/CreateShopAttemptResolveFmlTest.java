package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.minecolonies.api.colony.requestsystem.manager.IRequestManager;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.request.RequestState;
import com.minecolonies.api.colony.requestsystem.requestable.IDeliverable;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.api.colony.requestsystem.token.StandardToken;
import com.minecolonies.api.util.Tuple;
import com.minecolonies.core.colony.requestsystem.management.IStandardRequestManager;
import com.thesettler_x_create.blockentity.CreateShopBlockEntity;
import com.thesettler_x_create.minecolonies.building.BuildingCreateShop;
import com.thesettler_x_create.minecolonies.tileentity.TileEntityCreateShop;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Pins what attemptResolve does for each stock situation: which calls it makes on its
 * collaborators, what it reserves and what it returns. Written against the method before it was
 * split up, so the restructured version has to behave the same.
 */
@Tag("fml")
class CreateShopAttemptResolveFmlTest {
  private static final int NEEDED = 32;

  private final CreateShopRequestStateMutatorService mutator =
      mock(CreateShopRequestStateMutatorService.class);
  private final CreateShopResolverMessaging messaging = mock(CreateShopResolverMessaging.class);
  private final CreateShopDeliveryManager deliveryManager = mock(CreateShopDeliveryManager.class);
  private final CreateShopOutstandingNeededService outstanding =
      mock(CreateShopOutstandingNeededService.class);
  private final CreateShopResolverCooldown cooldown = mock(CreateShopResolverCooldown.class);
  private final CreateShopResolverChain chain = mock(CreateShopResolverChain.class);
  private final CreateShopResolverPlanning planning = mock(CreateShopResolverPlanning.class);
  private final CreateShopStockResolver stockResolver = mock(CreateShopStockResolver.class);
  private final CreateShopResolverDiagnostics diagnostics =
      mock(CreateShopResolverDiagnostics.class);
  private final CreateShopRequestStateMachine flow = mock(CreateShopRequestStateMachine.class);
  private final CreateShopNetworkOrderService networkOrders =
      mock(CreateShopNetworkOrderService.class);

  private final CreateShopAttemptResolveService service =
      new CreateShopAttemptResolveService(
          mutator,
          messaging,
          deliveryManager,
          outstanding,
          cooldown,
          chain,
          planning,
          stockResolver,
          diagnostics,
          flow,
          networkOrders);

  private CreateShopRequestResolver resolver;
  private IStandardRequestManager manager;
  private Level level;
  private IRequest<IDeliverable> request;
  private IDeliverable deliverable;
  private BuildingCreateShop shop;
  private TileEntityCreateShop tile;
  private CreateShopBlockEntity pickup;
  private UUID requestId;

  @BeforeEach
  @SuppressWarnings({"unchecked", "rawtypes"})
  void setUp() {
    resolver = mock(CreateShopRequestResolver.class);
    manager = mock(IStandardRequestManager.class, RETURNS_DEEP_STUBS);
    level = mock(Level.class);
    when(manager.getColony().getWorld()).thenReturn(level);

    requestId = UUID.randomUUID();
    // Real tokens: with FML loaded debug logging is on, and Log4j cannot format a mocked token.
    IToken<UUID> token = new StandardToken(requestId);
    deliverable = mock(IDeliverable.class);
    request = (IRequest<IDeliverable>) mock(IRequest.class);
    when(request.getId()).thenReturn((IToken) token);
    when(request.getRequest()).thenReturn(deliverable);
    when(request.getState()).thenReturn(RequestState.IN_PROGRESS);

    shop = mock(BuildingCreateShop.class);
    tile = mock(TileEntityCreateShop.class);
    pickup = mock(CreateShopBlockEntity.class);
    when(resolver.getShop(manager)).thenReturn(shop);
    when(shop.getCreateShopTileEntity()).thenReturn(tile);
    when(tile.getStockNetworkId()).thenReturn(UUID.randomUUID());
    when(shop.getPickupBlockEntity()).thenReturn(pickup);
    when(pickup.getLevel()).thenReturn(level);
    when(shop.isWorkerWorking()).thenReturn(true);
    when(outstanding.compute(any(), any(), anyInt())).thenReturn(NEEDED);
    when(messaging.resolveRequesterName(any(), any())).thenReturn("Bob");
    when(planning.extractStacks(any()))
        .thenAnswer(
            inv -> {
              List<Tuple<ItemStack, BlockPos>> tuples = inv.getArgument(0);
              List<ItemStack> stacks = new ArrayList<>();
              tuples.forEach(t -> stacks.add(t.getA()));
              return stacks;
            });
  }

  @Test
  void cancelledRequestIsSkipped() {
    when(resolver.isCancelledRequest(request.getId())).thenReturn(true);

    assertTrue(attempt().isEmpty());
    verify(stockResolver, never()).getAvailability(any(), any(), any(), anyInt(), any());
  }

  @Test
  void requestOnCooldownIsSkipped() {
    when(cooldown.isRequestOnCooldown(level, request.getId())).thenReturn(true);

    assertTrue(attempt().isEmpty());
    verify(stockResolver, never()).getAvailability(any(), any(), any(), anyInt(), any());
  }

  @Test
  void requestWithChildrenIsSkipped() {
    when(request.hasChildren()).thenReturn(true);

    assertTrue(attempt().isEmpty());
    verify(stockResolver, never()).getAvailability(any(), any(), any(), anyInt(), any());
  }

  @Test
  void nothingNeededIsSkipped() {
    when(outstanding.compute(any(), any(), anyInt())).thenReturn(0);

    assertTrue(attempt().isEmpty());
    verify(stockResolver, never()).getAvailability(any(), any(), any(), anyInt(), any());
    verify(mutator, never()).markOrderedWithPending(any(), any(), any(), anyInt());
  }

  @Test
  void nothingAvailableWaitsWithTheFullNeedPending() {
    stock(0, 0);

    assertTrue(attempt().isEmpty());
    verify(mutator).markOrderedWithPending(resolver, level, request.getId(), NEEDED);
    verify(networkOrders, never())
        .orderMissing(any(), any(), any(), any(), anyInt(), any(), anyString());
    verify(pickup, never()).reserve(any(), any(), anyInt());
  }

  @Test
  void rackCoveringEverythingCreatesDeliveriesWithoutReserving() {
    stock(0, 40);
    List<Tuple<ItemStack, BlockPos>> planned = rackPlan(NEEDED);
    List<IToken<?>> created = List.of(new StandardToken(UUID.randomUUID()));
    when(deliveryManager.createDeliveriesFromStacks(manager, request, planned, pickup))
        .thenReturn(created);

    assertEquals(created, attempt());
    verify(networkOrders, never())
        .orderMissing(any(), any(), any(), any(), anyInt(), any(), anyString());
    verify(pickup, never()).reserve(any(), any(), anyInt());
    verify(resolver)
        .transitionFlow(
            eq(manager),
            eq(request),
            eq(CreateShopFlowState.DELIVERY_CREATED),
            anyString(),
            anyString(),
            eq(NEEDED),
            anyString());
  }

  @Test
  void rackCoveringEverythingDefersBehindAWrappedManager() {
    IRequestManager wrapped = mock(IRequestManager.class, RETURNS_DEEP_STUBS);
    when(wrapped.getColony().getWorld()).thenReturn(level);
    when(resolver.getShop(wrapped)).thenReturn(shop);
    stock(0, 40);
    rackPlan(NEEDED);

    assertTrue(service.attemptResolve(resolver, wrapped, request).isEmpty());
    verify(mutator).markOrderedWithPendingAtLeastOne(resolver, level, request.getId(), NEEDED);
    verify(deliveryManager, never()).createDeliveriesFromStacks(any(), any(), any(), any());
    verify(pickup, never()).reserve(any(), any(), anyInt());
  }

  @Test
  void rackPartAndNetworkRemainderOrdersTheRestAndReservesTheRackPart() {
    stock(100, 12);
    rackPlan(12);
    List<ItemStack> ordered = List.of(new ItemStack(Items.OAK_LOG, 20));
    order(20, new CreateShopNetworkOrderService.OrderResult(ordered, 0, 0));

    assertTrue(attempt().isEmpty());
    verify(mutator).markOrderedWithPendingAtLeastOne(resolver, level, request.getId(), NEEDED);
    verify(messaging)
        .sendShopChat(manager, "com.thesettler_x_create.message.createshop.request_sent", ordered);
    verify(pickup).reserve(eq(requestId), any(), eq(12));
    verify(deliveryManager, never()).createDeliveriesFromStacks(any(), any(), any(), any());
  }

  @Test
  void networkOnlyOrderReservesNothing() {
    stock(100, 0);
    rackPlan(0);
    List<ItemStack> ordered = List.of(new ItemStack(Items.OAK_LOG, NEEDED));
    order(NEEDED, new CreateShopNetworkOrderService.OrderResult(ordered, 0, 0));

    assertTrue(attempt().isEmpty());
    verify(mutator).markOrderedWithPendingAtLeastOne(resolver, level, request.getId(), NEEDED);
    verify(messaging)
        .sendShopChat(manager, "com.thesettler_x_create.message.createshop.request_sent", ordered);
    verify(pickup, never()).reserve(any(), any(), anyInt());
  }

  @Test
  void rackPartWithTheRestAlreadyOnItsWayReservesWithoutChat() {
    stock(100, 12);
    rackPlan(12);
    order(20, new CreateShopNetworkOrderService.OrderResult(List.of(), 20, 0));

    assertTrue(attempt().isEmpty());
    verify(mutator).markOrderedWithPendingAtLeastOne(resolver, level, request.getId(), NEEDED);
    verify(messaging, never()).sendShopChat(any(), anyString(), anyList());
    verify(pickup).reserve(eq(requestId), any(), eq(12));
  }

  @Test
  void everythingAlreadyOnItsWayWaits() {
    stock(100, 0);
    rackPlan(0);
    order(NEEDED, new CreateShopNetworkOrderService.OrderResult(List.of(), NEEDED, 0));

    assertTrue(attempt().isEmpty());
    verify(mutator).markOrderedWithPendingAtLeastOne(resolver, level, request.getId(), NEEDED);
    verify(pickup, never()).reserve(any(), any(), anyInt());
  }

  @Test
  void networkOrderThatFindsNothingLeavesThePendingStateAlone() {
    stock(100, 0);
    rackPlan(0);
    order(NEEDED, new CreateShopNetworkOrderService.OrderResult(List.of(), 0, 0));

    assertTrue(attempt().isEmpty());
    verify(mutator, never()).markOrderedWithPending(any(), any(), any(), anyInt());
    verify(mutator, never()).markOrderedWithPendingAtLeastOne(any(), any(), any(), anyInt());
    verify(pickup, never()).reserve(any(), any(), anyInt());
  }

  @Test
  void idleShopkeeperDoesNotUseTheNetwork() {
    when(shop.isWorkerWorking()).thenReturn(false);
    stock(100, 0);

    assertTrue(attempt().isEmpty());
    verify(mutator).markOrderedWithPending(resolver, level, request.getId(), NEEDED);
    verify(networkOrders, never())
        .orderMissing(any(), any(), any(), any(), anyInt(), any(), anyString());
  }

  @Test
  void rackPlanShortOfTheRackStockWithAnIdleShopkeeperReservesWhatWasPlanned() {
    // The racks report 20 usable, but planning finds only 6 (stock moved in between). With the
    // shopkeeper idle nothing is ordered; the planned part is still reserved.
    when(shop.isWorkerWorking()).thenReturn(false);
    stock(0, 20);
    rackPlan(6);

    assertTrue(attempt().isEmpty());
    verify(networkOrders, never())
        .orderMissing(any(), any(), any(), any(), anyInt(), any(), anyString());
    verify(mutator).markOrderedWithPendingAtLeastOne(resolver, level, request.getId(), NEEDED);
    verify(pickup).reserve(eq(requestId), any(), eq(6));
    verify(deliveryManager, never()).createDeliveriesFromStacks(any(), any(), any(), any());
  }

  private List<IToken<?>> attempt() {
    return service.attemptResolve(resolver, manager, request);
  }

  private void stock(int network, int rackUsable) {
    when(stockResolver.getAvailability(eq(tile), eq(pickup), eq(deliverable), anyInt(), any()))
        .thenReturn(
            new CreateShopStockSnapshot(network, rackUsable, 0, rackUsable, network + rackUsable));
  }

  private List<Tuple<ItemStack, BlockPos>> rackPlan(int count) {
    List<Tuple<ItemStack, BlockPos>> planned =
        count > 0
            ? new ArrayList<>(
                ImmutableList.of(new Tuple<>(new ItemStack(Items.OAK_LOG, count), BlockPos.ZERO)))
            : new ArrayList<>();
    when(planning.planFromRacksWithPositions(eq(tile), eq(deliverable), anyInt()))
        .thenReturn(planned);
    return planned;
  }

  private void order(int missing, CreateShopNetworkOrderService.OrderResult result) {
    when(networkOrders.orderMissing(
            eq(tile), eq(pickup), eq(deliverable), eq(requestId), eq(missing), any(), anyString()))
        .thenReturn(result);
  }
}
