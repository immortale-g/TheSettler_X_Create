package com.thesettler_x_create.minecolonies.command;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.core.colony.requestsystem.management.IStandardRequestManager;
import com.thesettler_x_create.TheSettlerXCreate;
import com.thesettler_x_create.blockentity.CreateShopBlockEntity;
import com.thesettler_x_create.minecolonies.building.BuildingCreateShop;
import com.thesettler_x_create.minecolonies.tileentity.TileEntityCreateShop;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * Handles the auto test harness commands for the Create Shop building.
 *
 * <p>Extracted from {@link CreateShopMaintenanceCommands} to separate test-harness automation from
 * the diagnostic and reset commands. Depends on {@link CreateShopResetCommands} and {@link
 * CreateShopDiagnosticCommands}.
 */
final class CreateShopTestHarnessCommands {
  private CreateShopTestHarnessCommands() {}

  // -------------------------------------------------------------------------
  // Package-visible entry points (called from CreateShopMaintenanceCommands)
  // -------------------------------------------------------------------------

  static int runAutoHarnessStart(
      CommandSourceStack source, int requests, int amount, boolean forceWarehouseQueue) {
    CreateShopResetCommands.ResetLiveStateResult reset =
        CreateShopResetCommands.resetLiveState(forceWarehouseQueue);
    CreateShopDiagnosticCommands.LiveTestResult live =
        CreateShopDiagnosticCommands.runLiveTest(requests, amount);
    HarnessSnapshot snapshot = collectHarnessSnapshot();
    source.sendSuccess(
        () ->
            Component.literal(
                "[CreateShop] AutoHarness start: resetErrors="
                    + reset.errors
                    + ", created="
                    + live.created
                    + ", liveErrors="
                    + live.errors
                    + ", colonies="
                    + snapshot.colonies
                    + ", shops="
                    + snapshot.shops
                    + ", rootsActive="
                    + snapshot.rootsActive
                    + ", rootsTerminal="
                    + snapshot.rootsTerminal
                    + ", childrenActive="
                    + snapshot.childrenActive
                    + ", warehouseQueueEntries="
                    + snapshot.warehouseQueueEntries),
        true);
    if (!live.message.isEmpty()) {
      source.sendSuccess(
          () -> Component.literal("[CreateShop] AutoHarness live: " + live.message), false);
    }
    return live.created > 0 ? 1 : 0;
  }

  static int runAutoHarnessSnapshot(CommandSourceStack source) {
    HarnessSnapshot snapshot = collectHarnessSnapshot();
    source.sendSuccess(
        () ->
            Component.literal(
                "[CreateShop] AutoHarness snapshot: colonies="
                    + snapshot.colonies
                    + ", shops="
                    + snapshot.shops
                    + ", rootsActive="
                    + snapshot.rootsActive
                    + ", rootsTerminal="
                    + snapshot.rootsTerminal
                    + ", childrenActive="
                    + snapshot.childrenActive
                    + ", childrenTerminal="
                    + snapshot.childrenTerminal
                    + ", queueEntries="
                    + snapshot.warehouseQueueEntries
                    + ", assignmentEntries="
                    + snapshot.assignmentEntries
                    + ", errors="
                    + snapshot.errors),
        true);
    return snapshot.errors == 0 ? 1 : 0;
  }

  static int runAutoHarnessLostInject(CommandSourceStack source, int amount, int ageTicks) {
    HarnessShopContext context = findFirstHarnessShopContext();
    if (context == null || context.pickup == null) {
      source.sendFailure(
          Component.literal("[CreateShop] AutoHarness lost_inject: no eligible shop/pickup"));
      return 0;
    }
    ItemStack key = CreateShopDiagnosticCommands.selectLiveTestStack(context.tile);
    if (key.isEmpty()) {
      source.sendFailure(
          Component.literal("[CreateShop] AutoHarness lost_inject: no network stock item found"));
      return 0;
    }
    int injected =
        context.pickup.debugInjectInflight(
            key, Math.max(1, amount), "AUTO_HARNESS", "AUTO_TARGET", Math.max(1, ageTicks));
    source.sendSuccess(
        () ->
            Component.literal(
                "[CreateShop] AutoHarness lost_inject: shop="
                    + context.shop.getLocation().getInDimensionLocation()
                    + ", item="
                    + key.getHoverName().getString()
                    + ", injected="
                    + injected),
        true);
    return injected > 0 ? 1 : 0;
  }

