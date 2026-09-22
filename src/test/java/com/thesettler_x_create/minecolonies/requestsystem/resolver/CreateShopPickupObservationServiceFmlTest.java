package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.managers.interfaces.ICitizenManager;
import com.minecolonies.api.colony.requestsystem.location.ILocation;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.requestable.deliveryman.Delivery;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.api.colony.requestsystem.token.StandardToken;
import com.minecolonies.core.colony.jobs.JobDeliveryman;
import com.minecolonies.core.colony.requestsystem.management.IStandardRequestManager;
import com.thesettler_x_create.blockentity.CreateShopBlockEntity;
import com.thesettler_x_create.minecolonies.building.BuildingCreateShop;
import com.thesettler_x_create.stock.PickupTracker;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * What happens to a request's reservation when a courier takes its items out of the shop hut. The
 * hut reports an amount and never an actor, so the delivery an extraction belongs to is read from
 * MineColonies' own ongoing-deliveries set; only what that set names is booked, and the reservation
 * of exactly that request shrinks by exactly that much. Needs real ItemStacks, so it runs with a
 * loaded FML.
 */
@Tag("fml")
class CreateShopPickupObservationServiceFmlTest {
  private static final BlockPos HUT = new BlockPos(10, 64, 10);
  private static final BlockPos OTHER_HUT = new BlockPos(90, 64, 90);

  private final CourierOngoingDeliveries ongoing = mock(CourierOngoingDeliveries.class);
  private final CreateShopPickupObservationService service =
      new CreateShopPickupObservationService(ongoing);
  private final PickupTracker<IToken<?>> tracker = new PickupTracker<>();

  private CreateShopRequestResolver resolver;
  private IStandardRequestManager manager;
  private IColony colony;
  private Level level;
  private BuildingCreateShop shop;
  private CreateShopBlockEntity pickup;
  private List<ICitizenData> citizens;

  @BeforeEach
  void setUp() {
    resolver = mock(CreateShopRequestResolver.class);
    manager = mock(IStandardRequestManager.class);
    colony = mock(IColony.class);
    level = mock(Level.class);
    shop = mock(BuildingCreateShop.class);
    pickup = mock(CreateShopBlockEntity.class);
    ICitizenManager citizenManager = mock(ICitizenManager.class);
    citizens = new ArrayList<>();

    when(manager.getColony()).thenReturn(colony);
    when(colony.getWorld()).thenReturn(level);
    when(colony.getCitizenManager()).thenReturn(citizenManager);
    when(citizenManager.getCitizens()).thenReturn(citizens);
    when(resolver.getShop(manager)).thenReturn(shop);
    when(shop.getPickupBlockEntity()).thenReturn(pickup);
    ILocation hutLocation = locationAt(HUT);
    when(shop.getLocation()).thenReturn(hutLocation);
    when(pickup.consumeReservedForRequest(any(), any(), anyInt()))
        .thenAnswer(invocation -> invocation.getArgument(2));
  }

  @Test
  void aCourierTakingItsDeliveryConsumesThatRequestsReservation() {
    Delivered delivery = new Courier().carries(Items.OAK_LOG, 16, HUT);

    takes(Items.OAK_LOG, 16);

    verify(pickup).consumeReservedForRequest(eq(delivery.owner()), any(), eq(16));
    verify(resolver).observeDeliveryChildPickup(level, delivery.parent(), delivery.token());
    assertEquals(16, tracker.takenFor(delivery.token()));
  }

  @Test
  void aDeliveryGatheredInSeveralHandfulsIsNeverBookedBeyondWhatItCarries() {
    Delivered delivery = new Courier().carries(Items.OAK_LOG, 16, HUT);

    takes(Items.OAK_LOG, 8);
    takes(Items.OAK_LOG, 8);
    takes(Items.OAK_LOG, 8);

    assertEquals(16, tracker.takenFor(delivery.token()));
    verify(pickup, times(2)).consumeReservedForRequest(eq(delivery.owner()), any(), eq(8));
    verify(pickup, times(2)).consumeReservedForRequest(any(), any(), anyInt());
  }

  @Test
  void twoOpenDeliveriesForTheSameItemAreFilledInTurn() {
    Delivered first = new Courier().carries(Items.OAK_LOG, 16, HUT);
    Delivered second = new Courier().carries(Items.OAK_LOG, 16, HUT);

    takes(Items.OAK_LOG, 20);

    assertEquals(16, tracker.takenFor(first.token()));
    assertEquals(4, tracker.takenFor(second.token()));
    verify(pickup).consumeReservedForRequest(eq(first.owner()), any(), eq(16));
    verify(pickup).consumeReservedForRequest(eq(second.owner()), any(), eq(4));
  }

