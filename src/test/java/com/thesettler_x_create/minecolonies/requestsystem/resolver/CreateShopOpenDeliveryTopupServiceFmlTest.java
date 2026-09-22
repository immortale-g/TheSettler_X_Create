package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.minecolonies.api.colony.requestsystem.location.ILocation;
import com.minecolonies.api.colony.requestsystem.management.IRequestHandler;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.request.RequestState;
import com.minecolonies.api.colony.requestsystem.requestable.IDeliverable;
import com.minecolonies.api.colony.requestsystem.requestable.deliveryman.Delivery;
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
import org.mockito.ArgumentCaptor;

/**
 * What a request may still do while some of its deliveries are open. The point is that open
 * deliveries are goods the request already has coming: they count as covered when reserving and
 * ordering, so nothing goes out twice, while stock that arrived in the meantime still turns into a
 * new delivery instead of waiting for the open ones. Needs real ItemStacks, so it runs with a
 * loaded FML.
 */
@Tag("fml")
class CreateShopOpenDeliveryTopupServiceFmlTest {
  private static final BlockPos PICKUP = new BlockPos(4, 64, 4);
  private static final BlockPos ELSEWHERE = new BlockPos(400, 64, 400);
  private static final String REQUEST_LOG = "req#1";

  private final CreateShopOutstandingNeededService outstanding =
      mock(CreateShopOutstandingNeededService.class);
  private final CreateShopResolverPlanning planning = mock(CreateShopResolverPlanning.class);
  private final CreateShopNetworkOrderService networkOrders =
      mock(CreateShopNetworkOrderService.class);
  private final CreateShopStockResolver stockResolver = mock(CreateShopStockResolver.class);
  private final CreateShopDeliveryManager deliveryManager = mock(CreateShopDeliveryManager.class);
  private final CreateShopPostCreationUpdateService postCreation =
      mock(CreateShopPostCreationUpdateService.class);
  private final CreateShopRequestStateMutatorService mutator =
      mock(CreateShopRequestStateMutatorService.class);
  private final CreateShopResolverMessaging messaging = mock(CreateShopResolverMessaging.class);
  private final CreateShopResolverDiagnostics diagnostics =
      mock(CreateShopResolverDiagnostics.class);

  private final CreateShopOpenDeliveryTopupService service =
      new CreateShopOpenDeliveryTopupService(
          outstanding,
          planning,
          networkOrders,
          stockResolver,
          deliveryManager,
          postCreation,
          mutator,
          messaging,
          diagnostics);

  private CreateShopRequestResolver resolver;
  private IStandardRequestManager manager;
  private IRequestHandler requestHandler;
  private Level level;
  private BuildingCreateShop shop;
  private TileEntityCreateShop tile;
  private CreateShopBlockEntity pickup;
  private IDeliverable deliverable;
  private IRequest<?> request;
  private IToken<?> requestToken;
  private UUID requestId;
  private List<IToken<?>> children;

  @BeforeEach
  void setUp() {
    resolver = mock(CreateShopRequestResolver.class);
    manager = mock(IStandardRequestManager.class);
    requestHandler = mock(IRequestHandler.class);
    level = mock(Level.class);
    shop = mock(BuildingCreateShop.class);
    tile = mock(TileEntityCreateShop.class);
    pickup = mock(CreateShopBlockEntity.class);
    deliverable = mock(IDeliverable.class);
    children = new ArrayList<>();

    requestId = UUID.randomUUID();
    requestToken = new StandardToken(requestId);
    request = mock(IRequest.class);
    IToken<?> token = requestToken;
    when(request.getId()).thenAnswer(invocation -> token);
    List<IToken<?>> childList = children;
    when(request.getChildren()).thenAnswer(invocation -> ImmutableList.copyOf(childList));

    when(level.dimension()).thenReturn(Level.OVERWORLD);
    when(level.getGameTime()).thenReturn(1000L);
    when(pickup.getLevel()).thenReturn(level);
    when(pickup.getBlockPos()).thenReturn(PICKUP);
    when(deliverable.matches(any())).thenReturn(true);
    when(messaging.resolveRequesterName(any(), any())).thenReturn("Bob");
    when(planning.extractStacks(any()))
        .thenAnswer(
            invocation -> {
              List<Tuple<ItemStack, BlockPos>> tuples = invocation.getArgument(0);
              List<ItemStack> stacks = new ArrayList<>();
              tuples.forEach(tuple -> stacks.add(tuple.getA()));
              return stacks;
            });
    when(planning.countPlanned(any()))
        .thenAnswer(
            invocation -> {
              List<Tuple<ItemStack, BlockPos>> tuples = invocation.getArgument(0);
              return tuples.stream().mapToInt(tuple -> tuple.getA().getCount()).sum();
            });
    needs(64);
    racksHold(0);
  }

