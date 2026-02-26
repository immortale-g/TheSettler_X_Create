package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.minecolonies.api.colony.requestsystem.location.ILocation;
import com.minecolonies.api.colony.requestsystem.request.RequestState;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.core.colony.requestsystem.management.IStandardRequestManager;
import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CreateShopRequestResolverTwoPhaseStaleRecoveryRuntimeTest {
  private CreateShopRequestResolver resolver;

  @BeforeEach
  void setUp() {
    ILocation resolverLocation = mock(ILocation.class);
    when(resolverLocation.getDimension()).thenReturn(Level.OVERWORLD);
    when(resolverLocation.getInDimensionLocation()).thenReturn(BlockPos.ZERO);
    IToken<?> resolverToken = mock(IToken.class);
    resolver = new CreateShopRequestResolver(resolverLocation, resolverToken);
  }

  @Test
  void staleRecoveryArmsFirstAndMutatesOnlyAfterRecheckDelay() throws Exception {
    AtomicLong now = new AtomicLong(1_000L);
    Level level = mock(Level.class);
    when(level.getGameTime()).thenAnswer(invocation -> now.get());

    IStandardRequestManager manager = mock(IStandardRequestManager.class, RETURNS_DEEP_STUBS);
    when(manager.getColony().getWorld()).thenReturn(level);

    IToken<?> parentToken = token(UUID.randomUUID());
    IToken<?> childToken = token(UUID.randomUUID());

    boolean initiallyStale =
        invokeBoolean(
            "isStaleDeliveryChild",
            new Class<?>[] {Level.class, IToken.class, IToken.class, RequestState.class},
            level,
            parentToken,
            childToken,
            RequestState.IN_PROGRESS);
    assertFalse(initiallyStale);

    now.addAndGet(200_000L);
    boolean staleAfterTimeout =
        invokeBoolean(
            "isStaleDeliveryChild",
            new Class<?>[] {Level.class, IToken.class, IToken.class, RequestState.class},
            level,
            parentToken,
            childToken,
            RequestState.IN_PROGRESS);
    assertTrue(staleAfterTimeout);

    boolean firstArm =
        invokeBoolean(
            "isStaleRecoveryArmed",
            new Class<?>[] {Level.class, IStandardRequestManager.class, IToken.class},
            level,
            manager,
            parentToken);
    assertFalse(firstArm);
    assertEquals(now.get() + 20L, resolver.getParentChildrenRecheck().get(parentToken));

    boolean secondBeforeDelay =
        invokeBoolean(
            "isStaleRecoveryArmed",
            new Class<?>[] {Level.class, IStandardRequestManager.class, IToken.class},
            level,
            manager,
            parentToken);
    assertFalse(secondBeforeDelay);

    now.addAndGet(21L);
    boolean armedAfterDelay =
        invokeBoolean(
            "isStaleRecoveryArmed",
            new Class<?>[] {Level.class, IStandardRequestManager.class, IToken.class},
            level,
            manager,
            parentToken);
    assertTrue(armedAfterDelay);
  }

  private boolean invokeBoolean(String methodName, Class<?>[] signature, Object... args)
      throws Exception {
    Method method = CreateShopRequestResolver.class.getDeclaredMethod(methodName, signature);
    method.setAccessible(true);
    return (boolean) method.invoke(resolver, args);
  }

  private IToken<?> token(UUID id) {
    IToken<?> token = mock(IToken.class);
    when(token.getIdentifier()).thenReturn(id);
    when(token.toString()).thenReturn("token:" + id);
    return token;
  }
}