  static int runAutoHarnessLostReorder(CommandSourceStack source) {
    LostTupleContext context = findOldestLostTupleContext();
    if (context == null) {
      source.sendFailure(
          Component.literal("[CreateShop] AutoHarness lost_reorder: no inflight tuple found"));
      return 0;
    }
    int restarted =
        context.shop.restartLostPackage(
            context.notice.stackKey,
            context.notice.remaining,
            context.notice.requesterName,
            context.notice.address,
            context.notice.requestedAt);
    source.sendSuccess(
        () ->
            Component.literal(
                "[CreateShop] AutoHarness lost_reorder: item="
                    + context.notice.stackKey.getHoverName().getString()
                    + ", restarted="
                    + restarted),
        true);
    return restarted > 0 ? 1 : 0;
  }

  static int runAutoHarnessLostHandoverSim(CommandSourceStack source) {
    LostTupleContext context = findOldestLostTupleContext();
    if (context == null) {
      source.sendFailure(
          Component.literal("[CreateShop] AutoHarness lost_handover_sim: no inflight tuple found"));
      return 0;
    }
    int simulated =
        context.shop.debugSimulateLostPackageHandover(
            context.notice.stackKey,
            context.notice.remaining,
            context.notice.requesterName,
            context.notice.address,
            context.notice.requestedAt);
    source.sendSuccess(
        () ->
            Component.literal(
                "[CreateShop] AutoHarness lost_handover_sim: item="
                    + context.notice.stackKey.getHoverName().getString()
                    + ", simulated="
                    + simulated),
        true);
    return simulated > 0 ? 1 : 0;
  }

  static int runAutoHarnessLostCancel(CommandSourceStack source) {
    LostTupleContext context = findOldestLostTupleContext();
    if (context == null) {
      source.sendFailure(
          Component.literal("[CreateShop] AutoHarness lost_cancel: no inflight tuple found"));
      return 0;
    }
    int before =
        context.pickup.getInflightRemaining(
            context.notice.stackKey,
            context.notice.requesterName,
            context.notice.address,
            context.notice.requestedAt);
    int cancelled =
        context.shop.cancelLostPackageRequestAndInflight(
            context.notice.stackKey,
            context.notice.remaining,
            context.notice.requesterName,
            context.notice.address,
            context.notice.requestedAt);
    int after =
        context.pickup.getInflightRemaining(
            context.notice.stackKey,
            context.notice.requesterName,
            context.notice.address,
            context.notice.requestedAt);
    source.sendSuccess(
        () ->
            Component.literal(
                "[CreateShop] AutoHarness lost_cancel: item="
                    + context.notice.stackKey.getHoverName().getString()
                    + ", cancelled="
                    + cancelled
                    + ", remainingBefore="
                    + before
                    + ", remainingAfter="
                    + after),
        true);
    return cancelled > 0 ? 1 : 0;
  }

  static int runAutoHarnessFull(
      CommandSourceStack source,
      int rounds,
      int requestsPerRound,
      int amount,
      boolean forceWarehouseQueue) {
    int safeRounds = Math.max(1, rounds);
    int createdTotal = 0;
    int errors = 0;
    for (int i = 0; i < safeRounds; i++) {
      CreateShopResetCommands.ResetLiveStateResult reset =
          CreateShopResetCommands.resetLiveState(forceWarehouseQueue);
      CreateShopDiagnosticCommands.LiveTestResult live =
          CreateShopDiagnosticCommands.runLiveTest(requestsPerRound, amount);
      createdTotal += live.created;
      errors += reset.errors + live.errors;
      TheSettlerXCreate.LOGGER.info(
          "[CreateShop] auto_harness round={} created={} resetErrors={} liveErrors={} message={}",
          i + 1,
          live.created,
          reset.errors,
          live.errors,
          live.message);
    }
    HarnessSnapshot snapshot = collectHarnessSnapshot();
    final int createdTotalFinal = createdTotal;
    final int errorsFinal = errors;
    source.sendSuccess(
        () ->
            Component.literal(
                "[CreateShop] AutoHarness full: rounds="
                    + safeRounds
                    + ", createdTotal="
                    + createdTotalFinal
                    + ", errors="
                    + errorsFinal
                    + ", rootsActive="
                    + snapshot.rootsActive
                    + ", childrenActive="
                    + snapshot.childrenActive
                    + ", queueEntries="
                    + snapshot.warehouseQueueEntries),
        true);
    source.sendSuccess(
        () ->
            Component.literal(
                "[CreateShop] Next: let couriers run, then execute /thesettlerxcreate auto_test_harness snapshot"),
        false);
    return errorsFinal == 0 ? 1 : 0;
  }

