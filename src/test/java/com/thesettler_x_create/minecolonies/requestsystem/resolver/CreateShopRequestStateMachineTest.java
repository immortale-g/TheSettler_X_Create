package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.minecolonies.api.colony.requestsystem.token.IToken;
import java.util.List;
import org.junit.jupiter.api.Test;

class CreateShopRequestStateMachineTest {
  @Test
  void transitionIsMonotonicForNonTerminalStates() {
    CreateShopRequestStateMachine stateMachine = new CreateShopRequestStateMachine();
    IToken<?> token = token("req-a");

    assertTrue(
        stateMachine.transition(token, CreateShopFlowState.ELIGIBILITY_CHECK, 10L, "start", "", 0));
    assertTrue(
        stateMachine.transition(
            token, CreateShopFlowState.ORDERED_FROM_NETWORK, 12L, "ordered", "Stone Shovel", 1));
    assertFalse(
        stateMachine.transition(
            token, CreateShopFlowState.ELIGIBILITY_CHECK, 13L, "backward", "", 0));
    assertEquals(CreateShopFlowState.ORDERED_FROM_NETWORK, stateMachine.get(token).getState());
  }

  @Test
  void terminalStateBlocksFurtherTransitions() {
    CreateShopRequestStateMachine stateMachine = new CreateShopRequestStateMachine();
    IToken<?> token = token("req-b");

    assertTrue(
        stateMachine.transition(token, CreateShopFlowState.REQUEST_COMPLETED, 30L, "done", "", 0));
    assertFalse(
        stateMachine.transition(token, CreateShopFlowState.DELIVERY_CREATED, 31L, "late", "", 0));
    assertEquals(CreateShopFlowState.REQUEST_COMPLETED, stateMachine.get(token).getState());
  }

  @Test
  void timeoutCollectsOnlyActiveNonTerminalRecords() {
    CreateShopRequestStateMachine stateMachine = new CreateShopRequestStateMachine();
    IToken<?> active = token("req-c");
    IToken<?> done = token("req-d");

    assertTrue(
        stateMachine.transition(
            active, CreateShopFlowState.ORDERED_FROM_NETWORK, 100L, "ordered", "Stone Shovel", 1));
    assertTrue(
        stateMachine.transition(
            done, CreateShopFlowState.REQUEST_COMPLETED, 100L, "done", "Stone Shovel", 1));

    stateMachine.touch(active, 105L, "waiting");
    List<CreateShopFlowRecord> timedOut = stateMachine.collectTimedOut(200L, 80L);

    assertEquals(1, timedOut.size());
    assertEquals(active, timedOut.get(0).getRequestToken());
  }

  @Test
  void nonTerminalWorkFlagIgnoresTerminalHistory() {
    CreateShopRequestStateMachine stateMachine = new CreateShopRequestStateMachine();
    IToken<?> token = token("req-e");

    assertFalse(stateMachine.hasNonTerminalWork());
    assertTrue(
        stateMachine.transition(token, CreateShopFlowState.REQUEST_COMPLETED, 40L, "done", "", 0));
    assertFalse(stateMachine.hasNonTerminalWork());
    assertTrue(
        stateMachine.transition(
            token("req-f"), CreateShopFlowState.ORDERED_FROM_NETWORK, 41L, "ordered", "", 1));
    assertTrue(stateMachine.hasNonTerminalWork());
  }

  private IToken<?> token(String id) {
    IToken<?> token = mock(IToken.class);
    when(token.getIdentifier()).thenReturn(id);
    when(token.toString()).thenReturn("token:" + id);
    return token;
  }
}
