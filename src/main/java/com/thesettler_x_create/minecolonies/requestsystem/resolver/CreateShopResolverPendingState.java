package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.thesettler_x_create.Config;
import net.minecraft.world.level.Level;

/**
 * Rate-limits "this request is still pending" notices per token so a request stuck waiting doesn't
 * re-trigger the notice every tick - each token gets at most one per {@link
 * Config#PENDING_NOTICE_COOLDOWN}.
 */
final class CreateShopResolverPendingState {
  private final java.util.Map<IToken<?>, Long> pendingNotices =
      new java.util.concurrent.ConcurrentHashMap<>();

  CreateShopResolverPendingState() {}

  boolean shouldNotifyPending(Level level, IToken<?> token) {
    if (level == null || token == null) {
      return false;
    }
    Long next = pendingNotices.get(token);
    long now = level.getGameTime();
    if (next != null && now < next) {
      return false;
    }
    pendingNotices.put(token, now + Config.PENDING_NOTICE_COOLDOWN.getAsLong());
    return true;
  }
}