  static int runAutoHarnessFullAll(
      CommandSourceStack source,
      int rounds,
      int requestsPerRound,
      int amount,
      int lostAmount,
      int lostAgeTicks,
      boolean forceWarehouseQueue) {
    int safeRounds = Math.max(1, rounds);
    int createdTotal = 0;
    int errors = 0;
    int lostInjectOk = 0;
    int lostReorderOk = 0;
    int lostHandoverOk = 0;
    int lostCancelOk = 0;

    for (int i = 0; i < safeRounds; i++) {
      CreateShopResetCommands.ResetLiveStateResult reset =
          CreateShopResetCommands.resetLiveState(forceWarehouseQueue);
      CreateShopDiagnosticCommands.LiveTestResult live =
          CreateShopDiagnosticCommands.runLiveTest(requestsPerRound, amount);
      createdTotal += live.created;
      errors += reset.errors + live.errors;

      if (performLostInject(lostAmount, lostAgeTicks) > 0) {
        lostInjectOk++;
      } else {
        errors++;
      }
      if (performLostReorder() > 0) {
        lostReorderOk++;
      } else {
        errors++;
      }
      if (performLostInject(lostAmount, lostAgeTicks) > 0) {
        lostInjectOk++;
      } else {
        errors++;
      }
      if (performLostHandoverSim() > 0) {
        lostHandoverOk++;
      } else {
        errors++;
      }
      if (performLostInject(lostAmount, lostAgeTicks) > 0) {
        lostInjectOk++;
      } else {
        errors++;
      }
      if (performLostCancel() > 0) {
        lostCancelOk++;
      } else {
        errors++;
      }
    }

    HarnessSnapshot snapshot = collectHarnessSnapshot();
    final int createdTotalFinal = createdTotal;
    final int errorsFinal = errors;
    final int lostInjectOkFinal = lostInjectOk;
    final int lostReorderOkFinal = lostReorderOk;
    final int lostHandoverOkFinal = lostHandoverOk;
    final int lostCancelOkFinal = lostCancelOk;
    source.sendSuccess(
        () ->
            Component.literal(
                "[CreateShop] AutoHarness full_all: rounds="
                    + safeRounds
                    + ", createdTotal="
                    + createdTotalFinal
                    + ", errors="
                    + errorsFinal
                    + ", lostInjectOk="
                    + lostInjectOkFinal
                    + ", lostReorderOk="
                    + lostReorderOkFinal
                    + ", lostHandoverOk="
                    + lostHandoverOkFinal
                    + ", lostCancelOk="
                    + lostCancelOkFinal
                    + ", rootsActive="
                    + snapshot.rootsActive
                    + ", childrenActive="
                    + snapshot.childrenActive
                    + ", queueEntries="
                    + snapshot.warehouseQueueEntries),
        true);
    return errorsFinal == 0 ? 1 : 0;
  }

  // -------------------------------------------------------------------------
  // Private helpers
  // -------------------------------------------------------------------------

  private static int performLostInject(int amount, int ageTicks) {
    HarnessShopContext context = findFirstHarnessShopContext();
    if (context == null || context.pickup == null) {
      return 0;
    }
    ItemStack key = CreateShopDiagnosticCommands.selectLiveTestStack(context.tile);
    if (key.isEmpty()) {
      return 0;
    }
    return context.pickup.debugInjectInflight(
        key, Math.max(1, amount), "AUTO_HARNESS", "AUTO_TARGET", Math.max(1, ageTicks));
  }

  private static int performLostReorder() {
    LostTupleContext context = findOldestLostTupleContext();
    if (context == null) {
      return 0;
    }
    return context.shop.restartLostPackage(
        context.notice.stackKey,
        context.notice.remaining,
        context.notice.requesterName,
        context.notice.address,
        context.notice.requestedAt);
  }

  private static int performLostHandoverSim() {
    LostTupleContext context = findOldestLostTupleContext();
    if (context == null) {
      return 0;
    }
    return context.shop.debugSimulateLostPackageHandover(
        context.notice.stackKey,
        context.notice.remaining,
        context.notice.requesterName,
        context.notice.address,
        context.notice.requestedAt);
  }

  private static int performLostCancel() {
    LostTupleContext context = findOldestLostTupleContext();
    if (context == null) {
      return 0;
    }
    return context.shop.cancelLostPackageRequestAndInflight(
        context.notice.stackKey,
        context.notice.remaining,
        context.notice.requesterName,
        context.notice.address,
        context.notice.requestedAt);
  }

