package com.thesettler_x_create.minecolonies.command;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.core.colony.requestsystem.management.IStandardRequestManager;
import com.thesettler_x_create.TheSettlerXCreate;
import com.thesettler_x_create.minecolonies.building.BuildingCreateShop;
import net.minecraft.commands.CommandSourceStack;

/**
 * Handles the {@code reset_live_state} command for the Create Shop building.
 *
 * <p>Orchestrates four drain-round collaborators, each extracted for one concern: {@link
 * CreateShopRequestGraphCanceller} (root-first request-graph cancellation, both the Create
 * Shop-owned pass and the hard-reset "every assigned root" pass), {@link
 * CreateShopLiveDeliveryDrainer} (active local-delivery cancellation), {@link
 * CreateShopAssignmentReconciler} (stale-assignment pruning and stuck-courier kicks), and {@link
 * CreateShopWarehouseQueuePruner} (warehouse request-queue pruning). All four route their
 * request-graph exceptions through {@link #handleGraphException} instead of each hand-rolling the
 * same classify-log-or-ignore block.
 *
 * <p>Extracted from {@link CreateShopMaintenanceCommands} to separate destructive maintenance
 * operations from diagnostic and test-harness commands. {@code prepare_uninstall} lives in {@link
 * CreateShopUninstallCommands}; helpers shared between the two live in {@link
 * CreateShopCommandSupport}.
 */
final class CreateShopResetCommands {
  private CreateShopResetCommands() {}

  // -------------------------------------------------------------------------
  // Package-visible entry point (called from CreateShopMaintenanceCommands)
  // -------------------------------------------------------------------------

  static ResetLiveStateResult resetLiveState(
      CommandSourceStack source, boolean forceWarehouseQueueClear) {
    ResetLiveStateResult result = new ResetLiveStateResult();
    for (IColony colony : CreateShopCommandSupport.resolveTargetColonies(source)) {
      result.colonies++;
      if (!(colony.getRequestManager() instanceof IStandardRequestManager standard)) {
        continue;
      }

      java.util.Set<BuildingCreateShop> shops = CreateShopCommandSupport.collectCreateShops(colony);
      result.shops += shops.size();
      int initialActiveLocalDeliveries =
          CreateShopLiveDeliveryDrainer.countShopsWithActiveLocalDeliveries(colony, shops);
      int drainRounds = Math.max(1, 3 + (initialActiveLocalDeliveries > 0 ? 1 : 0));
      result.drainRounds += drainRounds;
      for (int round = 0; round < drainRounds; round++) {
        CreateShopRequestGraphCanceller.cancelCreateShopOwnedRequestsGraphAware(standard, result);
        CreateShopRequestGraphCanceller.cancelAllAssignedRequestsGraphAware(standard, result);
        CreateShopLiveDeliveryDrainer.cancelActiveLocalDeliveries(colony, standard, result);
        CreateShopAssignmentReconciler.reconcileAssignmentsAndKickCouriers(standard, result);
      }

      // Always prune stale/terminal queue entries; this is conservative and prevents stale
      // warehouse queue tokens from keeping courier jobs in a stuck loop after request cleanup.
      CreateShopWarehouseQueuePruner.clearWarehouseQueues(colony, standard, result);

      int remainingActiveLocalDeliveries =
          CreateShopLiveDeliveryDrainer.countShopsWithActiveLocalDeliveries(colony, shops);
      if (forceWarehouseQueueClear) {
        CreateShopWarehouseQueuePruner.clearWarehouseQueues(colony, standard, result);
      }
      if (remainingActiveLocalDeliveries > 0
          || CreateShopRequestGraphCanceller.hasActiveCreateShopRootRequests(standard)) {
        result.blockedActiveDeliveries += remainingActiveLocalDeliveries;
        result.runtimeTrackingSkipped += shops.size();
        result.drainResiduals += 1;
        continue;
      }

      for (BuildingCreateShop shop : shops) {
        try {
          result.runtimeTrackingCleared += Math.max(0, shop.clearRuntimeTrackingForDebug());
        } catch (Exception ex) {
          result.errors++;
          TheSettlerXCreate.LOGGER.warn(
              "[CreateShop] reset_live_state tracking clear failed shop={} error={}",
              shop.getLocation() == null
                  ? "<unknown>"
                  : shop.getLocation().getInDimensionLocation(),
              ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
        }
      }
    }
    return result;
  }

  /**
   * Classifies a caught exception from request-graph traversal: a MineColonies "stale request
   * graph" condition (see {@link CreateShopCommandSupport#isStaleRequestGraphException}) is
   * expected - this returns {@code true} and leaves pruning to the caller - anything else is a
   * real error, which this records into {@code result.errors} and logs before returning {@code
   * false}. Shared by every {@code reset_live_state} collaborator so none of them hand-roll their
   * own classify-log-or-ignore block.
   */
  static boolean handleGraphException(
      Exception ex, IToken<?> token, String context, ResetLiveStateResult result) {
    if (CreateShopCommandSupport.isStaleRequestGraphException(ex)) {
      return true;
    }
    result.errors++;
    TheSettlerXCreate.LOGGER.warn(
        "[CreateShop] {} failed token={} error={}",
        context,
        token,
        ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
    return false;
  }
}
