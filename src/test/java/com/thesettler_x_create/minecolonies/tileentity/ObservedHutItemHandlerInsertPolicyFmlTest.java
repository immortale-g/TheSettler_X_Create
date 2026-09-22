package com.thesettler_x_create.minecolonies.tileentity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.minecolonies.api.inventory.api.CombinedItemHandler;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Who may fill which half of the shop's inventory.
 *
 * <p>Create delivers into the racks, so a rack slot a courier filled is capacity the stock network
 * cannot deliver into and the goods stay inflight. The hut buffer is the colony side and takes what
 * a courier brings; the racks stay the fallback for a full hut. Taking things out is not restricted
 * at all, because that is how a courier gathers a delivery out of the racks through the hut block.
 *
 * <p>Needs real ItemStacks, so it runs with a loaded FML.
 */
@Tag("fml")
class ObservedHutItemHandlerInsertPolicyFmlTest {
  private static final int RACK_SLOT = 3;
  private static final int HUT_SLOT = 30;

  private final List<String> changes = new ArrayList<>();

  private final ObservedHutItemHandler.ChangeListener listener =
      new ObservedHutItemHandler.ChangeListener() {
        @Override
        public void taken(int slot, ItemStack taken) {
          changes.add("taken:" + slot + ":" + taken.getCount());
        }

        @Override
        public void changed(int slot, ItemStack key, int delta) {
          changes.add("changed:" + slot + ":" + delta);
        }
      };

  /** Stands in for "the hut buffer still has room": only the hut slot may be filled. */
  private static final ObservedHutItemHandler.InsertPolicy HUT_ONLY =
      (slot, stack) -> slot >= HUT_SLOT;

  /** Stands in for "the hut buffer is full": the racks are the fallback. */
  private static final ObservedHutItemHandler.InsertPolicy ANYWHERE = (slot, stack) -> true;

  private static ItemStack torches(int count) {
    return new ItemStack(Items.TORCH, count);
  }

  @Test
  void aRackSlotTakesNothingWhileTheHutBufferHasRoom() {
    CombinedItemHandler delegate = mock(CombinedItemHandler.class);
    ObservedHutItemHandler observed = new ObservedHutItemHandler(delegate, HUT_ONLY, listener);
    ItemStack offered = torches(16);

    ItemStack leftover = observed.insertItem(RACK_SLOT, offered, false);

    assertSame(offered, leftover, "the whole stack has to come back");
    verify(delegate, never()).insertItem(anyInt(), any(), anyBoolean());
    assertTrue(changes.isEmpty(), "nothing moved, so nothing may be reported");
  }

  @Test
  void theHutBufferTakesTheDeliveryAndTheShopHearsAboutIt() {
    CombinedItemHandler delegate = mock(CombinedItemHandler.class);
    when(delegate.insertItem(anyInt(), any(), anyBoolean())).thenReturn(ItemStack.EMPTY);
    ObservedHutItemHandler observed = new ObservedHutItemHandler(delegate, HUT_ONLY, listener);

    ItemStack leftover = observed.insertItem(HUT_SLOT, torches(16), false);

    assertTrue(leftover.isEmpty());
    assertEquals(List.of("changed:" + HUT_SLOT + ":16"), changes);
  }

  @Test
  void aFullHutBufferMakesTheRacksTheFallback() {
    CombinedItemHandler delegate = mock(CombinedItemHandler.class);
    when(delegate.insertItem(anyInt(), any(), anyBoolean())).thenReturn(ItemStack.EMPTY);
    ObservedHutItemHandler observed = new ObservedHutItemHandler(delegate, ANYWHERE, listener);

    ItemStack leftover = observed.insertItem(RACK_SLOT, torches(16), false);

    assertTrue(leftover.isEmpty(), "a courier with nowhere else to go may use a rack");
    assertEquals(List.of("changed:" + RACK_SLOT + ":16"), changes);
  }

  @Test
  void aRefusedSlotAlsoRefusesASimulatedInsertion() {
    CombinedItemHandler delegate = mock(CombinedItemHandler.class);
    ObservedHutItemHandler observed = new ObservedHutItemHandler(delegate, HUT_ONLY, listener);

    // A simulation that says yes where the real insertion says no makes the shop plan for space it
    // does not have, and the capacity stall notice would name the wrong reason.
    assertFalse(observed.insertItem(RACK_SLOT, torches(16), true).isEmpty());
    verify(delegate, never()).insertItem(anyInt(), any(), anyBoolean());
  }

  @Test
  void aRefusedSlotReportsItselfAsInvalidForTheItem() {
    CombinedItemHandler delegate = mock(CombinedItemHandler.class);
    when(delegate.isItemValid(anyInt(), any())).thenReturn(true);
    ObservedHutItemHandler observed = new ObservedHutItemHandler(delegate, HUT_ONLY, listener);

    assertFalse(observed.isItemValid(RACK_SLOT, torches(1)));
    assertTrue(observed.isItemValid(HUT_SLOT, torches(1)));
  }

  @Test
  void takingOutOfARackStaysOpen() {
    CombinedItemHandler delegate = mock(CombinedItemHandler.class);
    when(delegate.extractItem(RACK_SLOT, 16, false)).thenReturn(torches(16));
    ObservedHutItemHandler observed = new ObservedHutItemHandler(delegate, HUT_ONLY, listener);

    // This is the courier gathering a delivery: it walks to the hut block and pulls the reserved
    // goods out of the racks through this very inventory.
    ItemStack taken = observed.extractItem(RACK_SLOT, 16, false);

    assertEquals(16, taken.getCount());
    assertEquals(List.of("taken:" + RACK_SLOT + ":16"), changes);
  }
}
