package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.thesettler_x_create.Config;
import net.minecraft.world.level.Level;

final class CreateShopResolverPendingState {
  private final CreateShopRequestResolver resolver;

  CreateShopResolverPendingState(CreateShopRequestResolver resolver) {
    this.resolver = resolver;
  }

  boolean shouldNotifyPending(Level level, IToken<?> token) {
    if (level == null || token == null) {
      return false;
    }
    Long next = resolver.getPendingNotices().get(token);
    long now = level.getGameTime();
    if (next != null && now < next) {
      return false;
    }
    resolver.getPendingNotices().put(token, now + Config.PENDING_NOTICE_COOLDOWN.getAsLong());
    return true;
  }
}
