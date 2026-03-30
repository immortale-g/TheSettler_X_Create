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
import java.lang.reflect.Field;
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
        invokeLifecycleBoolean(
            "isStaleDeliveryChild",
            new Class<?>[] {
              CreateShopRequestResolver.class,
              Level.class,
              IToken.class,
              IToken.class,
              RequestState.class
            },
            resolver,
            level,
            parentToken,
            childToken,
            RequestState.IN_PROGRESS);
    assertFalse(initiallyStale);

    now.addAndGet(200_000L);
    boolean staleAfterTimeout =
        invokeLifecycleBoolean(
            "isStaleDeliveryChild",
            new Class<?>[] {
              CreateShopRequestResolver.class,
              Level.class,
              IToken.class,
              IToken.class,
              RequestState.class
            },
            resolver,
            level,
            parentToken,
            childToken,
            RequestState.IN_PROGRESS);
    assertTrue(staleAfterTimeout);

    boolean firstArm =
        invokeLifecycleBoolean(
            "isStaleRecoveryArmed",
            new Class<?>[] {
              CreateShopRequestResolver.class,
              Level.class,
              IStandardRequestManager.class,
              IToken.class
            },
            resolver,
            level,
            manager,
            parentToken);
    assertFalse(firstArm);
    assertEquals(now.get() + 20L, resolver.getParentChildRecheckDueTick(parentToken));

    boolean secondBeforeDelay =
        invokeLifecycleBoolean(
            "isStaleRecoveryArmed",
            new Class<?>[] {
              CreateShopRequestResolver.class,
              Level.class,
              IStandardRequestManager.class,
              IToken.class
            },
            resolver,
            level,
            manager,
            parentToken);
    assertFalse(secondBeforeDelay);

    now.addAndGet(21L);
    boolean armedAfterDelay =
        invokeLifecycleBoolean(
            "isStaleRecoveryArmed",
            new Class<?>[] {
              CreateShopRequestResolver.class,
              Level.class,
              IStandardRequestManager.class,
              IToken.class
            },
            resolver,
            level,
            manager,
            parentToken);
    assertTrue(armedAfterDelay);
  }

  private boolean invokeLifecycleBoolean(String methodName, Class<?>[] signature, Object... args)
      throws Exception {
    Field field = CreateShopRequestResolver.class.getDeclaredField("deliveryChildLifecycleService");
    field.setAccessible(true);
    Object lifecycleService = field.get(resolver);
    Method method = lifecycleService.getClass().getDeclaredMethod(methodName, signature);
    method.setAccessible(true);
    return (boolean) method.invoke(lifecycleService, args);
  }

  private IToken<?> token(UUID id) {
    IToken<?> token = mock(IToken.class);
    when(token.getIdentifier()).thenReturn(id);
    when(token.toString()).thenReturn("token:" + id);
    return token;
  }
}
