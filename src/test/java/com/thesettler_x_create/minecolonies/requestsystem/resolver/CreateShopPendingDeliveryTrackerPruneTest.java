package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.minecolonies.api.colony.requestsystem.token.IToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Behavior tests for CreateShopPendingDeliveryTracker's pruning and state management.
 *
 * <p>Covers the non-TTL path: entries that transition back to fully-empty state should be removed
 * by pruneIfEmpty so the tracker does not accumulate phantom entries for completed requests.
 */
class CreateShopPendingDeliveryTrackerPruneTest {
  private CreateShopPendingDeliveryTracker tracker;

  @BeforeEach
  void setUp() {
    tracker = new CreateShopPendingDeliveryTracker();
  }

  @SuppressWarnings("unchecked")
  private IToken<?> token(String id) {
    IToken<String> t = (IToken<String>) mock(IToken.class);
    when(t.getIdentifier()).thenReturn(id);
    return t;
  }

  @Test
  void setPendingCountToZeroWithNoOtherStatePrunesEntry() {
    IToken<?> tok = token("prune-a");
    tracker.setPendingCount(tok, 5);
    assertEquals(5, tracker.getPendingCount(tok));
    assertTrue(tracker.getTokens().contains(tok));

    tracker.setPendingCount(tok, 0);

    assertEquals(0, tracker.getPendingCount(tok));
    assertFalse(tracker.getTokens().contains(tok));
    assertNull(tracker.get(tok));
  }

  @Test
  void setPendingCountToZeroDoesNotPruneWhenDeliveryCreated() {
    IToken<?> tok = token("prune-b");
    tracker.setPendingCount(tok, 3);
    tracker.markDeliveryCreated(tok);
    assertTrue(tracker.isDeliveryCreated(tok));
    assertTrue(tracker.hasDeliveryStarted(tok));

    tracker.setPendingCount(tok, 0);

    // Entry must survive because deliveryCreated and deliveryStarted are still true.
    assertTrue(tracker.isDeliveryCreated(tok));
    assertTrue(tracker.getTokens().contains(tok));
  }

  @Test
  void clearDeliveryCreatedKeepsEntryAliveWhenDeliveryStartedIsSet() {
    IToken<?> tok = token("prune-c");
    tracker.markDeliveryCreated(tok);
    assertTrue(tracker.isDeliveryCreated(tok));
    assertTrue(tracker.hasDeliveryStarted(tok));

    tracker.clearDeliveryCreated(tok);

    // deliveryCreated is now false, but deliveryStarted must still be true.
    assertFalse(tracker.isDeliveryCreated(tok));
    assertTrue(tracker.hasDeliveryStarted(tok));
    // Entry must NOT be pruned while deliveryStarted is true.
    assertTrue(tracker.getTokens().contains(tok));
  }

  @Test
  void removeEvictsEntryImmediately() {
    IToken<?> tok = token("prune-d");
    tracker.setPendingCount(tok, 2);
    assertTrue(tracker.getTokens().contains(tok));

    tracker.remove(tok);

    assertFalse(tracker.getTokens().contains(tok));
    assertEquals(0, tracker.getPendingCount(tok));
  }

  @Test
  void getPendingCountReturnsZeroForAbsentToken() {
    IToken<?> tok = token("absent");
    assertEquals(0, tracker.getPendingCount(tok));
  }

  @Test
  void isActiveReturnsTrueForPositivePendingCount() {
    IToken<?> tok = token("active-a");
    tracker.setPendingCount(tok, 1);
    assertTrue(tracker.isActive(tok));
  }

  @Test
  void isActiveReturnsTrueWhenDeliveryCreatedEvenWithZeroPending() {
    IToken<?> tok = token("active-b");
    tracker.markDeliveryCreated(tok);
    tracker.setPendingCount(tok, 0);
    assertTrue(tracker.isActive(tok));
  }

  @Test
  void isActiveReturnsFalseForAbsentToken() {
    assertFalse(tracker.isActive(token("inactive")));
  }

  @Test
  void sizeReflectsLiveEntryCount() {
    IToken<?> a = token("sz-a");
    IToken<?> b = token("sz-b");
    assertEquals(0, tracker.size());
    tracker.setPendingCount(a, 1);
    assertEquals(1, tracker.size());
    tracker.setPendingCount(b, 3);
    assertEquals(2, tracker.size());
    tracker.remove(a);
    assertEquals(1, tracker.size());
  }
}
