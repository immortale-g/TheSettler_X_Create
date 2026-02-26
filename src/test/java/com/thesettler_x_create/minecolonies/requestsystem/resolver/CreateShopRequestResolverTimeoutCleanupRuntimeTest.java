package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.minecolonies.api.colony.requestsystem.location.ILocation;
import com.minecolonies.api.colony.requestsystem.manager.IRequestManager;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.requestable.IDeliverable;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.core.colony.requestsystem.management.IStandardRequestManager;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CreateShopRequestResolverTimeoutCleanupRuntimeTest {
  private TestResolver resolver;
  private Level level;
  private IStandardRequestManager manager;

  @BeforeEach
  void setUp() {
    ILocation resolverLocation = mock(ILocation.class);
    when(resolverLocation.getDimension()).thenReturn(Level.OVERWORLD);
    when(resolverLocation.getInDimensionLocation()).thenReturn(BlockPos.ZERO);
    IToken<?> resolverToken = mock(IToken.class);
    resolver = new TestResolver(resolverLocation, resolverToken);

    level = mock(Level.class);
    when(level.getGameTime()).thenReturn(10_000L);
    when(level.dimension()).thenReturn(Level.OVERWORLD);

    manager = mock(IStandardRequestManager.class, RETURNS_DEEP_STUBS);
    when(manager.getColony().getWorld()).thenReturn(level);
  }

  @Test
  void timedOutFlowReleasesReservationAndCleansTrackedState() throws Exception {
    UUID parentId = UUID.randomUUID();
    IToken<?> parentToken = token(parentId);
    IToken<?> childToken = token(UUID.randomUUID());

    IRequest<?> parentRequest = mock(IRequest.class);
    when(parentRequest.getId()).thenReturn(parentToken);
    when(parentRequest.getRequest()).thenReturn(mock(IDeliverable.class));
    when(manager.getRequestHandler().getRequest(parentToken)).thenReturn((IRequest) parentRequest);

    IRequest<?> childRequest = mock(IRequest.class);
    when(childRequest.getParent()).thenReturn(parentToken);
    when(manager.getRequestHandler().getRequest(childToken)).thenReturn((IRequest) childRequest);

    CreateShopRequestStateMachine flowStateMachine =
        (CreateShopRequestStateMachine) getField("flowStateMachine");
    flowStateMachine.transition(
        parentToken, CreateShopFlowState.ORDERED_FROM_NETWORK, 0L, "ordered", "", 0);

    resolver.getPendingTracker().setPendingCount(parentToken, 3);
    resolver.getPendingTracker().setCooldown(level, parentToken, 200L);
    resolver.markDeliveriesCreated(parentToken);
    parentMap("parentDeliveryActiveSince").put(parentToken, 8_000L);
    parentMap("deliveryChildActiveSince").put(childToken, 8_500L);

    invokeProcessTimedOutFlows(manager, level);

    assertTrue(resolver.reservationReleased);
    assertEquals(parentToken, resolver.releasedToken);
    assertEquals(0, resolver.getPendingTracker().getPendingCount(parentToken));
    assertFalse(resolver.getCooldown().isOrdered(parentToken));
    assertFalse(resolver.hasDeliveriesCreated(parentToken));
    assertFalse(parentMap("parentDeliveryActiveSince").containsKey(parentToken));
    assertFalse(parentMap("deliveryChildActiveSince").containsKey(childToken));
  }

  private void invokeProcessTimedOutFlows(IStandardRequestManager manager, Level level)
      throws Exception {
    Method method =
        CreateShopRequestResolver.class.getDeclaredMethod(
            "processTimedOutFlows", IStandardRequestManager.class, Level.class);
    method.setAccessible(true);
    method.invoke(resolver, manager, level);
  }

  private Object getField(String fieldName) throws Exception {
    Field field = CreateShopRequestResolver.class.getDeclaredField(fieldName);
    field.setAccessible(true);
    return field.get(resolver);
  }

  @SuppressWarnings("unchecked")
  private Map<IToken<?>, Long> parentMap(String fieldName) throws Exception {
    Field field = CreateShopRequestResolver.class.getDeclaredField(fieldName);
    field.setAccessible(true);
    return (Map<IToken<?>, Long>) field.get(resolver);
  }

  private IToken<?> token(UUID id) {
    IToken<?> token = mock(IToken.class);
    when(token.getIdentifier()).thenReturn(id);
    when(token.toString()).thenReturn("token:" + id);
    return token;
  }

  private static final class TestResolver extends CreateShopRequestResolver {
    private boolean reservationReleased;
    private IToken<?> releasedToken;

    private TestResolver(ILocation location, IToken<?> token) {
      super(location, token);
    }

    @Override
    void releaseReservation(IRequestManager manager, IRequest<?> request) {
      reservationReleased = true;
      releasedToken = request == null ? null : request.getId();
    }
  }
}
