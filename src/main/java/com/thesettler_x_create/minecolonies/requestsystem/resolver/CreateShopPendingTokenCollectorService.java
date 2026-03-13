package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.core.colony.requestsystem.management.IStandardRequestManager;
import com.thesettler_x_create.Config;
import com.thesettler_x_create.TheSettlerXCreate;
import java.util.Map;
import java.util.Set;
import net.minecraft.world.level.Level;

/**
 * Collects pending request tokens from direct assignments, ownership recovery, and pending maps.
 */
final class CreateShopPendingTokenCollectorService {
  private final CreateShopResolverOwnership ownership;
  private final CreateShopTickPendingTelemetryService tickPendingTelemetryService;

  CreateShopPendingTokenCollectorService(
      CreateShopResolverOwnership ownership,
      CreateShopTickPendingTelemetryService tickPendingTelemetryService) {
    this.ownership = ownership;
    this.tickPendingTelemetryService = tickPendingTelemetryService;
  }

  Set<IToken<?>> collectPendingTokens(
      CreateShopRequestResolver resolver,
      IStandardRequestManager standardManager,
      Level level,
      Map<IToken<?>, java.util.Collection<IToken<?>>> assignments) {
    java.util.Set<IToken<?>> assigned = new java.util.LinkedHashSet<>();
    java.util.Collection<IToken<?>> directAssigned = assignments.get(resolver.getResolverToken());
    if (directAssigned != null && !directAssigned.isEmpty()) {
      assigned.addAll(directAssigned);
    }
    java.util.Set<IToken<?>> assignedByOwner =
        ownership.collectAssignedTokensByRequestResolver(standardManager, assignments);
    if (!assignedByOwner.isEmpty()) {
      int before = assigned.size();
      assigned.addAll(assignedByOwner);
      if (Config.DEBUG_LOGGING.getAsBoolean()
          && tickPendingTelemetryService.shouldLogTickPending(level)
          && (before == 0 || assignedByOwner.size() > before)) {
        TheSettlerXCreate.LOGGER.info(
            "[CreateShop] tickPending owner-sync: resolverId={} directAssignments={} ownerAssignments={} effective={}",
            resolver.getResolverToken(),
            directAssigned == null ? 0 : directAssigned.size(),
            assignedByOwner.size(),
            assigned.size());
      }
    } else {
      java.util.Set<IToken<?>> recovered =
          ownership.collectAssignedTokensFromLocalResolvers(standardManager, assignments);
      if (!recovered.isEmpty()) {
        assigned.addAll(recovered);
        if (Config.DEBUG_LOGGING.getAsBoolean()
            && tickPendingTelemetryService.shouldLogTickPending(level)) {
          TheSettlerXCreate.LOGGER.info(
              "[CreateShop] tickPending assignment drift recovered: resolverId={} recoveredAssignments={}",
              resolver.getResolverToken(),
              recovered.size());
        }
      }
    }
    if (Config.DEBUG_LOGGING.getAsBoolean() && assigned.isEmpty()) {
      TheSettlerXCreate.LOGGER.info(
          "[CreateShop] tickPending no assignments for resolverId={} assignmentsKeys={}",
          resolver.getResolverToken(),
          assignments.keySet());
    }
    java.util.Set<IToken<?>> pendingTokens = new java.util.HashSet<>();
    pendingTokens.addAll(assigned);
    pendingTokens.addAll(resolver.getPendingTracker().getTokens());
    if (pendingTokens.isEmpty()) {
      if (Config.DEBUG_LOGGING.getAsBoolean()
          && tickPendingTelemetryService.shouldLogTickPending(level)) {
        if (resolver.getPendingTracker().hasEntries()) {
          TheSettlerXCreate.LOGGER.info(
              "[CreateShop] tickPending: empty snapshot but maps ordered={} pendingCounts={}",
              resolver.getCooldown().getOrderedCount(),
              resolver.getPendingTracker().size());
        }
      }
      if (Config.DEBUG_LOGGING.getAsBoolean()
          && tickPendingTelemetryService.shouldLogTickPending(level)) {
        TheSettlerXCreate.LOGGER.info(
            "[CreateShop] tickPending: no assigned or ordered requests for resolver {}",
            resolver.getResolverToken());
      }
    }
    return pendingTokens;
  }
}
