package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import com.minecolonies.api.colony.requestsystem.token.IToken;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class CreateShopRequestStateMachine {
  private final Map<IToken<?>, CreateShopFlowRecord> active = new ConcurrentHashMap<>();

  CreateShopFlowRecord getOrCreate(IToken<?> token, long now) {
    return active.computeIfAbsent(token, k -> new CreateShopFlowRecord(k, now));
  }

  CreateShopFlowRecord get(IToken<?> token) {
    return active.get(token);
  }

  boolean transition(
      IToken<?> token,
      CreateShopFlowState newState,
      long now,
      String detail,
      String stackLabel,
      int amount) {
    CreateShopFlowRecord record = getOrCreate(token, now);
    CreateShopFlowState current = record.getState();
    if (!isAllowedTransition(current, newState)) {
      return false;
    }
    record.setState(newState, now, detail, stackLabel, amount);
    return true;
  }

  void touch(IToken<?> token, long now, String detail) {
    CreateShopFlowRecord record = active.get(token);
    if (record != null) {
      record.touch(now, detail);
    }
  }

  void remove(IToken<?> token) {
    active.remove(token);
  }

  Collection<CreateShopFlowRecord> snapshot() {
    return new ArrayList<>(active.values());
  }

  boolean hasNonTerminalWork() {
    for (CreateShopFlowRecord record : active.values()) {
      if (record != null && !record.getState().isTerminal()) {
        return true;
      }
    }
    return false;
  }

  List<CreateShopFlowRecord> collectTimedOut(long now, long timeoutTicks) {
    List<CreateShopFlowRecord> timedOut = new ArrayList<>();
    if (timeoutTicks <= 0L) {
      return timedOut;
    }
    for (CreateShopFlowRecord record : active.values()) {
      if (record.getState().isTerminal()) {
        continue;
      }
      if (now - record.getLastProgressTick() >= timeoutTicks) {
        timedOut.add(record);
      }
    }
    return timedOut;
  }

  private boolean isAllowedTransition(CreateShopFlowState from, CreateShopFlowState to) {
    if (from == to) {
      return true;
    }
    if (from.isTerminal()) {
      return false;
    }
    if (to.isTerminal()) {
      return true;
    }
    return to.rank() >= from.rank();
  }
}
