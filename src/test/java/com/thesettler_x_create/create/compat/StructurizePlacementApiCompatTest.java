package com.thesettler_x_create.create.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mock;

import com.ldtteam.structurize.placement.handlers.placement.IPlacementHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.Test;

/**
 * The placement handlers are compiled against Structurize 1.0.808+ (IPlacementContext signatures)
 * but must keep working with 1.0.807 and older. {@code test} runs this class with the older
 * Structurize on the classpath, {@code testModernStructurize} with the newer one, so each run calls
 * the handlers exactly the way that Structurize version does.
 */
class StructurizePlacementApiCompatTest {
  private static final String CONTEXT_CLASS = "com.ldtteam.structurize.placement.IPlacementContext";

  private static boolean modernRun() {
    return "modern".equals(System.getProperty("thesettler.structurizeApi"));
  }

  @Test
  void classpathMatchesTheRequestedStructurizeApi() {
    // Guards the premise of both runs: a classpath mix-up would silently skip one API path.
    if (modernRun()) {
      assertTrue(contextClassPresent());
    } else {
      assertThrows(ClassNotFoundException.class, () -> Class.forName(CONTEXT_CLASS));
    }
  }

  @Test
  void legacyStructurizeCanUseTheBeltHandler() {
    assumeFalse(modernRun());
    IPlacementHandler handler = new CreateBeltPlacementHandler();
    Level level = mock(Level.class);
    // Without tile entity data both paths return before touching the state, so no bootstrap needed.
    BlockState state = null;

    assertEquals(List.of(), handler.getRequiredItems(level, BlockPos.ZERO, state, null, false));
    assertEquals(
        IPlacementHandler.ActionProcessingResult.DENY,
        handler.handle(level, BlockPos.ZERO, state, null, false, BlockPos.ZERO, null));
  }

  @Test
  void legacyStructurizeCanLoadTheCompositeHandler() {
    assumeFalse(modernRun());
    IPlacementHandler handler = new CompositeBlockItemRemapHandler(Map.of());

    assertTrue(handler instanceof CompositeBlockItemRemapHandler);
  }

  @Test
  void modernStructurizeFindsAnImplementationForEveryAbstractHandlerMethod() {
    assumeTrue(modernRun());
    // The 0.3.3 crash: AbstractMethodError on doesWorldStateMatchBlueprintState.
    for (Class<?> handlerClass :
        List.of(CreateBeltPlacementHandler.class, CompositeBlockItemRemapHandler.class)) {
      for (Method abstractMethod : IPlacementHandler.class.getMethods()) {
        if (!Modifier.isAbstract(abstractMethod.getModifiers())) {
          continue;
        }
        Method implementation =
            assertDoesNotThrowLookup(
                handlerClass, abstractMethod.getName(), abstractMethod.getParameterTypes());
        assertFalse(
            Modifier.isAbstract(implementation.getModifiers()),
            handlerClass.getSimpleName() + "#" + abstractMethod.getName());
      }
    }
  }

  @Test
  void modernStructurizeCanUseTheBeltHandler() throws Exception {
    assumeTrue(modernRun());
    Class<?> contextClass = Class.forName(CONTEXT_CLASS);
    Object context =
        Proxy.newProxyInstance(
            contextClass.getClassLoader(), new Class<?>[] {contextClass}, (p, m, a) -> null);
    IPlacementHandler handler = new CreateBeltPlacementHandler();
    Level level = mock(Level.class);

    Method requiredItems =
        IPlacementHandler.class.getMethod(
            "getRequiredItems",
            Level.class,
            BlockPos.class,
            BlockState.class,
            net.minecraft.nbt.CompoundTag.class,
            contextClass);
    Method handle =
        IPlacementHandler.class.getMethod(
            "handle",
            Level.class,
            BlockPos.class,
            BlockState.class,
            net.minecraft.nbt.CompoundTag.class,
            contextClass);

    assertEquals(
        List.of(), requiredItems.invoke(handler, level, BlockPos.ZERO, null, null, context));
    assertEquals(
        IPlacementHandler.ActionProcessingResult.DENY,
        handle.invoke(handler, level, BlockPos.ZERO, null, null, context));
  }

  private static Method assertDoesNotThrowLookup(
      Class<?> type, String name, Class<?>[] parameterTypes) {
    try {
      return type.getMethod(name, parameterTypes);
    } catch (NoSuchMethodException ex) {
      throw new AssertionError(type.getSimpleName() + " lacks " + name, ex);
    }
  }

  private static boolean contextClassPresent() {
    try {
      Class.forName(CONTEXT_CLASS);
      return true;
    } catch (ClassNotFoundException ex) {
      return false;
    }
  }
}