  @Test
  void openDeliveriesCountAsCoveredSoNothingIsOrderedTwice() {
    needs(32);
    racksHold(64);
    openDelivery(32, PICKUP, false);

    process();

    verify(pickup, never()).reserve(any(), any(), anyInt());
    verify(networkOrders, never())
        .orderMissing(any(), any(), any(), any(), anyInt(), any(), anyString());
    verify(deliveryManager, never()).createDeliveriesFromStacks(any(), any(), any(), any());
  }

  @Test
  void onlyWhatTheOpenDeliveriesDoNotCoverIsReservedFromTheRacks() {
    racksHold(64);
    openDelivery(32, PICKUP, false);
    rackPlan(32);

    process();

    verify(pickup).reserve(eq(requestId), any(), eq(32));
    verify(networkOrders, never())
        .orderMissing(any(), any(), any(), any(), anyInt(), any(), anyString());
    verify(deliveryManager, never()).createDeliveriesFromStacks(any(), any(), any(), any());
  }

  @Test
  void aDeliveryAlreadyPickedUpFreesItsReservationForANewDelivery() {
    racksHold(32);
    reserved(32);
    openDelivery(32, PICKUP, true);
    List<Tuple<ItemStack, BlockPos>> planned = rackPlan(32);
    List<IToken<?>> created = List.of(new StandardToken(UUID.randomUUID()));
    when(deliveryManager.createDeliveriesFromStacks(manager, request, planned, pickup))
        .thenReturn(created);

    process();

    verify(deliveryManager).createDeliveriesFromStacks(manager, request, planned, pickup);
    assertEquals(0, creationResult().remainingCount());
  }

  @Test
  void aDeliveryNobodyPickedUpYetHoldsItsReservationBack() {
    racksHold(32);
    reserved(32);
    openDelivery(32, PICKUP, false);
    rackPlan(32);

    process();

    verify(deliveryManager, never()).createDeliveriesFromStacks(any(), any(), any(), any());
    verifyNoInteractions(postCreation);
  }

  @Test
  void theRestBesideTheOpenDeliveriesIsOrderedFromTheNetworkAndAnnounced() {
    openDelivery(32, PICKUP, false);
    List<ItemStack> ordered = List.of(new ItemStack(Items.OAK_LOG, 32));
    order(32, new CreateShopNetworkOrderService.OrderResult(ordered, 0, 0));

    process();

    verify(messaging)
        .sendShopChat(manager, "com.thesettler_x_create.message.createshop.request_sent", ordered);
    verify(diagnostics).recordPendingSource(requestToken, "tickPending:open-delivery-topup");
    verify(mutator).markOrderedWithPendingAtLeastOne(resolver, level, requestToken, 32);
  }

  @Test
  void whatIsAlreadyOnItsWayIsNotAnnouncedAgain() {
    openDelivery(32, PICKUP, false);
    order(32, new CreateShopNetworkOrderService.OrderResult(List.of(), 32, 0));

    process();

    verify(messaging, never()).sendShopChat(any(), anyString(), anyList());
    verify(mutator).markOrderedWithPendingAtLeastOne(resolver, level, requestToken, 32);
  }

  @Test
  void aFinishedDeliveryChildNoLongerCountsAsCoverage() {
    racksHold(64);
    openDelivery(32, PICKUP, false).completed();
    rackPlan(64);

    process();

    verify(pickup).reserve(eq(requestId), any(), eq(64));
  }

  @Test
  void aDeliveryStartingAtAnotherBuildingIsNotOurs() {
    racksHold(64);
    openDelivery(32, ELSEWHERE, false);
    rackPlan(64);

    process();

    verify(pickup).reserve(eq(requestId), any(), eq(64));
  }

