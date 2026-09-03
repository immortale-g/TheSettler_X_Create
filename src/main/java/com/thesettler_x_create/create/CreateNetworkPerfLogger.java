package com.thesettler_x_create.create;

import com.thesettler_x_create.Config;
import com.thesettler_x_create.TheSettlerXCreate;
import com.thesettler_x_create.minecolonies.tileentity.TileEntityCreateShop;

/** Tracks and rate-limits debug perf logging for one {@link CreateNetworkFacade}. */
final class CreateNetworkPerfLogger {
  private long lastPerfLogTime = 0L;
  private long lastSummaryNanos = 0L;
  private long lastBroadcastNanos = 0L;
  private int lastBroadcastCount = 0;

  void recordSummary(long nanos, TileEntityCreateShop shop) {
    lastSummaryNanos = nanos;
    maybeLogPerf(shop);
  }

  void recordBroadcast(long nanos, int count, TileEntityCreateShop shop) {
    lastBroadcastNanos = nanos;
    lastBroadcastCount = count;
    maybeLogPerf(shop);
  }

  private void maybeLogPerf(TileEntityCreateShop shop) {
    if (!Config.DEBUG_LOGGING.getAsBoolean()) {
      return;
    }
    if (shop == null || shop.getLevel() == null) {
      return;
    }
    long now = shop.getLevel().getGameTime();
    if (now != 0L && now - lastPerfLogTime < Config.PERF_LOG_COOLDOWN.getAsLong()) {
      return;
    }
    lastPerfLogTime = now;
    TheSettlerXCreate.LOGGER.info(
        "[CreateShop] perf summary: getSummary={}us broadcast={}us items={}",
        lastSummaryNanos / 1000L,
        lastBroadcastNanos / 1000L,
        lastBroadcastCount);
  }
}