  @Test
  void whatMineColoniesDoesNotNameIsNotBooked() {
    // A courier is at the shop, but its job names no ongoing delivery. The goods are then booked
    // on arrival instead; guessing one of its queued deliveries would consume the wrong
    // reservation and leave the right one hanging.
    new Courier();

    takes(Items.OAK_LOG, 16);

    verify(pickup, never()).consumeReservedForRequest(any(), any(), anyInt());
    verify(resolver, never()).observeDeliveryChildPickup(any(), any(), any());
  }

  @Test
  void aDeliveryStartingAtAnotherBuildingIsNotOurs() {
    new Courier().carries(Items.OAK_LOG, 16, OTHER_HUT);

    takes(Items.OAK_LOG, 16);

    verify(pickup, never()).consumeReservedForRequest(any(), any(), anyInt());
    verify(resolver, never()).observeDeliveryChildPickup(any(), any(), any());
  }

  @Test
  void anotherItemLeavingTheHutBooksNothingOnAWaitingDelivery() {
    Delivered delivery = new Courier().carries(Items.OAK_LOG, 16, HUT);

    takes(Items.TORCH, 16);

    assertEquals(0, tracker.takenFor(delivery.token()));
    verify(pickup, never()).consumeReservedForRequest(any(), any(), anyInt());
  }

  @Test
  void progressOfADeliveryNoCourierCarriesAnymoreIsForgotten() {
    Courier courier = new Courier();
    Delivered delivery = courier.carries(Items.OAK_LOG, 16, HUT);
    takes(Items.OAK_LOG, 8);
    assertEquals(8, tracker.takenFor(delivery.token()));

    courier.namesNothing();
    takes(Items.OAK_LOG, 8);

    assertEquals(0, tracker.trackedCount());
  }

  @Test
  void aShopWithoutItsPickupBlockBooksNothing() {
    when(shop.getPickupBlockEntity()).thenReturn(null);
    new Courier().carries(Items.OAK_LOG, 16, HUT);

    takes(Items.OAK_LOG, 16);

    verify(resolver, never()).observeDeliveryChildPickup(any(), any(), any());
  }

  @Test
  void anEmptyExtractionIsIgnored() {
    new Courier().carries(Items.OAK_LOG, 16, HUT);

    service.onHutItemsTaken(resolver, tracker, manager, ItemStack.EMPTY);

    verify(pickup, never()).consumeReservedForRequest(any(), any(), anyInt());
    verify(resolver, never()).observeDeliveryChildPickup(any(), any(), any());
  }

  private void takes(Item item, int count) {
    service.onHutItemsTaken(resolver, tracker, manager, new ItemStack(item, count));
  }

  /** One courier of the colony, and what its job says it is reaching for right now. */
  private final class Courier {
    private final JobDeliveryman job = mock(JobDeliveryman.class);

    private Courier() {
      ICitizenData citizen = mock(ICitizenData.class);
      when(citizen.getJob()).thenAnswer(invocation -> job);
      citizens.add(citizen);
      namesNothing();
    }

    private Delivered carries(Item item, int count, BlockPos from) {
      IToken<?> token = new StandardToken(UUID.randomUUID());
      UUID owner = UUID.randomUUID();
      IToken<?> parent = new StandardToken(owner);
      Delivery delivery = mock(Delivery.class);
      ILocation start = locationAt(from);
      when(delivery.getStack()).thenReturn(new ItemStack(item, count));
      when(delivery.getStart()).thenReturn(start);
      IRequest<?> request = mock(IRequest.class);
      when(request.hasParent()).thenReturn(true);
      when(request.getParent()).thenAnswer(invocation -> parent);
      when(request.getRequest()).thenAnswer(invocation -> delivery);
      when(manager.getRequestForToken(token)).thenAnswer(invocation -> request);
      when(ongoing.of(colony, job)).thenReturn(Set.of(token));
      return new Delivered(token, parent, owner);
    }

    private void namesNothing() {
      when(ongoing.of(colony, job)).thenReturn(Set.of());
    }
  }

  /** A delivery a courier is fetching: its own token, its parent request and that request's id. */
  private record Delivered(IToken<?> token, IToken<?> parent, UUID owner) {}

  private ILocation locationAt(BlockPos pos) {
    ILocation location = mock(ILocation.class);
    when(location.getDimension()).thenReturn(Level.OVERWORLD);
    when(location.getInDimensionLocation()).thenReturn(pos);
    return location;
  }
}
