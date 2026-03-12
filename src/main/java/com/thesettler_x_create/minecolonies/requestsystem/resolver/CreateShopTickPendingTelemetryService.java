package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import com.minecolonies.api.colony.requestsystem.management.IRequestHandler;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.thesettler_x_create.Config;
import com.thesettler_x_create.TheSettlerXCreate;
import java.util.List;
import java.util.Set;
import net.minecraft.world.level.Level;

/** Rate-limited tick-pending diagnostics and perf logging state. */
final class CreateShopTickPendingTelemetryService {
  private final Object debugLock = new Object();
  private volatile long lastTickPendingDebugTime = 0L;
  private long lastPerfLogTime = 0L;
  private long lastTickPendingNanos = 0L;

  boolean shouldLogTickPending(Level level) {
    long now = level.getGameTime();
    if (now == 0L) {
      return true;
    }
    synchronized (debugLock) {
      if (now - lastTickPendingDebugTime >= Config.TICK_PENDING_DEBUG_COOLDOWN.getAsLong()) {
        lastTickPendingDebugTime = now;
        return true;
      }
      return false;
    }
  }

  void logTickPendingCandidates(IRequestHandler requestHandler, Set<IToken<?>> pendingTokens) {
    int logged = 0;
    for (IToken<?> token : List.copyOf(pendingTokens)) {
      if (logged >= 5) {
        break;
      }
      try {
        IRequest<?> req = requestHandler.getRequest(token);
        String type = req == null ? "<null>" : req.getRequest().getClass().getName();
        String state = req == null ? "<null>" : req.getState().toString();
        TheSettlerXCreate.LOGGER.info(
            "[CreateShop] tickPending: candidate {} type={} state={}", token, type, state);
        logged++;
      } catch (IllegalArgumentException ignored) {
        // Missing request.
      }
    }
  }

  void recordAndMaybeLogPerf(Level level, long tickPendingNanos) {
    lastTickPendingNanos = tickPendingNanos;
    if (!Config.DEBUG_LOGGING.getAsBoolean()) {
      return;
    }
    if (level == null) {
      return;
    }
    long now = level.getGameTime();
    if (now != 0L && now - lastPerfLogTime < Config.PERF_LOG_COOLDOWN.getAsLong()) {
      return;
    }
    lastPerfLogTime = now;
    TheSettlerXCreate.LOGGER.info("[CreateShop] perf tickPending={}us", lastTickPendingNanos / 1000L);
  }
}
