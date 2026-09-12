package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Behaviour of the single place that closes a Create Shop parent request. */
class CreateShopFinishIfDeliveredTest {
  private TestResolver resolver;
  private IStandardRequestManager manager;

  @BeforeEach
  void setUp() {
    ILocation resolverLocation = mock(ILocation.class);
    when(resolverLocation.getDimension()).thenReturn(Level.OVERWORLD);
    when(resolverLocation.getInDimensionLocation()).thenReturn(BlockPos.ZERO);
    resolver = new TestResolver(resolverLocation, mock(IToken.class));

    Level level = mock(Level.class);
    when(level.getGameTime()).thenReturn(10_000L);
    manager = mock(IStandardRequestManager.class, RETURNS_DEEP_STUBS);
    when(manager.getColony().getWorld()).thenReturn(level);
  }

  @Test
  void resolvesAndCleansUpOnceNothingIsOutstanding() {
    IRequest<IDeliverable> request = request(0, RequestState.IN_PROGRESS, false);
    resolver.getPendingTracker().setPendingCount(request.getId(), 3);
    resolver.getPendingTracker().markDeliveryStarted(request.getId());

    boolean finished = finish(request);

    assertTrue(finished);
    verify(manager).updateRequestState(request.getId(), RequestState.RESOLVED);
    assertTrue(resolver.reservationReleased);
    assertEquals(0, resolver.getPendingTracker().getPendingCount(request.getId()));
    assertFalse(resolver.getPendingTracker().hasDeliveryStarted(request.getId()));
  }

  @Test
  void keepsTheRequestOpenWhileSomethingIsStillOutstanding() {
    // A partial delivery or a Create order still on its way: the tick orders and delivers the
    // rest, so the request must stay IN_PROGRESS.
    IRequest<IDeliverable> request = request(12, RequestState.IN_PROGRESS, false);

    assertFalse(finish(request));
    verify(manager, never()).updateRequestState(any(), any());
    assertFalse(resolver.reservationReleased);
  }

  @Test
  void neverResolvesWhileADeliveryChildIsOpen() {
    IRequest<IDeliverable> request = request(0, RequestState.IN_PROGRESS, true);

    assertFalse(finish(request));
    verify(manager, never()).updateRequestState(any(), any());
  }

  @Test
  void ignoresRequestsThatAreAlreadyTerminal() {
    IRequest<IDeliverable> request = request(0, RequestState.COMPLETED, false);

    assertFalse(finish(request));
    verify(manager, never()).updateRequestState(any(), any());
  }

  private boolean finish(IRequest<?> request) {
    return resolver
        .getTerminalRequestLifecycleService()
        .finishIfDelivered(resolver, manager, request, "test");
  }

  @SuppressWarnings("unchecked")
  private IRequest<IDeliverable> request(int outstanding, RequestState state, boolean children) {
    IToken<UUID> token = (IToken<UUID>) mock(IToken.class);
    when(token.getIdentifier()).thenReturn(UUID.randomUUID());
    IDeliverable deliverable = mock(IDeliverable.class);
    when(deliverable.getCount()).thenReturn(outstanding);
    IRequest<IDeliverable> request = (IRequest<IDeliverable>) mock(IRequest.class);
    when(request.getId()).thenReturn((IToken) token);
    when(request.getRequest()).thenReturn(deliverable);
    when(request.getDeliveries()).thenReturn(ImmutableList.of());
    when(request.getState()).thenReturn(state);
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
