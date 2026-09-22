package com.thesettler_x_create.minecolonies.building;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.modules.ICraftingBuildingModule;
import com.minecolonies.api.colony.buildings.workerbuildings.IWareHouse;
import com.minecolonies.api.colony.managers.interfaces.IRegisteredStructureManager;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.requestable.Stack;
import com.minecolonies.api.colony.requestsystem.requester.IRequester;
import com.minecolonies.api.colony.requestsystem.token.StandardToken;
import com.minecolonies.api.crafting.IRecipeStorage;
import com.minecolonies.api.tileentities.AbstractTileEntityWareHouse;
import com.minecolonies.api.util.Tuple;
import com.minecolonies.core.colony.requestsystem.management.IStandardRequestManager;
import com.thesettler_x_create.blockentity.CreateShopBlockEntity;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * What a Colony Factory Gauge actually orders from the colony. Two things decide it: how much the
 * warehouses hold, and whether anybody could craft the rest. The minimum count on the order is the
 * part that matters most, since it is what makes MineColonies hand a shortfall to the crafters,
 * while a minimum the colony cannot cover strands the order instead. Needs real ItemStacks, so it
 * runs with a loaded FML.
 */
@Tag("fml")
class ShopGaugeQueueFmlTest {
  private static final ItemStack TORCH = new ItemStack(Items.TORCH);
  private static final String ADDRESS = "gauge-one";

  private BuildingCreateShop shop;
  private IStandardRequestManager manager;
  private IRequester requester;
  private CreateShopBlockEntity pickup;
  private List<IWareHouse> warehouses;
  private Map<BlockPos, IBuilding> buildings;
  private ShopGaugeQueue queue;
  private List<StandardToken> issuedTokens;

  @BeforeEach
  void setUp() {
    shop = mock(BuildingCreateShop.class);
    IColony colony = mock(IColony.class);
    manager = mock(IStandardRequestManager.class);
    requester = mock(IRequester.class);
    pickup = mock(CreateShopBlockEntity.class);
    IRegisteredStructureManager structures = mock(IRegisteredStructureManager.class);
    warehouses = new ArrayList<>();
    buildings = new HashMap<>();

    when(shop.getBuildingLevel()).thenReturn(5);
    when(shop.getColony()).thenReturn(colony);
    when(shop.getRequester()).thenReturn(requester);
    when(shop.isWorkerWorking()).thenReturn(true);
    when(shop.getPickupBlockEntity()).thenReturn(pickup);
    when(colony.getRequestManager()).thenReturn(manager);
    when(colony.getServerBuildingManager()).thenReturn(structures);
    when(structures.getWareHouses()).thenReturn(warehouses);
    when(structures.getBuildings()).thenReturn(buildings);
    // A token of its own per order: two gauges asking for the same item are tracked apart by
    // exactly that.
    issuedTokens = new ArrayList<>();
    doAnswer(
            invocation -> {
              StandardToken token = new StandardToken(UUID.randomUUID());
              issuedTokens.add(token);
              return token;
            })
        .when(manager)
        .createAndAssignRequest(any(), any());

    queue = new ShopGaugeQueue(shop);
  }

  @Test
  void stockOnTheShelfIsOrderedWithoutAMinimum() {
    warehouseHolds(TORCH, 64);

    assertEquals(64, queue.requestForGauge(TORCH, 64, ADDRESS));

    Stack ordered = orderPlaced();
    assertEquals(64, ordered.getCount());
    assertEquals(1, ordered.getMinimumCount());
  }

  @Test
  void whatNobodyCanCraftIsCutDownToWhatTheWarehouseHolds() {
    warehouseHolds(TORCH, 20);

    assertEquals(20, queue.requestForGauge(TORCH, 64, ADDRESS));

    Stack ordered = orderPlaced();
    assertEquals(20, ordered.getCount());
    // Asking for that much at minimum would turn a handful of units drawn by someone else in
    // between into a child request nobody can craft.
    assertEquals(1, ordered.getMinimumCount());
  }

  @Test
  void whatSomebodyCanCraftIsOrderedInFullSoTheRestReachesTheCrafters() {
    warehouseHolds(TORCH, 20);
    someoneCanCraft(TORCH);

    assertEquals(64, queue.requestForGauge(TORCH, 64, ADDRESS));

    Stack ordered = orderPlaced();
    assertEquals(64, ordered.getCount());
    assertEquals(64, ordered.getMinimumCount());
  }

  @Test
  void anEmptyWarehouseStillOrdersWhatACrafterCouldMake() {
    someoneCanCraft(TORCH);

    assertEquals(64, queue.requestForGauge(TORCH, 64, ADDRESS));

    Stack ordered = orderPlaced();
    assertEquals(64, ordered.getCount());
    assertEquals(64, ordered.getMinimumCount());
  }

  @Test
  void withNeitherStockNorACrafterNothingIsOrdered() {
    assertEquals(0, queue.requestForGauge(TORCH, 64, ADDRESS));

    verify(manager, never()).createAndAssignRequest(any(), any());
  }