  @Test
  void anIdleShopkeeperTopsUpNothing() {
    racksHold(64);
    openDelivery(32, PICKUP, false);

    service.process(
        resolver,
        manager,
        requestHandler,
        request,
        level,
        shop,
        tile,
        pickup,
        deliverable,
        false,
        REQUEST_LOG);

    verifyNoInteractions(planning);
    verify(pickup, never()).reserve(any(), any(), anyInt());
  }

  @Test
  void aCoveredNeedTouchesNothing() {
    needs(0);

    process();

    verifyNoInteractions(planning);
    verify(networkOrders, never())
        .orderMissing(any(), any(), any(), any(), anyInt(), any(), anyString());
  }

  private void process() {
    service.process(
        resolver,
        manager,
        requestHandler,
        request,
        level,
        shop,
        tile,
        pickup,
        deliverable,
        true,
        REQUEST_LOG);
  }

  private void needs(int amount) {
    when(outstanding.compute(any(), any(), anyInt())).thenReturn(amount);
  }

  private void racksHold(int amount) {
    when(planning.getAvailableFromRacks(tile, deliverable)).thenReturn(amount);
  }

  private void reserved(int amount) {
    when(pickup.getReservedForRequest(requestId)).thenReturn(amount);
    when(pickup.getReservedForDeliverable(deliverable)).thenReturn(amount);
  }

  private List<Tuple<ItemStack, BlockPos>> rackPlan(int count) {
    List<Tuple<ItemStack, BlockPos>> planned =
        new ArrayList<>(
            ImmutableList.of(new Tuple<>(new ItemStack(Items.OAK_LOG, count), BlockPos.ZERO)));
    when(planning.planFromRacksWithPositions(eq(tile), eq(deliverable), anyInt()))
        .thenReturn(planned);
    return planned;
  }

  private void order(int missing, CreateShopNetworkOrderService.OrderResult result) {
    when(networkOrders.orderMissing(
            eq(tile), eq(pickup), eq(deliverable), eq(requestId), eq(missing), any(), anyString()))
        .thenReturn(result);
  }

  private CreateShopPendingDeliveryCreationService.DeliveryCreationResult creationResult() {
    ArgumentCaptor<CreateShopPendingDeliveryCreationService.DeliveryCreationResult> captor =
        ArgumentCaptor.forClass(
            CreateShopPendingDeliveryCreationService.DeliveryCreationResult.class);
    verify(postCreation)
        .apply(eq(resolver), eq(manager), eq(request), eq(level), captor.capture(), anyString());
    return captor.getValue();
  }

  /** One delivery child of the request, as the resolver sees it while it is still open. */
  private OpenDelivery openDelivery(int count, BlockPos start, boolean pickedUp) {
    IToken<?> childToken = new StandardToken(UUID.randomUUID());
    ILocation location = mock(ILocation.class);
    when(location.getDimension()).thenReturn(Level.OVERWORLD);
    when(location.getInDimensionLocation()).thenReturn(start);
    Delivery delivery = mock(Delivery.class);
    when(delivery.getStack()).thenReturn(new ItemStack(Items.OAK_LOG, count));
    when(delivery.getStart()).thenReturn(location);
    IRequest<?> child = mock(IRequest.class);
    when(child.getState()).thenReturn(RequestState.IN_PROGRESS);
    when(child.getRequest()).thenAnswer(invocation -> delivery);
    when(requestHandler.getRequest(childToken)).thenAnswer(invocation -> child);
    if (pickedUp) {
      CreateShopDeliveryChildLedgerEntry entry = new CreateShopDeliveryChildLedgerEntry(childToken);
      entry.pickupConfirmedAtTick = 500L;
      when(resolver.getDeliveryChildLedgerEntry(childToken)).thenReturn(entry);
    }
    children.add(childToken);
    return new OpenDelivery(child);
  }

  /** Lets a test change a delivery child after it was added to the request. */
  private record OpenDelivery(IRequest<?> child) {
    private void completed() {
      when(child.getState()).thenReturn(RequestState.COMPLETED);
    }
  }
}
