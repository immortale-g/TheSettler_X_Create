package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.core.colony.requestsystem.management.IStandardRequestManager;
import net.minecraft.world.level.Level;

final class CreateShopResolverRecheck {
  private final CreateShopRequestResolver resolver;
  private final CreateShopResolverDiagnostics diagnostics;

  CreateShopResolverRecheck(
      CreateShopRequestResolver resolver, CreateShopResolverDiagnostics diagnostics) {
    this.resolver = resolver;
    this.diagnostics = diagnostics;
  }

  void scheduleParentChildRecheck(IStandardRequestManager manager, IToken<?> parentToken) {
    var level = manager.getColony().getWorld();
    resolver.getParentChildrenRecheck().put(parentToken, level.getGameTime() + 20L);
  }

  void processParentChildRechecks(IStandardRequestManager manager, Level level) {
    var recheckMap = resolver.getParentChildrenRecheck();
    if (recheckMap.isEmpty()) {
      return;
    }
    long now = level.getGameTime();
    var iterator = recheckMap.entrySet().iterator();
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
