package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import com.minecolonies.api.colony.requestsystem.token.IToken;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Centralized runtime lifecycle state for pending/cooldown/child tracking. */
final class CreateShopLifecycleStateStore {
  private final CreateShopPendingDeliveryTracker pendingTracker = new CreateShopPendingDeliveryTracker();
  private final Map<IToken<?>, Long> retryingReassignAttempts = new ConcurrentHashMap<>();
  private final Map<IToken<?>, Long> deliveryChildActiveSince = new ConcurrentHashMap<>();
  private final Map<IToken<?>, Long> missingChildSince = new ConcurrentHashMap<>();
  private final Map<IToken<?>, Long> parentDeliveryActiveSince = new ConcurrentHashMap<>();
  private final Map<IToken<?>, Long> parentStaleRecoveryArmedAt = new ConcurrentHashMap<>();
  private final Map<IToken<?>, Integer> parentLastKnownChildCount = new ConcurrentHashMap<>();
  private final Map<IToken<?>, String> parentLastKnownChildren = new ConcurrentHashMap<>();
  private final Map<IToken<?>, Long> parentChildDropLastLogTick = new ConcurrentHashMap<>();
  private final Map<IToken<?>, String> deliveryRootCauseSnapshots = new ConcurrentHashMap<>();
  private final Map<IToken<?>, Long> deliveryRootCauseLastLogTick = new ConcurrentHashMap<>();

  CreateShopPendingDeliveryTracker getPendingTracker() {
    return pendingTracker;
  }

  Map<IToken<?>, Long> getRetryingReassignAttempts() {
    return retryingReassignAttempts;
  }

  Map<IToken<?>, Long> getDeliveryChildActiveSince() {
    return deliveryChildActiveSince;
  }

  Map<IToken<?>, Long> getMissingChildSince() {
    return missingChildSince;
  }

  Map<IToken<?>, Long> getParentDeliveryActiveSince() {
    return parentDeliveryActiveSince;
  }

  Map<IToken<?>, Long> getParentStaleRecoveryArmedAt() {
    return parentStaleRecoveryArmedAt;
  }

  Map<IToken<?>, Integer> getParentLastKnownChildCount() {
    return parentLastKnownChildCount;
  }

  Map<IToken<?>, String> getParentLastKnownChildren() {
    return parentLastKnownChildren;
  }

  Map<IToken<?>, Long> getParentChildDropLastLogTick() {
    return parentChildDropLastLogTick;
  }

  Map<IToken<?>, String> getDeliveryRootCauseSnapshots() {
    return deliveryRootCauseSnapshots;
  }

  Map<IToken<?>, Long> getDeliveryRootCauseLastLogTick() {
    return deliveryRootCauseLastLogTick;
  }
}
