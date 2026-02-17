package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import net.minecraft.world.level.Level;

final class CreateShopPendingDeliveryTracker {
  private final Cache<IToken<?>, CreateShopPendingDeliveryState> pending =
      CacheBuilder.newBuilder().expireAfterWrite(5, TimeUnit.MINUTES).build();

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

  boolean isDeliveryCreated(IToken<?> token) {
    CreateShopPendingDeliveryState state = pending.getIfPresent(token);
    return state != null && state.isDeliveryCreated();
  }

  void markDeliveryCreated(IToken<?> token) {
    CreateShopPendingDeliveryState state = getOrCreate(token);
    state.setDeliveryCreated(true);
  }

  void clearDeliveryCreated(IToken<?> token) {
    CreateShopPendingDeliveryState state = pending.getIfPresent(token);
    if (state != null) {
      state.setDeliveryCreated(false);
      pruneIfEmpty(token, state);
    }
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
        || state.isDeliveryCreated()
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
        && !state.isDeliveryCreated()
        && state.getCooldownUntil() <= 0L) {
      pending.invalidate(token);
    }
  }
}
