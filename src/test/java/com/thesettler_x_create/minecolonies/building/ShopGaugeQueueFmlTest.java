package com.thesettler_x_create.minecolonies.building;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.modules.ICraftingBuildingModule;
import com.minecolonies.api.colony.buildings.workerbuildings.IWareHouse;
import com.minecolonies.api.colony.managers.interfaces.IRegisteredStructureManager;
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
    doAnswer(invocation -> new StandardToken(UUID.randomUUID()))
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
  void theReservationCoversWhatWasOrderedNotWhatWasAskedFor() {
    warehouseHolds(TORCH, 20);

    queue.requestForGauge(TORCH, 64, ADDRESS);

    // The reservation keeps rack housekeeping off the delivered goods until they are packaged, so
    // it has to match the order rather than what the gauge asked for.
    verify(pickup).reserve(any(UUID.class), any(ItemStack.class), eq(20));
  }

  @Test
  void whatTheRacksHoldShipsNowAndTheRestStaysOwed() {
    warehouseHolds(TORCH, 20);
    queue.requestForGauge(TORCH, 64, ADDRESS);
    // The colony delivers in batches, so only part of the order is in the racks when the packager
    // looks. Waiting for the last item would hold back goods that are already there.
    queue.deliverPartOfNextGaugeTask(12);

    BuildingCreateShop.GaugePackagingTask open = queue.peekNextGaugeTask();
    assertEquals(20 - 12, open.amount());
    assertEquals(ADDRESS, open.gaugeAddress());
    verify(pickup).consumeReservedForRequest(any(UUID.class), any(ItemStack.class), eq(12));
  }

  @Test
  void aTaskThatIsFullyPackagedLeavesTheQueue() {
    warehouseHolds(TORCH, 20);
    queue.requestForGauge(TORCH, 64, ADDRESS);

    queue.deliverPartOfNextGaugeTask(20);

    assertNull(queue.peekNextGaugeTask());
    assertFalse(queue.hasGaugeTask());
    // The whole reservation goes, rather than being consumed piece by piece.
    verify(pickup).release(any(UUID.class));
  }

  @Test
  void packagingMoreThanIsOwedClosesTheTaskWithoutGoingNegative() {
    warehouseHolds(TORCH, 20);
    queue.requestForGauge(TORCH, 64, ADDRESS);

    queue.deliverPartOfNextGaugeTask(999);

    assertNull(queue.peekNextGaugeTask());
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
