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
    assertFalse(gate.shouldDeferNetworkOrder(true, 64));
  }

  @Test
  void doesNotDeferWhenNothingNeeded() {
    assertFalse(gate.shouldDeferNetworkOrder(false, 0));
    assertFalse(gate.shouldDeferNetworkOrder(true, 0));
  }

  @Test
  void resumesPendingWheneverPendingIsPositive() {
    assertTrue(gate.shouldResumePending(true, 1));
    assertTrue(gate.shouldResumePending(false, 10));
    assertFalse(gate.shouldResumePending(true, 0));
    assertFalse(gate.shouldResumePending(false, 0));
  }

  @Test
  void resumeDecisionDoesNotDependOnWorkerAvailability() {
    assertTrue(gate.shouldResumePending(false, 3));
    assertTrue(gate.shouldResumePending(true, 3));
  }

  @Test
  void keepPendingStateOnlyWhenWorkerUnavailableAndPendingPositive() {
    assertTrue(gate.shouldKeepPendingState(false, 1));
    assertTrue(gate.shouldKeepPendingState(false, 10));
    assertFalse(gate.shouldKeepPendingState(true, 10));
    assertFalse(gate.shouldKeepPendingState(false, 0));
  }

  @Test
  void transitionUnavailableToAvailableStopsNetworkDeferral() {
    int pending = 4;

    assertTrue(gate.shouldKeepPendingState(false, pending));
    assertTrue(gate.shouldResumePending(false, pending));
    assertTrue(gate.shouldDeferNetworkOrder(false, pending));

    assertFalse(gate.shouldKeepPendingState(true, pending));
    assertTrue(gate.shouldResumePending(true, pending));
    assertFalse(gate.shouldDeferNetworkOrder(true, pending));
  }
}
