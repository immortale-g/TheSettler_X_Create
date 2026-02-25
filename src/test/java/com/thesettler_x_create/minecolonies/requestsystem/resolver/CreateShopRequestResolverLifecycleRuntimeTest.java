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

import com.minecolonies.api.colony.requestsystem.location.ILocation;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.requestable.IDeliverable;
import com.minecolonies.api.colony.requestsystem.resolver.IRequestResolver;
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

class CreateShopRequestResolverLifecycleRuntimeTest {
  private CreateShopRequestResolver resolver;
  private Level level;
  private IStandardRequestManager manager;

  @BeforeEach
  void setUp() {
    ILocation resolverLocation = mock(ILocation.class);
    when(resolverLocation.getDimension()).thenReturn(Level.OVERWORLD);
    when(resolverLocation.getInDimensionLocation()).thenReturn(BlockPos.ZERO);
    IToken<?> resolverToken = mock(IToken.class);
    resolver = new CreateShopRequestResolver(resolverLocation, resolverToken);

    level = mock(Level.class);
    when(level.getGameTime()).thenReturn(10_000L);
    when(level.dimension()).thenReturn(Level.OVERWORLD);

    manager = mock(IStandardRequestManager.class, RETURNS_DEEP_STUBS);
    when(manager.getColony().getWorld()).thenReturn(level);
  }

  @Test
  void recoverySkipsMutationWhenParentOwnershipDriftsAway() throws Exception {
    IToken<?> parentToken = token(UUID.randomUUID());
    IToken<?> childToken = token(UUID.randomUUID());
    IRequest<?> parentRequest = mock(IRequest.class);
    when(parentRequest.getId()).thenReturn(parentToken);

    ILocation foreignLocation = mock(ILocation.class);
    when(foreignLocation.getDimension()).thenReturn(Level.OVERWORLD);
    when(foreignLocation.getInDimensionLocation()).thenReturn(new BlockPos(99, 70, 99));
    CreateShopRequestResolver foreignResolver =
        new CreateShopRequestResolver(foreignLocation, token(UUID.randomUUID()));

    when(manager.getResolverHandler().getResolverForRequest(parentRequest))
        .thenReturn((IRequestResolver) foreignResolver);

    boolean recovered =
        invokeRecoverDeliveryChild(
            manager, level, parentRequest, childToken, null, null, null, "owner-drift", "test");

    assertFalse(recovered);
    verify(manager, never()).updateRequestState(any(), any());
    verify(parentRequest, never()).removeChild(any());
  }

  @Test
  void requestedCompleteCleansParentTrackingAndPendingState() throws Exception {
    IToken<?> parentToken = token(UUID.randomUUID());
    IToken<?> childToken = token(UUID.randomUUID());

    resolver.getPendingTracker().setPendingCount(parentToken, 3);
    resolver.getPendingTracker().setCooldown(level, parentToken, 200L);
    resolver.markDeliveriesCreated(parentToken);
    resolver.getParentChildrenRecheck().put(parentToken, 10_100L);

    parentMap("parentDeliveryActiveSince").put(parentToken, 9_000L);
    parentMap("parentStaleRecoveryArmedAt").put(parentToken, 9_500L);
    parentMap("deliveryChildActiveSince").put(childToken, 9_200L);

    IRequest<?> childRequest = mock(IRequest.class);
    when(childRequest.getParent()).thenReturn(parentToken);
    when(manager.getRequestHandler().getRequest(childToken)).thenReturn((IRequest) childRequest);

    @SuppressWarnings("unchecked")
    IRequest<IDeliverable> parentRequest = (IRequest<IDeliverable>) mock(IRequest.class);
    when(parentRequest.getId()).thenReturn(parentToken);
    when(parentRequest.getRequest()).thenReturn(mock(IDeliverable.class));

    resolver.onRequestedRequestComplete(manager, parentRequest);

    assertEquals(0, resolver.getPendingTracker().getPendingCount(parentToken));
    assertFalse(resolver.hasDeliveriesCreated(parentToken));
    assertFalse(resolver.getCooldown().isOrdered(parentToken));
    assertFalse(parentMap("parentDeliveryActiveSince").containsKey(parentToken));
    assertFalse(parentMap("parentStaleRecoveryArmedAt").containsKey(parentToken));
    assertFalse(parentMap("deliveryChildActiveSince").containsKey(childToken));
    assertTrue(parentMap("deliveryChildActiveSince").isEmpty());
  }

  private boolean invokeRecoverDeliveryChild(
      IStandardRequestManager manager,
      Level level,
      IRequest<?> parentRequest,
      IToken<?> childToken,
      IRequest<?> childRequest,
      com.thesettler_x_create.minecolonies.building.BuildingCreateShop shop,
      com.thesettler_x_create.blockentity.CreateShopBlockEntity pickup,
      String pendingSource,
      String logTemplate)
      throws Exception {
    Method method =
        CreateShopRequestResolver.class.getDeclaredMethod(
            "recoverDeliveryChild",
            IStandardRequestManager.class,
            Level.class,
            IRequest.class,
            IToken.class,
            IRequest.class,
            com.thesettler_x_create.minecolonies.building.BuildingCreateShop.class,
            com.thesettler_x_create.blockentity.CreateShopBlockEntity.class,
            String.class,
            String.class);
    method.setAccessible(true);
    return (boolean)
        method.invoke(
            resolver,
            manager,
            level,
            parentRequest,
            childToken,
            childRequest,
            shop,
            pickup,
            pendingSource,
            logTemplate);
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
}
