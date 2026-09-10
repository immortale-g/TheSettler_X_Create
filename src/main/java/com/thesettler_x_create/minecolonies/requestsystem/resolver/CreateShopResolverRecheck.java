package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.core.colony.requestsystem.management.IStandardRequestManager;
import net.minecraft.world.level.Level;

/**
 * Schedules and processes a one-shot delayed re-check of a parent request's children (20 ticks
 * later), used after an operation that changes child linkage so {@link
 * CreateShopResolverDiagnostics} can log a before/after snapshot once the change has settled.
 */
final class CreateShopResolverRecheck {
  private final CreateShopResolverDiagnostics diagnostics;
  private final java.util.Map<IToken<?>, Long> parentChildrenRecheck =
      new java.util.concurrent.ConcurrentHashMap<>();

  CreateShopResolverRecheck(
      CreateShopRequestResolver resolver, CreateShopResolverDiagnostics diagnostics) {
    this.diagnostics = diagnostics;
  }

  void scheduleParentChildRecheck(IStandardRequestManager manager, IToken<?> parentToken) {
    var level = manager.getColony().getWorld();
    parentChildrenRecheck.put(parentToken, level.getGameTime() + 20L);
  }

  void scheduleParentChildRecheckAtForTest(IToken<?> parentToken, long dueTick) {
    if (parentToken == null) {
      return;
    }
    parentChildrenRecheck.put(parentToken, dueTick);
  }

  Long getParentChildRecheckDueTick(IToken<?> parentToken) {
    return parentToken == null ? null : parentChildrenRecheck.get(parentToken);
  }

  void processParentChildRechecks(IStandardRequestManager manager, Level level) {
    if (parentChildrenRecheck.isEmpty()) {
      return;
    }
    long now = level.getGameTime();
    var iterator = parentChildrenRecheck.entrySet().iterator();
    while (iterator.hasNext()) {
      var entry = iterator.next();
      Long due = entry.getValue();
      if (due != null && due > now) {
        continue;
      }
      diagnostics.logParentChildrenState(manager, entry.getKey(), "recheck");
      iterator.remove();
    }
  }
}