  @Test
  void aShopkeeperWhoIsNotWorkingOrdersNothing() {
    warehouseHolds(TORCH, 64);
    when(shop.isWorkerWorking()).thenReturn(false);

    assertEquals(0, queue.requestForGauge(TORCH, 64, ADDRESS));

    verify(manager, never()).createAndAssignRequest(any(), any());
  }

  @Test
  void aShopTooSmallForGaugeRequestsOrdersNothing() {
    warehouseHolds(TORCH, 64);
    when(shop.getBuildingLevel()).thenReturn(1);

    assertEquals(0, queue.requestForGauge(TORCH, 64, ADDRESS));

    verify(manager, never()).createAndAssignRequest(any(), any());
  }

  @Test
  void theSameGaugeAskingAgainReportsTheOpenOrderInsteadOfPlacingASecond() {
    warehouseHolds(TORCH, 20);
    assertEquals(20, queue.requestForGauge(TORCH, 64, ADDRESS));

    assertEquals(20, queue.requestForGauge(TORCH, 64, ADDRESS));

    verify(manager, times(1)).createAndAssignRequest(any(), any());
  }

  @Test
  void anotherGaugeAskingForTheSameItemGetsItsOwnOrder() {
    warehouseHolds(TORCH, 20);
    queue.requestForGauge(TORCH, 64, ADDRESS);

    assertEquals(20, queue.requestForGauge(TORCH, 64, "gauge-two"));

    verify(manager, times(2)).createAndAssignRequest(any(), any());
  }

  @Test
  void whatIsOwedCoversWhatWasOrderedNotWhatWasAskedFor() {
    warehouseHolds(TORCH, 20);

    queue.requestForGauge(TORCH, 64, ADDRESS);

    // What the shop owes its gauges is what a pickup leaves standing and what the resolver does
    // not hand out, so it has to match the order rather than what the gauge asked for.
    assertEquals(20, owedForTorches());
  }

  @Test
  void aGaugeOrderIsNotAPickupReservation() {
    warehouseHolds(TORCH, 20);
    queue.requestForGauge(TORCH, 64, ADDRESS);

    queue.deliverPartOfGaugeTask(taskId(), 12);
    queue.deliverPartOfGaugeTask(taskId(), 8);

    // The ledger next to the pickup block is for the Create requests the shop resolves. A gauge
    // order is colony goods waiting in the hut buffer, and used to sit in that same ledger, where
    // every reader had to know which reservations were not rack stock.
    verifyNoInteractions(pickup);
  }

  @Test
  void anItemNobodyAskedForIsNotOwed() {
    warehouseHolds(TORCH, 20);
    queue.requestForGauge(TORCH, 20, ADDRESS);

    assertEquals(0, queue.owedToGaugeTasks(stack -> stack.is(Items.STONE)));
    assertEquals(0, queue.owedToGaugeTasks(null));
  }

  @Test
  void whatTheRacksHoldShipsNowAndTheRestStaysOwed() {
    warehouseHolds(TORCH, 20);
    queue.requestForGauge(TORCH, 64, ADDRESS);
    // The colony delivers in batches, so only part of the order is in the racks when the packager
    // looks. Waiting for the last item would hold back goods that are already there.
    assertEquals(20 - 12, queue.deliverPartOfGaugeTask(taskId(), 12));

    BuildingCreateShop.GaugePackagingTask open = queue.peekNextGaugeTask();
    assertEquals(20 - 12, open.amount());
    assertEquals(ADDRESS, open.gaugeAddress());
    // Only the rest is still owed; what left the shop is nobody's to keep anymore.
    assertEquals(20 - 12, owedForTorches());
  }

  @Test
  void aTaskThatIsFullyPackagedLeavesTheQueue() {
    warehouseHolds(TORCH, 20);
    queue.requestForGauge(TORCH, 64, ADDRESS);

    assertEquals(0, queue.deliverPartOfGaugeTask(taskId(), 20));

    assertNull(queue.peekNextGaugeTask());
    assertFalse(queue.hasGaugeTask());
    assertEquals(0, owedForTorches());
  }

  @Test
  void packagingMoreThanIsOwedClosesTheTaskWithoutGoingNegative() {
    warehouseHolds(TORCH, 20);
    queue.requestForGauge(TORCH, 64, ADDRESS);

    assertEquals(0, queue.deliverPartOfGaugeTask(taskId(), 999));

    assertNull(queue.peekNextGaugeTask());
  }

  @Test
  void anOrderTheColonyClosesShortIsCutDownToWhatArrived() {
    warehouseHolds(TORCH, 10);
    queue.requestForGauge(TORCH, 10, ADDRESS);

    // Somebody else drew from the warehouse in between. MineColonies hands over what is left and
    // closes the request, because the minimum of 1 is covered: nothing more will come for it.
    queue.onRequestComplete(requestFor(issuedTokens.get(0), TORCH, 6));

    assertEquals(6, queue.peekNextGaugeTask().amount());
  }

