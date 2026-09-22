package com.thesettler_x_create.minecolonies.tileentity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.minecolonies.api.inventory.api.CombinedItemHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;

// ItemStack cannot be loaded without a Minecraft bootstrap, so the extraction path is pinned by
// source and the forwarding is checked on the methods that do not touch item stacks.
class ObservedHutItemHandlerTest {
  private static final ObservedHutItemHandler.ChangeListener NO_OP =
      new ObservedHutItemHandler.ChangeListener() {
        @Override
        public void taken(int slot, ItemStack taken) {}

        @Override
        public void changed(int slot, ItemStack key, int delta) {}
      };

  private static final ObservedHutItemHandler.InsertPolicy OPEN = (slot, stack) -> true;

  @Test
  void reportsOnlyRealNonEmptyExtractions() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/tileentity/ObservedHutItemHandler.java"));

    assertTrue(
        source.contains("ItemStack extracted = delegate.extractItem(slot, amount, simulate);"));
    assertTrue(source.contains("if (!simulate && !extracted.isEmpty()) {"));
    assertTrue(source.contains("listener.taken(slot, extracted.copy());"));
  }

  @Test
  void reportsRealInsertionsAndOverwrittenSlots() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/tileentity/ObservedHutItemHandler.java"));

    assertTrue(source.contains("if (!simulate && !stack.isEmpty() && inserted > 0) {"));
    assertTrue(source.contains("listener.changed(slot, stack.copy(), inserted);"));
    assertTrue(source.contains("listener.changed(slot, before, -before.getCount());"));
    assertTrue(source.contains("listener.changed(slot, stack.copy(), stack.getCount());"));
  }

  @Test
  void overridesEveryPublicMethodOfTheCombinedInventory() throws Exception {
    for (Method method : CombinedItemHandler.class.getDeclaredMethods()) {
      if (!Modifier.isPublic(method.getModifiers()) || method.isSynthetic()) {
        continue;
      }
      Method override =
          ObservedHutItemHandler.class.getMethod(method.getName(), method.getParameterTypes());
      assertEquals(
          ObservedHutItemHandler.class,
          override.getDeclaringClass(),
          "not forwarded: " + method.getName());
    }
  }

  @Test
  void forwardsSlotLayoutToTheHutInventory() {
    CombinedItemHandler hut = mock(CombinedItemHandler.class);
    when(hut.getSlots()).thenReturn(54);
    when(hut.getLastIndex(10)).thenReturn(27);
    when(hut.getSlotLimit(5)).thenReturn(64);
    ObservedHutItemHandler observed = new ObservedHutItemHandler(hut, OPEN, NO_OP);

    assertEquals(54, observed.getSlots());
    assertEquals(27, observed.getLastIndex(10));
    assertEquals(64, observed.getSlotLimit(5));
    assertSame(hut, observed.delegate());
  }

  @Test
  void twoWrappersOfTheSameInventoryAreEqual() {
    CombinedItemHandler hut = mock(CombinedItemHandler.class);
    CombinedItemHandler otherHut = mock(CombinedItemHandler.class);

    assertEquals(
        new ObservedHutItemHandler(hut, OPEN, NO_OP), new ObservedHutItemHandler(hut, OPEN, NO_OP));
    assertNotEquals(
        new ObservedHutItemHandler(hut, OPEN, NO_OP),
        new ObservedHutItemHandler(otherHut, OPEN, NO_OP));
  }
}
