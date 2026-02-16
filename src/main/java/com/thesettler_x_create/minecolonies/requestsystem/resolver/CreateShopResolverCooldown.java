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
    Long until = resolver.getOrderedRequests().get(token);
    if (until == null) {
      return false;
    }
    long now = level.getGameTime();
    if (now >= until) {
      resolver.getOrderedRequests().remove(token);
      return false;
    }
    return true;
  }

  void markRequestOrdered(Level level, IToken<?> token) {
    long until = level.getGameTime() + Config.ORDER_TTL_TICKS.getAsLong();
    resolver.getOrderedRequests().put(token, until);
    if (Config.DEBUG_LOGGING.getAsBoolean()) {
      CreateShopRequestResolver.getPendingSources().put(token, "markRequestOrdered");
      TheSettlerXCreate.LOGGER.info(
          "[CreateShop] markRequestOrdered token={} resolver={} until={}",
          token,
          resolver.getResolverToken(),
          until);
    }
  }

  void clearRequestCooldown(IToken<?> token) {
    resolver.getOrderedRequests().remove(token);
    if (Config.DEBUG_LOGGING.getAsBoolean() && token != null) {
      String source = CreateShopRequestResolver.getPendingSources().remove(token);
      if (source != null) {
        TheSettlerXCreate.LOGGER.info(
            "[CreateShop] pending cleared token={} source=clearCooldown prev={}", token, source);
      }
    }
  }
}