  @Test
  void anOrderTheColonyFillsInFullKeepsItsTask() {
    warehouseHolds(TORCH, 10);
    queue.requestForGauge(TORCH, 10, ADDRESS);

    queue.onRequestComplete(requestFor(issuedTokens.get(0), TORCH, 10));

    assertEquals(10, queue.peekNextGaugeTask().amount());
  }

  @Test
  void anOrderThatSaysNothingAboutItsDeliveriesLeavesTheTaskAlone() {
    warehouseHolds(TORCH, 10);
    queue.requestForGauge(TORCH, 10, ADDRESS);

    // A resolver that records nothing tells us nothing, and a task is only cut down on a number
    // that was really read.
    queue.onRequestComplete(requestFor(issuedTokens.get(0), TORCH, -1));

    assertEquals(10, queue.peekNextGaugeTask().amount());
  }

  @Test
  void anOrderThatBroughtNothingLeavesTheQueue() {
    warehouseHolds(TORCH, 10);
    queue.requestForGauge(TORCH, 10, ADDRESS);

    queue.onRequestComplete(requestFor(issuedTokens.get(0), TORCH, 0));

    assertFalse(queue.hasGaugeTask());
    assertEquals(0, owedForTorches());
  }

  @Test
  void aTaskIsServedByItsOwnOrderRatherThanByItsPlaceInTheQueue() {
    warehouseHolds(TORCH, 20);
    queue.requestForGauge(TORCH, 20, ADDRESS);
    queue.requestForGauge(TORCH, 20, "gauge-two");

    // The shop ships whichever task the racks can cover, so the second one can be served while the
    // first still waits for its goods.
    List<BuildingCreateShop.GaugePackagingTask> tasks = queue.getGaugeTasks();
    assertEquals(5, queue.deliverPartOfGaugeTask(tasks.get(1).requestId(), 15));

    assertEquals(20, queue.getGaugeTasks().get(0).amount());
    assertEquals(5, queue.getGaugeTasks().get(1).amount());
    assertEquals(ADDRESS, queue.getGaugeTasks().get(0).gaugeAddress());
  }

  @Test
  void bookingAgainstATaskThatIsNoLongerQueuedSaysSo() {
    assertEquals(-1, queue.deliverPartOfGaugeTask(UUID.randomUUID(), 5));
  }

  /** What the shop still owes its gauges in torches. */
  private int owedForTorches() {
    return queue.owedToGaugeTasks(stack -> stack.is(Items.TORCH));
  }

  /** The request id of the only queued task. */
  private UUID taskId() {
    return queue.peekNextGaugeTask().requestId();
  }

  /**
   * A completed colony request that delivered {@code delivered} of {@code item}, or one that
   * records no deliveries at all when {@code delivered} is negative.
   */
  private static IRequest<?> requestFor(StandardToken token, ItemStack item, int delivered) {
    ImmutableList<ItemStack> deliveries;
    if (delivered < 0) {
      deliveries = ImmutableList.of();
    } else if (delivered == 0) {
      // Something arrived, but nothing of what was asked for.
      deliveries = ImmutableList.of(new ItemStack(Items.STONE, 1));
    } else {
      deliveries = ImmutableList.of(item.copyWithCount(delivered));
    }
    IRequest<?> request = mock(IRequest.class);
    doReturn(token).when(request).getId();
    doReturn(deliveries).when(request).getDeliveries();
    return request;
  }

  /** The stack the shop handed to MineColonies. */
  private Stack orderPlaced() {
    ArgumentCaptor<Stack> captor = ArgumentCaptor.forClass(Stack.class);
    verify(manager).createAndAssignRequest(eq(requester), captor.capture());
    return captor.getValue();
  }

  private void warehouseHolds(ItemStack stack, int count) {
    AbstractTileEntityWareHouse tile = mock(AbstractTileEntityWareHouse.class);
    when(tile.getMatchingItemStacksInWarehouse(any()))
        .thenReturn(List.of(new Tuple<>(stack.copyWithCount(count), BlockPos.ZERO)));
    IWareHouse warehouse = mock(IWareHouse.class);
    when(warehouse.getTileEntity()).thenReturn(tile);
    warehouses.add(warehouse);
  }

  @SuppressWarnings("unchecked")
  private void someoneCanCraft(ItemStack product) {
    ICraftingBuildingModule module = mock(ICraftingBuildingModule.class);
    when(module.getFirstRecipe(any(Predicate.class)))
        .thenAnswer(
            invocation -> {
              Predicate<ItemStack> wanted = invocation.getArgument(0);
              return wanted.test(product) ? mock(IRecipeStorage.class) : null;
            });
    IBuilding building = mock(IBuilding.class);
    when(building.getBuildingLevel()).thenReturn(3);
    when(building.getAllAssignedCitizen()).thenReturn(Set.of(mock(ICitizenData.class)));
    when(building.getModulesByType(ICraftingBuildingModule.class)).thenReturn(List.of(module));
    buildings.put(new BlockPos(buildings.size(), 64, 0), building);
  }
}
