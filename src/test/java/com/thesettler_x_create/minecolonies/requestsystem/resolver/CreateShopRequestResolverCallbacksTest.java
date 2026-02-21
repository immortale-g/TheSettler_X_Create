package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.minecolonies.api.colony.requestsystem.location.ILocation;
import com.minecolonies.api.colony.requestsystem.manager.IRequestManager;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.requestable.IDeliverable;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import java.lang.reflect.Method;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CreateShopRequestResolverCallbacksTest {
  private CreateShopRequestResolver resolver;

  @BeforeEach
  void setUp() {
    ILocation resolverLocation = mock(ILocation.class);
    when(resolverLocation.getInDimensionLocation()).thenReturn(BlockPos.ZERO);
    IToken<?> resolverToken = mock(IToken.class);
    resolver = new CreateShopRequestResolver(resolverLocation, resolverToken);
    resolver.getDeliveryParents().clear();
  }

  @Test
  void deliveryCompleteRemovesLinkMapsAndClearsParentDeliveryFlag() throws Exception {
    IToken<?> deliveryToken = mock(IToken.class);
    IToken<?> parentToken = mock(IToken.class);

    resolver.getDeliveryParents().put(deliveryToken, parentToken);
    resolver.markDeliveriesCreated(parentToken);

    IRequest<?> deliveryRequest = mock(IRequest.class);
    when(deliveryRequest.getId()).thenReturn(deliveryToken);
    when(deliveryRequest.getParent()).thenReturn(null);
    when(deliveryRequest.getRequest()).thenReturn(mock(IDeliverable.class));

    invokePrivate(
        "handleDeliveryComplete",
        new Class<?>[] {IRequestManager.class, IRequest.class},
        new Object[] {null, deliveryRequest});

    assertNull(resolver.getDeliveryParents().get(deliveryToken));
    assertFalse(resolver.hasDeliveriesCreated(parentToken));
  }

  @Test
  void assignedRequestCancelClearsPendingAndDeliveryFlags() {
    IToken<?> parentToken = mock(IToken.class);

    resolver.markDeliveriesCreated(parentToken);
    resolver.getPendingTracker().setPendingCount(parentToken, 5);

    @SuppressWarnings("unchecked")
    IRequest<IDeliverable> request = (IRequest<IDeliverable>) mock(IRequest.class);
    when(request.getId()).thenReturn(parentToken);
    when(request.getRequest()).thenReturn(mock(IDeliverable.class));

    IRequestManager manager = null;

    resolver.onAssignedRequestCancelled(manager, request);

    assertEquals(0, resolver.getPendingTracker().getPendingCount(parentToken));
    assertFalse(resolver.hasDeliveriesCreated(parentToken));
  }

  private void invokePrivate(String name, Class<?>[] signature, Object[] args) throws Exception {
    Method method = CreateShopRequestResolver.class.getDeclaredMethod(name, signature);
    method.setAccessible(true);
    method.invoke(resolver, args);
  }
}
