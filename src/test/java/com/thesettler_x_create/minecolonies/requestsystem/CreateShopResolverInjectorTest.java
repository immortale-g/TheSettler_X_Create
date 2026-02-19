package com.thesettler_x_create.minecolonies.requestsystem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.minecolonies.api.colony.requestsystem.data.IRequestResolverRequestAssignmentDataStore;
import com.minecolonies.api.colony.requestsystem.location.ILocation;
import com.minecolonies.api.colony.requestsystem.management.IRequestHandler;
import com.minecolonies.api.colony.requestsystem.management.IResolverHandler;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.request.RequestState;
import com.minecolonies.api.colony.requestsystem.requestable.IDeliverable;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.thesettler_x_create.minecolonies.requestsystem.resolver.CreateShopRequestResolver;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CreateShopResolverInjectorTest {
  private static final String ACTIVE_SET_FIELD = "ACTIVE_SHOP_RESOLVERS";
  private static final String DISABLED_SET_FIELD = "DISABLED_DELIVERY_RESOLVERS";
  private static final String REASSIGN_ATTEMPTS_FIELD = "REASSIGN_ATTEMPTS";

  @BeforeEach
  void resetStaticState() throws Exception {
    getTokenSet(ACTIVE_SET_FIELD).clear();
    getTokenSet(DISABLED_SET_FIELD).clear();
    getReassignAttempts().clear();
  }

  @Test
  void staleShopResolverAssignmentIsReassigned() throws Exception {
    IToken<Object> requestToken = mock(IToken.class);
    IToken<Object> assignedToken = mock(IToken.class);
    IRequest<?> request = mock(IRequest.class);
    IRequestHandler requestHandler = mock(IRequestHandler.class);
    IResolverHandler resolverHandler = mock(IResolverHandler.class);
    IRequestResolverRequestAssignmentDataStore assignmentStore =
        mock(IRequestResolverRequestAssignmentDataStore.class);
    IDeliverable deliverable = mock(IDeliverable.class);
    CreateShopRequestResolver staleResolver =
        new CreateShopRequestResolver(mock(ILocation.class), assignedToken);

    when(request.hasChildren()).thenReturn(false);
    when(request.getRequest()).thenReturn(deliverable);
    when(request.getState()).thenReturn(RequestState.IN_PROGRESS);
    doReturn(assignedToken).when(assignmentStore).getAssignmentForValue(requestToken);
    doReturn(staleResolver).when(resolverHandler).getResolver(assignedToken);
    doReturn(staleResolver).when(resolverHandler).getResolverForRequest(request);

    int result =
        invokeTryReassignRequest(
            requestToken, request, 1000L, resolverHandler, requestHandler, assignmentStore);

    assertEquals(1, result);
    verify(requestHandler).reassignRequest(request, Collections.emptyList());
  }

  @Test
  void activeShopResolverAssignmentIsNotReassigned() throws Exception {
    IToken<Object> requestToken = mock(IToken.class);
    IToken<Object> assignedToken = mock(IToken.class);
    IRequest<?> request = mock(IRequest.class);
    IRequestHandler requestHandler = mock(IRequestHandler.class);
    IResolverHandler resolverHandler = mock(IResolverHandler.class);
    IRequestResolverRequestAssignmentDataStore assignmentStore =
        mock(IRequestResolverRequestAssignmentDataStore.class);
    IDeliverable deliverable = mock(IDeliverable.class);
    CreateShopRequestResolver activeResolver =
        new CreateShopRequestResolver(mock(ILocation.class), assignedToken);

    getTokenSet(ACTIVE_SET_FIELD).add(assignedToken);

    when(request.hasChildren()).thenReturn(false);
    when(request.getRequest()).thenReturn(deliverable);
    when(request.getState()).thenReturn(RequestState.IN_PROGRESS);
    doReturn(assignedToken).when(assignmentStore).getAssignmentForValue(requestToken);
    doReturn(activeResolver).when(resolverHandler).getResolver(assignedToken);

    int result =
        invokeTryReassignRequest(
            requestToken, request, 1000L, resolverHandler, requestHandler, assignmentStore);

    assertEquals(0, result);
    verify(requestHandler, never()).reassignRequest(request, Collections.emptyList());
    verify(requestHandler, never()).assignRequest(request);
  }

  private static int invokeTryReassignRequest(
      IToken<?> token,
      IRequest<?> request,
      long gameTime,
      IResolverHandler resolverHandler,
      IRequestHandler requestHandler,
      IRequestResolverRequestAssignmentDataStore assignmentStore)
      throws Exception {
    Method method =
        CreateShopResolverInjector.class.getDeclaredMethod(
            "tryReassignRequest",
            IToken.class,
            IRequest.class,
            long.class,
            IResolverHandler.class,
            IRequestHandler.class,
            IRequestResolverRequestAssignmentDataStore.class);
    method.setAccessible(true);
    return (Integer)
        method.invoke(
            null, token, request, gameTime, resolverHandler, requestHandler, assignmentStore);
  }

  @SuppressWarnings("unchecked")
  private static Set<IToken<?>> getTokenSet(String fieldName) throws Exception {
    Field field = CreateShopResolverInjector.class.getDeclaredField(fieldName);
    field.setAccessible(true);
    return (Set<IToken<?>>) field.get(null);
  }

  @SuppressWarnings("unchecked")
  private static Map<IToken<?>, Long> getReassignAttempts() throws Exception {
    Field field = CreateShopResolverInjector.class.getDeclaredField(REASSIGN_ATTEMPTS_FIELD);
    field.setAccessible(true);
    return (Map<IToken<?>, Long>) field.get(null);
  }
}
