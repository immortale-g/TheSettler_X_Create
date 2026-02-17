package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.thesettler_x_create.Config;
import com.thesettler_x_create.TheSettlerXCreate;
import net.minecraft.world.level.Level;

final class CreateShopResolverCooldown {
  private final CreateShopRequestResolver resolver;

  CreateShopResolverCooldown(CreateShopRequestResolver resolver) {
    this.resolver = resolver;
  }

  boolean isRequestOnCooldown(Level level, IToken<?> token) {
    return resolver.getPendingTracker().isOnCooldown(level, token);
  }

  void markRequestOrdered(Level level, IToken<?> token) {
    resolver.getPendingTracker().setCooldown(level, token, Config.ORDER_TTL_TICKS.getAsLong());
    if (Config.DEBUG_LOGGING.getAsBoolean()) {
      resolver.getPendingTracker().setReason(token, "markRequestOrdered");
      TheSettlerXCreate.LOGGER.info(
          "[CreateShop] markRequestOrdered token={} resolver={} until={}",
          token,
          resolver.getResolverToken(),
          resolver.getPendingTracker().get(token) == null
              ? "<unknown>"
              : resolver.getPendingTracker().get(token).getCooldownUntil());
    }
  }

  void clearRequestCooldown(IToken<?> token) {
    resolver.getPendingTracker().clearCooldown(token);
    if (Config.DEBUG_LOGGING.getAsBoolean() && token != null) {
      String source = resolver.getPendingTracker().getReason(token);
      resolver.getPendingTracker().setReason(token, null);
      if (source != null) {
        TheSettlerXCreate.LOGGER.info(
            "[CreateShop] pending cleared token={} source=clearCooldown prev={}", token, source);
      }
    }
  }

  boolean isOrdered(IToken<?> token) {
    return resolver.getPendingTracker().get(token) != null;
  }

  int getOrderedCount() {
    return resolver.getPendingTracker().size();
  }

  boolean hasOrderedRequests() {
    return resolver.getPendingTracker().hasEntries();
  }

  java.util.Set<IToken<?>> getOrderedTokens() {
    return resolver.getPendingTracker().getTokens();
  }
}