  private static HarnessSnapshot collectHarnessSnapshot() {
    HarnessSnapshot snapshot = new HarnessSnapshot();
    for (IColony colony : IColonyManager.getInstance().getAllColonies()) {
      snapshot.colonies++;
      if (!(colony.getRequestManager() instanceof IStandardRequestManager standard)) {
        continue;
      }
      java.util.Set<IToken<?>> assigned =
          CreateShopResetCommands.collectAssignedRequestTokens(standard);
      // Note: collectAssignedRequestTokens is package-private in CreateShopResetCommands
      snapshot.assignmentEntries += assigned.size();
      for (IToken<?> token : assigned) {
        if (token == null) {
          continue;
        }
        try {
          var request = standard.getRequestHandler().getRequestOrNull(token);
          if (request == null
              || !CreateShopResetCommands.isCreateShopOwnedRequest(standard, request)) {
            continue;
          }
          if (request.hasParent()) {
            if (CreateShopResetCommands.isTerminalState(request.getState())) {
              snapshot.childrenTerminal++;
            } else {
              snapshot.childrenActive++;
            }
          } else if (CreateShopResetCommands.isTerminalState(request.getState())) {
            snapshot.rootsTerminal++;
          } else {
            snapshot.rootsActive++;
          }
        } catch (Exception ex) {
          snapshot.errors++;
        }
      }

      java.util.Set<BuildingCreateShop> shops = CreateShopResetCommands.collectCreateShops(colony);
      snapshot.shops += shops.size();

      var buildingManager = colony.getServerBuildingManager();
      if (buildingManager == null || buildingManager.getBuildings() == null) {
        continue;
      }
      for (var entry : buildingManager.getBuildings().entrySet()) {
        var building = entry.getValue();
        if (building == null) {
          continue;
        }
        var queue =
            building.getModule(
                com.minecolonies.core.colony.buildings.modules.BuildingModules
                    .WAREHOUSE_REQUEST_QUEUE);
        if (queue == null || queue.getMutableRequestList() == null) {
          continue;
        }
        snapshot.warehouseQueueEntries += queue.getMutableRequestList().size();
      }
    }
    return snapshot;
  }

  private static HarnessShopContext findFirstHarnessShopContext() {
    for (IColony colony : IColonyManager.getInstance().getAllColonies()) {
      var buildingManager = colony.getServerBuildingManager();
      if (buildingManager == null || buildingManager.getBuildings() == null) {
        continue;
      }
      for (var entry : buildingManager.getBuildings().entrySet()) {
        var building = entry.getValue();
        if (!(building instanceof BuildingCreateShop shop)) {
          continue;
        }
        TileEntityCreateShop tile = shop.getCreateShopTileEntity();
        CreateShopBlockEntity pickup = shop.getPickupBlockEntity();
        if (tile == null || pickup == null || tile.getStockNetworkId() == null) {
          continue;
        }
        return new HarnessShopContext(colony, shop, tile, pickup);
      }
    }
    return null;
  }

  private static LostTupleContext findOldestLostTupleContext() {
    LostTupleContext best = null;
    for (IColony colony : IColonyManager.getInstance().getAllColonies()) {
      var buildingManager = colony.getServerBuildingManager();
      if (buildingManager == null || buildingManager.getBuildings() == null) {
        continue;
      }
      long now = colony.getWorld() == null ? 0L : colony.getWorld().getGameTime();
      for (var entry : buildingManager.getBuildings().entrySet()) {
        var building = entry.getValue();
        if (!(building instanceof BuildingCreateShop shop)) {
          continue;
        }
        CreateShopBlockEntity pickup = shop.getPickupBlockEntity();
        if (pickup == null) {
          continue;
        }
        CreateShopBlockEntity.InflightNotice notice = pickup.debugPeekOldestInflightNotice(now);
        if (notice == null
            || notice.stackKey == null
            || notice.stackKey.isEmpty()
            || notice.remaining <= 0) {
          continue;
        }
        LostTupleContext candidate = new LostTupleContext(shop, pickup, notice);
        if (best == null || candidate.notice.requestedAt < best.notice.requestedAt) {
          best = candidate;
        }
      }
    }
    return best;
  }

  // -------------------------------------------------------------------------
  // Result and context types
  // -------------------------------------------------------------------------

  static final class HarnessSnapshot {
    int colonies;
    int shops;
    int rootsActive;
    int rootsTerminal;
    int childrenActive;
    int childrenTerminal;
    int warehouseQueueEntries;
    int assignmentEntries;
    int errors;
  }

  private static final class HarnessShopContext {
    final BuildingCreateShop shop;
    final TileEntityCreateShop tile;
    final CreateShopBlockEntity pickup;

    HarnessShopContext(
        IColony colony,
        BuildingCreateShop shop,
        TileEntityCreateShop tile,
        CreateShopBlockEntity pickup) {
      this.shop = shop;
      this.tile = tile;
      this.pickup = pickup;
    }
  }

  private static final class LostTupleContext {
    final BuildingCreateShop shop;
    final CreateShopBlockEntity pickup;
    final CreateShopBlockEntity.InflightNotice notice;

    LostTupleContext(
        BuildingCreateShop shop,
        CreateShopBlockEntity pickup,
        CreateShopBlockEntity.InflightNotice notice) {
      this.shop = shop;
      this.pickup = pickup;
      this.notice = notice;
    }
  }
}
