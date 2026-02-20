package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CreateShopWorkerAvailabilityGateTest {
  private final CreateShopWorkerAvailabilityGate gate = new CreateShopWorkerAvailabilityGate();

  @Test
  void defersNetworkOrderWhenWorkerUnavailableAndNeedPositive() {
    assertTrue(gate.shouldDeferNetworkOrder(false, 1));
    assertTrue(gate.shouldDeferNetworkOrder(false, 64));
  }

  @Test
  void doesNotDeferWhenWorkerAvailableOrNothingNeeded() {
    assertFalse(gate.shouldDeferNetworkOrder(true, 5));
    assertFalse(gate.shouldDeferNetworkOrder(false, 0));
    assertFalse(gate.shouldDeferNetworkOrder(true, 0));
  }

  @Test
  void resumesPendingOnlyWhenWorkerAvailableAndPendingPositive() {
    assertTrue(gate.shouldResumePending(true, 1));
    assertTrue(gate.shouldResumePending(true, 10));
    assertFalse(gate.shouldResumePending(false, 10));
    assertFalse(gate.shouldResumePending(true, 0));
  }

  @Test
  void resumeDecisionFlipsWhenWorkerBecomesAvailable() {
    assertFalse(gate.shouldResumePending(false, 3));
    assertTrue(gate.shouldResumePending(true, 3));
  }

  @Test
  void keepPendingStateOnlyWhenUnavailableAndPendingPositive() {
    assertTrue(gate.shouldKeepPendingState(false, 1));
    assertTrue(gate.shouldKeepPendingState(false, 10));
    assertFalse(gate.shouldKeepPendingState(true, 10));
    assertFalse(gate.shouldKeepPendingState(false, 0));
  }
}
