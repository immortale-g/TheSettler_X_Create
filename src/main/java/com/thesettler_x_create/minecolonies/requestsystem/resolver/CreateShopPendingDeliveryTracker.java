package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import net.minecraft.world.level.Level;

/**
 * Guava-cache-backed store of {@link CreateShopPendingDeliveryState} per request token, with a
 * 30-minute TTL so a slow courier leg doesn't lose its bookkeeping mid-flight while a stuck/
 * abandoned request still eventually gets swept.
 */
final class CreateShopPendingDeliveryTracker {
  // Entries are mutated in place, which Guava does not count as a write, so expireAfterWrite
  // dropped
  // live entries mid-delivery. Expire on inactivity instead. 30 minutes leaves room for a slow
  // courier leg (traffic jam, sleep cycle) with no activity on the entry.
  private final Cache<IToken<?>, CreateShopPendingDeliveryState> pending =
      CacheBuilder.newBuilder().expireAfterAccess(30, TimeUnit.MINUTES).build();

  CreateShopPendingDeliveryState getOrCreate(IToken<?> token) {
    CreateShopPendingDeliveryState state = pending.getIfPresent(token);
    if (state != null) {
      return state;
    }
    CreateShopPendingDeliveryState created = new CreateShopPendingDeliveryState();
    pending.put(token, created);
    return created;
  }

  CreateShopPendingDeliveryState get(IToken<?> token) {
    return pending.getIfPresent(token);
  }

  void remove(IToken<?> token) {
    pending.invalidate(token);
  }

  Set<IToken<?>> getTokens() {
    return pending.asMap().keySet();
  }

  int getPendingCount(IToken<?> token) {
    CreateShopPendingDeliveryState state = pending.getIfPresent(token);
    return state == null ? 0 : state.getPendingCount();
  }

  void setPendingCount(IToken<?> token, int count) {
    CreateShopPendingDeliveryState state = getOrCreate(token);
    state.setPendingCount(count);
    pruneIfEmpty(token, state);
  }

  boolean isOnCooldown(Level level, IToken<?> token) {
    CreateShopPendingDeliveryState state = pending.getIfPresent(token);
    if (state == null) {
      return false;
    }
    long until = state.getCooldownUntil();
    if (until <= 0L) {
      return false;
    }
    long now = level.getGameTime();
    if (now >= until) {
      state.setCooldownUntil(0L);
      return false;
    }
    return true;
  }

  void setCooldown(Level level, IToken<?> token, long ttlTicks) {
    CreateShopPendingDeliveryState state = getOrCreate(token);
    state.setCooldownUntil(level.getGameTime() + ttlTicks);
  }

  void clearCooldown(IToken<?> token) {
    CreateShopPendingDeliveryState state = pending.getIfPresent(token);
    if (state != null) {
      state.setCooldownUntil(0L);
      pruneIfEmpty(token, state);
    }
  }

  /**
   * Latches that the shop handed out a delivery for this request, so the request stays with the
   * shop while MineColonies reassigns it (for example after a cancelled delivery child). Whether a
   * delivery is currently open is read from the request graph, not from here.
   */
  void markDeliveryStarted(IToken<?> token) {
    getOrCreate(token).setDeliveryStarted(true);
  }

  boolean hasDeliveryStarted(IToken<?> token) {
    CreateShopPendingDeliveryState state = pending.getIfPresent(token);
    return state != null && state.isDeliveryStarted();
  }

  void setReason(IToken<?> token, String reason) {
    CreateShopPendingDeliveryState state = pending.getIfPresent(token);
    if (state == null) {
      if (reason == null) {
        return;
      }
      state = getOrCreate(token);
    }
    state.setReason(reason);
  }

  String getReason(IToken<?> token) {
    CreateShopPendingDeliveryState state = pending.getIfPresent(token);
    return state == null ? null : state.getReason();
  }

  boolean isActive(IToken<?> token) {
    CreateShopPendingDeliveryState state = pending.getIfPresent(token);
    if (state == null) {
      return false;
    }
    return state.getPendingCount() > 0
        || state.isDeliveryStarted()
        || state.getCooldownUntil() > 0L;
  }

  boolean hasEntries() {
    return !pending.asMap().isEmpty();
  }

  int size() {
    return pending.asMap().size();
  }

  private void pruneIfEmpty(IToken<?> token, CreateShopPendingDeliveryState state) {
    if (state == null) {
      return;
    }
    if (state.getPendingCount() <= 0
        && !state.isDeliveryStarted()
        && state.getCooldownUntil() <= 0L) {
      pending.invalidate(token);
    }
  }
}
