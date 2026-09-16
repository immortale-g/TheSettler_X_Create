package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.minecolonies.api.colony.requestsystem.location.ILocation;
import com.minecolonies.api.colony.requestsystem.manager.IRequestManager;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.request.RequestState;
import com.minecolonies.api.colony.requestsystem.requestable.IDeliverable;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.core.colony.requestsystem.management.IStandardRequestManager;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * A request that got at least its minimum count closes once the shop has nothing more for it,
 * instead of blocking every other resolver. Needs real ItemStacks, so it runs with a loaded FML.
 */
@Tag("fml")
class CreateShopFinishShortOfCountFmlTest {
  private TestResolver resolver;
  private IStandardRequestManager manager;

  @BeforeEach
  void setUp() {
    ILocation location = mock(ILocation.class);
    when(location.getDimension()).thenReturn(Level.OVERWORLD);
    when(location.getInDimensionLocation()).thenReturn(BlockPos.ZERO);
    resolver = new TestResolver(location, mock(IToken.class));
    Level level = mock(Level.class);
    when(level.getGameTime()).thenReturn(10_000L);
    manager = mock(IStandardRequestManager.class, RETURNS_DEEP_STUBS);
    when(manager.getColony().getWorld()).thenReturn(level);
  }

  @Test
  void closesOnceTheMinimumArrivedAndNothingIsLeft() {
    IRequest<IDeliverable> request = request(64, 16, 20, false);

    assertTrue(finishShort(request, 0, 0));
    verify(manager).updateRequestState(request.getId(), RequestState.RESOLVED);
    assertTrue(resolver.reservationReleased);
  }

  @Test
  void staysOpenBelowTheMinimum() {
    IRequest<IDeliverable> request = request(64, 32, 20, false);

    assertFalse(finishShort(request, 0, 0));
    verify(manager, never()).updateRequestState(any(), any());
  }

  @Test
  void staysOpenWhileTheShopStillHoldsSomethingForTheRequest() {
    assertFalse(finishShort(request(64, 16, 20, false), 8, 0));
    assertFalse(finishShort(request(64, 16, 20, false), 0, 8));
    assertFalse(finishShort(request(64, 16, 20, true), 0, 0));
    verify(manager, never()).updateRequestState(any(), any());
  }

  @Test
  void deliveriesOfOtherItemsDoNotCount() {
    IRequest<IDeliverable> request = request(64, 16, 0, false);
    when(request.getDeliveries()).thenReturn(ImmutableList.of(new ItemStack(Items.STONE, 32)));

    assertFalse(finishShort(request, 0, 0));
  }

  private boolean finishShort(IRequest<?> request, int reserved, int rack) {
    return resolver
        .getResolverCallbackService()
        .finishShortOfCount(resolver, manager, request, reserved, rack, "test");
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private IRequest<IDeliverable> request(int count, int minimum, int delivered, boolean children) {
    IToken<UUID> token = (IToken<UUID>) mock(IToken.class);
    when(token.getIdentifier()).thenReturn(UUID.randomUUID());
    IDeliverable deliverable = mock(IDeliverable.class);
    when(deliverable.getCount()).thenReturn(count);
    when(deliverable.getMinimumCount()).thenReturn(minimum);
    when(deliverable.matches(any()))
        .thenAnswer(inv -> ((ItemStack) inv.getArgument(0)).is(Items.OAK_LOG));
    IRequest<IDeliverable> request = (IRequest<IDeliverable>) mock(IRequest.class);
    when(request.getId()).thenReturn((IToken) token);
    when(request.getRequest()).thenReturn(deliverable);
    when(request.getDeliveries())
        .thenReturn(
            delivered > 0
                ? ImmutableList.of(new ItemStack(Items.OAK_LOG, delivered))
                : ImmutableList.of());
    when(request.getState()).thenReturn(RequestState.IN_PROGRESS);
    when(request.hasChildren()).thenReturn(children);
    return request;
  }

  private static final class TestResolver extends CreateShopRequestResolver {
    private boolean reservationReleased;

    private TestResolver(ILocation location, IToken<?> token) {
      super(location, token);
    }

    @Override
    void releaseReservation(IRequestManager manager, IRequest<?> request) {
      reservationReleased = true;
    }
  }
}
