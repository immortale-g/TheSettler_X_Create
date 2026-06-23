package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import com.minecolonies.api.colony.requestsystem.token.IToken;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.nbt.CompoundTag;

final class CreateShopRequestStateMachine {
  private static final String TAG_FLOW_STATES = "FlowStates";

  private final Map<IToken<?>, CreateShopFlowRecord> active = new ConcurrentHashMap<>();

  /**
   * UUID → FlowState loaded from NBT, consumed the first time a matching token is seen via
   * getOrCreate. Allows exact state restoration after world reload without heuristic derivation.
   */
  private final Map<UUID, CreateShopFlowState> pendingRestore = new ConcurrentHashMap<>();

  CreateShopFlowRecord getOrCreate(IToken<?> token, long now) {
    CreateShopFlowRecord record =
        active.computeIfAbsent(token, k -> new CreateShopFlowRecord(k, now));
    // Restore persisted FlowState if the record hasn't progressed past NEW yet.
    if (!pendingRestore.isEmpty() && record.getState() == CreateShopFlowState.NEW) {
      UUID uuid = CreateShopRequestResolver.toRequestId(token);
      if (uuid != null) {
        CreateShopFlowState restored = pendingRestore.remove(uuid);
        if (restored != null) {
          record.setState(restored, now, "nbt-restore", record.getStackLabel(), record.getAmount());
        }
      }
    }
    return record;
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

  /**
   * Saves non-terminal, progressed FlowStates to NBT so they survive world reload. Only states
   * beyond NEW and ELIGIBILITY_CHECK are saved — those are re-derivable from the MineColonies
   * request graph. States from ORDERED_FROM_NETWORK onward represent actual progress and must be
   * restored exactly to prevent double-ordering.
   */
  void saveFlowStates(CompoundTag tag) {
    if (active.isEmpty()) {
      return;
    }
    CompoundTag flowStates = new CompoundTag();
    for (Map.Entry<IToken<?>, CreateShopFlowRecord> entry : active.entrySet()) {
      CreateShopFlowRecord record = entry.getValue();
      if (record == null) {
        continue;
      }
      CreateShopFlowState state = record.getState();
      if (state == null || state.isTerminal()) {
        continue;
      }
      // Skip initial/ephemeral states — they're re-derived on reload.
      if (state == CreateShopFlowState.NEW || state == CreateShopFlowState.ELIGIBILITY_CHECK) {
        continue;
      }
      UUID uuid = CreateShopRequestResolver.toRequestId(entry.getKey());
      if (uuid != null) {
        flowStates.putString(uuid.toString(), state.name());
      }
    }
    if (!flowStates.isEmpty()) {
      tag.put(TAG_FLOW_STATES, flowStates);
    }
  }

  /**
   * Loads FlowStates from NBT into pendingRestore. States are applied lazily in getOrCreate the
   * first time each token is seen after reload.
   */
  void loadFlowStates(CompoundTag flowStates) {
    pendingRestore.clear();
    if (flowStates == null) {
      return;
    }
    for (String uuidStr : flowStates.getAllKeys()) {
      try {
        UUID uuid = UUID.fromString(uuidStr);
        CreateShopFlowState state = CreateShopFlowState.valueOf(flowStates.getString(uuidStr));
        if (!state.isTerminal()) {
          pendingRestore.put(uuid, state);
        }
      } catch (Exception ignored) {
        // Malformed or unknown state name — skip; worst case is re-derivation by RehydrateService.
      }
    }
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
