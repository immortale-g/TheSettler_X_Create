package com.thesettler_x_create.minecolonies.command;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.workerbuildings.IWareHouse;
import com.minecolonies.api.colony.requestsystem.request.RequestState;
import com.minecolonies.api.colony.requestsystem.requestable.Stack;
import com.minecolonies.api.colony.requestsystem.requester.IRequester;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.core.colony.buildings.AbstractBuilding;
import com.minecolonies.core.colony.requestsystem.management.IStandardRequestManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.thesettler_x_create.TheSettlerXCreate;
import com.thesettler_x_create.blockentity.CreateShopBlockEntity;
import com.thesettler_x_create.create.CreateNetworkFacade;
import com.thesettler_x_create.minecolonies.building.BuildingCreateShop;
import com.thesettler_x_create.minecolonies.requestsystem.resolver.CreateShopRequestResolver;
import com.thesettler_x_create.minecolonies.tileentity.TileEntityCreateShop;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Server commands for Create Shop maintenance and uninstall preparation. */
public final class CreateShopMaintenanceCommands {
  private CreateShopMaintenanceCommands() {}

  public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
    var root = Commands.literal("thesettlerxcreate").requires(source -> source.hasPermission(2));

    root.then(
        Commands.literal("prepare_uninstall")
            .executes(
                context -> {
                  Result result = prepareUninstall();
                  context
                      .getSource()
                      .sendSuccess(
                          () ->
                              Component.literal(
                                  "[CreateShop] Uninstall prepare complete: colonies="
                                      + result.colonies
                                      + ", shops="
                                      + result.shops
                                      + ", providerUnregister="
                                      + result.providerUnregister
                                      + ", requestsCancelled="
                                      + result.requestsCancelled
                                      + ", errors="
                                      + result.errors),
                          true);
                  context
                      .getSource()
                      .sendSuccess(
                          () ->
                              Component.literal(
                                  "[CreateShop] Next step: stop server, backup world, remove mod jar, then restart."),
                          false);
                  return result.errors == 0 ? 1 : 0;
                }));

    root.then(
        Commands.literal("run_live_test")
            .executes(context -> runLiveTest(context.getSource(), 8, 8))
            .then(
                Commands.argument("requests", IntegerArgumentType.integer(1, 256))
                    .executes(
                        context ->
                            runLiveTest(
                                context.getSource(),
                                IntegerArgumentType.getInteger(context, "requests"),
                                8))
                    .then(
                        Commands.argument("amount", IntegerArgumentType.integer(1, 64))
                            .executes(
                                context ->
                                    runLiveTest(
                                        context.getSource(),
                                        IntegerArgumentType.getInteger(context, "requests"),
                                        IntegerArgumentType.getInteger(context, "amount"))))));

    root.then(
        Commands.literal("reset_live_state")
            .executes(
                context -> {
                  ResetLiveStateResult result = resetLiveState(false);
                  context
                      .getSource()
                      .sendSuccess(
                          () ->
                              Component.literal(
                                  "[CreateShop] Live state reset: colonies="
                                      + result.colonies
                                      + ", shops="
                                      + result.shops
                                      + ", requestsCancelled="
                                      + result.requestsCancelled
                                      + ", staleCleaned="
                                      + result.staleCleaned
                                      + ", runtimeTrackingCleared="
                                      + result.runtimeTrackingCleared
                                      + ", runtimeTrackingSkipped="
                                      + result.runtimeTrackingSkipped
                                      + ", queueEntriesCleared="
                                      + result.queueEntriesCleared
                                      + ", queueRequestsCancelled="
                                      + result.queueRequestsCancelled
                                      + ", blockedActiveDeliveries="
                                      + result.blockedActiveDeliveries
                                      + ", assignmentPruned="
                                      + result.assignmentPruned
                                      + ", deliveryAssignKicks="
                                      + result.deliveryAssignKicks
                                      + ", deliveryRequestsCancelled="
                                      + result.deliveryRequestsCancelled
                                      + ", drainRounds="
                                      + result.drainRounds
                                      + ", drainResiduals="
                                      + result.drainResiduals
                                      + ", errors="
                                      + result.errors),
                          true);
                  return result.errors == 0 ? 1 : 0;
                })
            .then(
                Commands.literal("force_warehouse_queue")
                    .executes(
                        context -> {
                          ResetLiveStateResult result = resetLiveState(true);
                          context
                              .getSource()
                              .sendSuccess(
                                  () ->
                                      Component.literal(
                                          "[CreateShop] Live state reset (force queue): colonies="
                                              + result.colonies
                                              + ", shops="
                                              + result.shops
                                              + ", requestsCancelled="
                                              + result.requestsCancelled
                                              + ", staleCleaned="
                                              + result.staleCleaned
                                              + ", runtimeTrackingCleared="
                                              + result.runtimeTrackingCleared
                                              + ", runtimeTrackingSkipped="
                                              + result.runtimeTrackingSkipped
                                              + ", queueEntriesCleared="
                                              + result.queueEntriesCleared
                                              + ", queueRequestsCancelled="
                                              + result.queueRequestsCancelled
                                              + ", blockedActiveDeliveries="
                                              + result.blockedActiveDeliveries
                                              + ", assignmentPruned="
                                              + result.assignmentPruned
                                              + ", deliveryAssignKicks="
                                              + result.deliveryAssignKicks
                                              + ", deliveryRequestsCancelled="
                                              + result.deliveryRequestsCancelled
                                              + ", drainRounds="
                                              + result.drainRounds
                                              + ", drainResiduals="
                                              + result.drainResiduals
                                              + ", errors="
                                              + result.errors),
                                  true);
                          return result.errors == 0 ? 1 : 0;
                        })));

    root.then(
        Commands.literal("auto_test_harness")
            .executes(context -> runAutoHarnessStart(context.getSource(), 1, 8, false))
            .then(
                Commands.literal("start")
                    .executes(context -> runAutoHarnessStart(context.getSource(), 1, 8, false))
                    .then(
                        Commands.argument("requests", IntegerArgumentType.integer(1, 256))
                            .executes(
                                context ->
                                    runAutoHarnessStart(
                                        context.getSource(),
                                        IntegerArgumentType.getInteger(context, "requests"),
                                        8,
                                        false))
                            .then(
                                Commands.argument("amount", IntegerArgumentType.integer(1, 64))
                                    .executes(
                                        context ->
                                            runAutoHarnessStart(
                                                context.getSource(),
                                                IntegerArgumentType.getInteger(context, "requests"),
                                                IntegerArgumentType.getInteger(context, "amount"),
                                                false)))))
            .then(
                Commands.literal("start_force_queue")
                    .executes(context -> runAutoHarnessStart(context.getSource(), 1, 8, true))
                    .then(
                        Commands.argument("requests", IntegerArgumentType.integer(1, 256))
                            .executes(
                                context ->
                                    runAutoHarnessStart(
                                        context.getSource(),
                                        IntegerArgumentType.getInteger(context, "requests"),
                                        8,
                                        true))
                            .then(
                                Commands.argument("amount", IntegerArgumentType.integer(1, 64))
                                    .executes(
                                        context ->
                                            runAutoHarnessStart(
                                                context.getSource(),
                                                IntegerArgumentType.getInteger(context, "requests"),
                                                IntegerArgumentType.getInteger(context, "amount"),
                                                true)))))
            .then(
                Commands.literal("snapshot")
                    .executes(context -> runAutoHarnessSnapshot(context.getSource())))
            .then(
                Commands.literal("lost_inject")
                    .executes(context -> runAutoHarnessLostInject(context.getSource(), 8, 20 * 60))
                    .then(
                        Commands.argument("amount", IntegerArgumentType.integer(1, 64))
                            .executes(
                                context ->
                                    runAutoHarnessLostInject(
                                        context.getSource(),
                                        IntegerArgumentType.getInteger(context, "amount"),
                                        20 * 60))
                            .then(
                                Commands.argument(
                                        "age_ticks", IntegerArgumentType.integer(1, 20 * 3600))
                                    .executes(
                                        context ->
                                            runAutoHarnessLostInject(
                                                context.getSource(),
                                                IntegerArgumentType.getInteger(context, "amount"),
                                                IntegerArgumentType.getInteger(
                                                    context, "age_ticks"))))))
            .then(
                Commands.literal("lost_reorder")
                    .executes(context -> runAutoHarnessLostReorder(context.getSource())))
            .then(
                Commands.literal("lost_handover_sim")
                    .executes(context -> runAutoHarnessLostHandoverSim(context.getSource())))
            .then(
                Commands.literal("lost_cancel")
                    .executes(context -> runAutoHarnessLostCancel(context.getSource())))
            .then(
                Commands.literal("full")
                    .executes(context -> runAutoHarnessFull(context.getSource(), 3, 1, 8, true))
                    .then(
                        Commands.argument("rounds", IntegerArgumentType.integer(1, 8))
                            .executes(
                                context ->
                                    runAutoHarnessFull(
                                        context.getSource(),
                                        IntegerArgumentType.getInteger(context, "rounds"),
                                        1,
                                        8,
                                        true))
                            .then(
                                Commands.argument("requests", IntegerArgumentType.integer(1, 256))
                                    .executes(
                                        context ->
                                            runAutoHarnessFull(
                                                context.getSource(),
                                                IntegerArgumentType.getInteger(context, "rounds"),
                                                IntegerArgumentType.getInteger(context, "requests"),
                                                8,
                                                true))
                                    .then(
                                        Commands.argument(
                                                "amount", IntegerArgumentType.integer(1, 64))
                                            .executes(
                                                context ->
                                                    runAutoHarnessFull(
                                                        context.getSource(),
                                                        IntegerArgumentType.getInteger(
                                                            context, "rounds"),
                                                        IntegerArgumentType.getInteger(
                                                            context, "requests"),
                                                        IntegerArgumentType.getInteger(
                                                            context, "amount"),
                                                        true)))))));

    root.then(
        Commands.literal("auto_test_harness_full_all")
            .executes(
                context -> runAutoHarnessFullAll(context.getSource(), 2, 1, 8, 8, 20 * 60, true)));

    dispatcher.register(root);
  }

  private static int runLiveTest(CommandSourceStack source, int requests, int amount) {
    LiveTestResult result = runLiveTest(requests, amount);
    source.sendSuccess(
        () ->
            Component.literal(
                "[CreateShop] Live test: requested="
                    + requests
                    + ", amount="
                    + amount
                    + ", created="
                    + result.created
                    + ", errors="
                    + result.errors
                    + ", colonies="
                    + result.colonies
                    + ", shopsSeen="
                    + result.shopsSeen),
        true);
    if (!result.message.isEmpty()) {
      source.sendSuccess(() -> Component.literal("[CreateShop] " + result.message), false);
    }
    return result.created > 0 ? 1 : 0;
  }

  private static LiveTestResult runLiveTest(int requests, int amount) {
    LiveTestResult result = new LiveTestResult();
    int requestCount = Math.max(1, requests);
    int stackAmount = Math.max(1, amount);

    for (IColony colony : IColonyManager.getInstance().getAllColonies()) {
      result.colonies++;
      if (!(colony.getRequestManager() instanceof IStandardRequestManager standard)) {
        continue;
      }
      var buildingManager = colony.getServerBuildingManager();
      if (buildingManager == null || buildingManager.getBuildings() == null) {
        continue;
      }

      for (var entry : buildingManager.getBuildings().entrySet()) {
        var building = entry.getValue();
        if (!(building instanceof BuildingCreateShop shop)) {
          continue;
        }
        result.shopsSeen++;
        TileEntityCreateShop tile = shop.getCreateShopTileEntity();
        if (tile == null || tile.getStockNetworkId() == null) {
          continue;
        }

        ItemStack available = selectLiveTestStack(tile);
        if (available.isEmpty()) {
          continue;
        }

        IRequester target = findLiveTestTargetRequester(colony, shop);
        if (target == null) {
          result.message =
              "No valid target requester found for shop at "
                  + shop.getLocation().getInDimensionLocation();
          continue;
        }

        int singleRequestAmount = Math.max(1, Math.min(stackAmount, available.getMaxStackSize()));
        int localCreated = 0;
        for (int i = 0; i < requestCount; i++) {
          try {
            ItemStack requestStack = available.copy();
            requestStack.setCount(singleRequestAmount);
            IToken<?> token = standard.createRequest(target, new Stack(requestStack));
            standard.assignRequest(token);
            localCreated++;
            result.created++;
            TheSettlerXCreate.LOGGER.info(
                "[CreateShop] run_live_test created request token={} shop={} target={} item={} count={}",
                token,
                shop.getLocation().getInDimensionLocation(),
                target.getLocation().getInDimensionLocation(),
                requestStack.getItem(),
                requestStack.getCount());
          } catch (Exception ex) {
            result.errors++;
            TheSettlerXCreate.LOGGER.warn(
                "[CreateShop] run_live_test createRequest failed shop={} error={}",
                shop.getLocation().getInDimensionLocation(),
                ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
          }
        }

        result.message =
            "Shop="
                + shop.getLocation().getInDimensionLocation()
                + " target="
                + target.getLocation().getInDimensionLocation()
                + " item="
                + available.getHoverName().getString()
                + " perRequest="
                + singleRequestAmount
                + " created="
                + localCreated;

        if (localCreated > 0) {
          return result;
        }
      }
    }

    if (result.message.isEmpty()) {
      result.message =
          "No eligible Create Shop with network stock and valid target requester found.";
    }
    return result;
  }

  private static int runAutoHarnessStart(
      CommandSourceStack source, int requests, int amount, boolean forceWarehouseQueue) {
    ResetLiveStateResult reset = resetLiveState(forceWarehouseQueue);
    LiveTestResult live = runLiveTest(requests, amount);
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

  private static int runAutoHarnessSnapshot(CommandSourceStack source) {
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

  private static int runAutoHarnessLostInject(CommandSourceStack source, int amount, int ageTicks) {
    HarnessShopContext context = findFirstHarnessShopContext();
    if (context == null || context.pickup == null) {
      source.sendFailure(
          Component.literal("[CreateShop] AutoHarness lost_inject: no eligible shop/pickup"));
      return 0;
    }
    ItemStack key = selectLiveTestStack(context.tile);
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
                    + ", amount="
                    + amount
                    + ", ageTicks="
                    + ageTicks
                    + ", injected="
                    + injected),
        true);
    return injected > 0 ? 1 : 0;
  }

  private static int runAutoHarnessLostReorder(CommandSourceStack source) {
    LostTupleContext context = findOldestLostTupleContext();
    if (context == null) {
      source.sendFailure(
          Component.literal("[CreateShop] AutoHarness lost_reorder: no inflight tuple found"));
      return 0;
    }
    int before =
        context.pickup.getInflightRemaining(
            context.notice.stackKey,
            context.notice.requesterName,
            context.notice.address,
            context.notice.requestedAt);
    int consumed =
        context.shop.restartLostPackage(
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
                "[CreateShop] AutoHarness lost_reorder: item="
                    + context.notice.stackKey.getHoverName().getString()
                    + ", requested="
                    + context.notice.remaining
                    + ", consumed="
                    + consumed
                    + ", remainingBefore="
                    + before
                    + ", remainingAfter="
                    + after),
        true);
    return consumed > 0 ? 1 : 0;
  }

  private static int runAutoHarnessLostHandoverSim(CommandSourceStack source) {
    LostTupleContext context = findOldestLostTupleContext();
    if (context == null) {
      source.sendFailure(
          Component.literal("[CreateShop] AutoHarness lost_handover_sim: no inflight tuple found"));
      return 0;
    }
    int before =
        context.pickup.getInflightRemaining(
            context.notice.stackKey,
            context.notice.requesterName,
            context.notice.address,
            context.notice.requestedAt);
    int consumed =
        context.shop.debugSimulateLostPackageHandover(
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
                "[CreateShop] AutoHarness lost_handover_sim: item="
                    + context.notice.stackKey.getHoverName().getString()
                    + ", target="
                    + context.notice.remaining
                    + ", consumed="
                    + consumed
                    + ", remainingBefore="
                    + before
                    + ", remainingAfter="
                    + after),
        true);
    return consumed > 0 ? 1 : 0;
  }

  private static int runAutoHarnessLostCancel(CommandSourceStack source) {
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

  private static int runAutoHarnessFull(
      CommandSourceStack source,
      int rounds,
      int requestsPerRound,
      int amount,
      boolean forceWarehouseQueue) {
    int safeRounds = Math.max(1, rounds);
    int createdTotal = 0;
    int errors = 0;
    for (int i = 0; i < safeRounds; i++) {
      ResetLiveStateResult reset = resetLiveState(forceWarehouseQueue);
      LiveTestResult live = runLiveTest(requestsPerRound, amount);
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

  private static int runAutoHarnessFullAll(
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
      ResetLiveStateResult reset = resetLiveState(forceWarehouseQueue);
      LiveTestResult live = runLiveTest(requestsPerRound, amount);
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

  private static int performLostInject(int amount, int ageTicks) {
    HarnessShopContext context = findFirstHarnessShopContext();
    if (context == null || context.pickup == null) {
      return 0;
    }
    ItemStack key = selectLiveTestStack(context.tile);
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
      java.util.Set<IToken<?>> assigned = collectAssignedRequestTokens(standard);
      snapshot.assignmentEntries += assigned.size();
      for (IToken<?> token : assigned) {
        if (token == null) {
          continue;
        }
        try {
          var request = standard.getRequestHandler().getRequestOrNull(token);
          if (request == null || !isCreateShopOwnedRequest(standard, request)) {
            continue;
          }
          if (request.hasParent()) {
            if (isTerminalState(request.getState())) {
              snapshot.childrenTerminal++;
            } else {
              snapshot.childrenActive++;
            }
          } else if (isTerminalState(request.getState())) {
            snapshot.rootsTerminal++;
          } else {
            snapshot.rootsActive++;
          }
        } catch (Exception ex) {
          snapshot.errors++;
        }
      }

      java.util.Set<BuildingCreateShop> shops = collectCreateShops(colony);
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

  private static ItemStack selectLiveTestStack(TileEntityCreateShop tile) {
    if (tile == null || tile.getStockNetworkId() == null) {
      return ItemStack.EMPTY;
    }
    java.util.List<ItemStack> available = new CreateNetworkFacade(tile).getAvailableStacks();
    if (available == null || available.isEmpty()) {
      return ItemStack.EMPTY;
    }

    ItemStack best = ItemStack.EMPTY;
    int bestCount = 0;
    for (ItemStack stack : available) {
      if (stack == null || stack.isEmpty() || stack.getCount() <= 0) {
        continue;
      }
      if (stack.getCount() > bestCount) {
        best = stack.copy();
        bestCount = stack.getCount();
      }
    }
    return best;
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

  private static IRequester findLiveTestTargetRequester(IColony colony, BuildingCreateShop shop) {
    if (colony == null || shop == null) {
      return null;
    }
    var buildingManager = colony.getServerBuildingManager();
    if (buildingManager == null || buildingManager.getBuildings() == null) {
      return null;
    }

    BlockPos shopPos = shop.getLocation().getInDimensionLocation();
    IRequester best = null;
    int bestTier = Integer.MAX_VALUE;
    double bestDistance = Double.MAX_VALUE;

    for (var entry : buildingManager.getBuildings().entrySet()) {
      var candidate = entry.getValue();
      if (!(candidate instanceof AbstractBuilding building)
          || candidate instanceof BuildingCreateShop) {
        continue;
      }
      IRequester requester = building.getRequester();
      if (requester == null || requester.getLocation() == null) {
        continue;
      }
      if (!requester.getLocation().getDimension().equals(shop.getLocation().getDimension())) {
        continue;
      }
      BlockPos targetPos = requester.getLocation().getInDimensionLocation();
      if (targetPos == null || targetPos.equals(shopPos)) {
        continue;
      }
      int tier = targetPriorityTier(candidate);
      double distance = targetPos.distSqr(shopPos);
      if (tier < bestTier || (tier == bestTier && distance < bestDistance)) {
        best = requester;
        bestTier = tier;
        bestDistance = distance;
      }
    }

    return best;
  }

  private static int targetPriorityTier(Object building) {
    if (building instanceof IWareHouse && !(building instanceof BuildingCreateShop)) {
      return 0; // Warehouse first.
    }
    if (building != null && "PostBox".equals(building.getClass().getSimpleName())) {
      return 1; // Then PostBox.
    }
    return 2; // Finally any other requester building.
  }

  private static Result prepareUninstall() {
    Result result = new Result();
    for (var colony : IColonyManager.getInstance().getAllColonies()) {
      result.colonies++;
      if (!(colony.getRequestManager() instanceof IStandardRequestManager standard)) {
        continue;
      }
      var buildingManager = colony.getServerBuildingManager();
      if (buildingManager != null && buildingManager.getBuildings() != null) {
        for (var entry : buildingManager.getBuildings().entrySet()) {
          var building = entry.getValue();
          if (!(building instanceof BuildingCreateShop shop)) {
            continue;
          }
          result.shops++;
          try {
            colony.getRequestManager().onProviderRemovedFromColony(shop);
            result.providerUnregister++;
          } catch (Exception ex) {
            result.errors++;
            TheSettlerXCreate.LOGGER.warn(
                "[CreateShop] prepare_uninstall provider unregister failed shop={} error={}",
                shop.getLocation() == null
                    ? "<unknown>"
                    : shop.getLocation().getInDimensionLocation(),
                ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
          }
        }
      }

      var assignments = standard.getRequestResolverRequestAssignmentDataStore().getAssignments();
      if (assignments == null || assignments.isEmpty()) {
        continue;
      }
      java.util.Set<com.minecolonies.api.colony.requestsystem.token.IToken<?>> requestTokens =
          new java.util.LinkedHashSet<>();
      for (var tokens : assignments.values()) {
        if (tokens != null) {
          requestTokens.addAll(tokens);
        }
      }
      for (var requestToken : requestTokens) {
        try {
          var request = standard.getRequestHandler().getRequest(requestToken);
          if (request == null) {
            continue;
          }
          var owner = standard.getResolverHandler().getResolverForRequest(request);
          if (!(owner instanceof CreateShopRequestResolver)) {
            continue;
          }
          standard.updateRequestState(request.getId(), RequestState.CANCELLED);
          result.requestsCancelled++;
        } catch (Exception ex) {
          result.errors++;
          TheSettlerXCreate.LOGGER.warn(
              "[CreateShop] prepare_uninstall cancel failed token={} error={}",
              requestToken,
              ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
        }
      }
    }
    return result;
  }

  private static ResetLiveStateResult resetLiveState(boolean forceWarehouseQueueClear) {
    ResetLiveStateResult result = new ResetLiveStateResult();
    for (IColony colony : IColonyManager.getInstance().getAllColonies()) {
      result.colonies++;
      if (!(colony.getRequestManager() instanceof IStandardRequestManager standard)) {
        continue;
      }

      java.util.Set<BuildingCreateShop> shops = collectCreateShops(colony);
      result.shops += shops.size();
      int initialActiveLocalDeliveries = countShopsWithActiveLocalDeliveries(colony, shops);
      int drainRounds = Math.max(1, 3 + (initialActiveLocalDeliveries > 0 ? 1 : 0));
      result.drainRounds += drainRounds;
      for (int round = 0; round < drainRounds; round++) {
        cancelCreateShopOwnedRequestsGraphAware(standard, result);
        cancelAllAssignedRequestsGraphAware(standard, result);
        cancelActiveLocalDeliveries(colony, standard, result);
        reconcileAssignmentsAndKickCouriers(standard, result);
      }

      // Always prune stale/terminal queue entries; this is conservative and prevents stale
      // warehouse queue tokens from keeping courier jobs in a stuck loop after request cleanup.
      clearWarehouseQueues(colony, standard, result);
      if (forceWarehouseQueueClear) {
        // Force mode gets one extra prune pass after reconciliation below.
      }

      int remainingActiveLocalDeliveries = countShopsWithActiveLocalDeliveries(colony, shops);
      if (forceWarehouseQueueClear) {
        clearWarehouseQueues(colony, standard, result);
      }
      if (remainingActiveLocalDeliveries > 0 || hasActiveCreateShopRootRequests(standard)) {
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

  private static void cancelActiveLocalDeliveries(
      IColony colony, IStandardRequestManager standard, ResetLiveStateResult result) {
    if (colony == null || standard == null || result == null) {
      return;
    }
    var store = standard.getRequestResolverRequestAssignmentDataStore();
    if (store == null || store.getAssignments() == null || store.getAssignments().isEmpty()) {
      return;
    }
    var requestHandler = standard.getRequestHandler();
    if (requestHandler == null) {
      return;
    }
    java.util.Set<BuildingCreateShop> shops = collectCreateShops(colony);
    if (shops.isEmpty()) {
      return;
    }

    for (var assignmentEntry : store.getAssignments().entrySet()) {
      var assigned = assignmentEntry.getValue();
      if (assigned == null || assigned.isEmpty()) {
        continue;
      }
      for (IToken<?> token : java.util.List.copyOf(assigned)) {
        if (token == null) {
          continue;
        }
        try {
          var request = requestHandler.getRequestOrNull(token);
          if (request == null
              || !(request.getRequest()
                  instanceof
                  com.minecolonies.api.colony.requestsystem.requestable.deliveryman.Delivery)) {
            continue;
          }
          if (isTerminalState(request.getState()) || !isCreateShopOwnedRequest(standard, request)) {
            continue;
          }
          boolean localDeliveryForAnyShop = false;
          for (BuildingCreateShop shop : shops) {
            if (shop != null && shop.hasActiveLocalDeliveryChildrenForInflight(colony)) {
              localDeliveryForAnyShop = true;
              break;
            }
          }
          if (!localDeliveryForAnyShop) {
            continue;
          }
          standard.updateRequestState(token, RequestState.CANCELLED);
          result.deliveryRequestsCancelled++;
        } catch (Exception ex) {
          if (isStaleRequestGraphException(ex)) {
            continue;
          }
          result.errors++;
          TheSettlerXCreate.LOGGER.warn(
              "[CreateShop] reset_live_state cancel active delivery failed token={} error={}",
              token,
              ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
        }
      }
    }
  }

  private static int countShopsWithActiveLocalDeliveries(
      IColony colony, java.util.Set<BuildingCreateShop> shops) {
    if (colony == null || shops == null || shops.isEmpty()) {
      return 0;
    }
    int active = 0;
    for (BuildingCreateShop shop : shops) {
      if (shop == null) {
        continue;
      }
      try {
        if (shop.hasActiveLocalDeliveryChildrenForInflight(colony)) {
          active++;
        }
      } catch (Exception ex) {
        // Fail closed: if we cannot verify cleanly, do not perform a destructive cleanup pass.
        active++;
        TheSettlerXCreate.LOGGER.warn(
            "[CreateShop] reset_live_state preflight failed shop={} error={}",
            shop.getLocation() == null ? "<unknown>" : shop.getLocation().getInDimensionLocation(),
            ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
      }
    }
    return active;
  }

  private static void reconcileAssignmentsAndKickCouriers(
      IStandardRequestManager standard, ResetLiveStateResult result) {
    if (standard == null || result == null) {
      return;
    }
    var store = standard.getRequestResolverRequestAssignmentDataStore();
    if (store == null || store.getAssignments() == null || store.getAssignments().isEmpty()) {
      return;
    }
    var requestHandler = standard.getRequestHandler();
    if (requestHandler == null) {
      return;
    }

    for (var assignmentEntry : store.getAssignments().entrySet()) {
      java.util.Collection<IToken<?>> assigned = assignmentEntry.getValue();
      if (assigned == null || assigned.isEmpty()) {
        continue;
      }
      java.util.Iterator<IToken<?>> iterator = assigned.iterator();
      while (iterator.hasNext()) {
        IToken<?> token = iterator.next();
        if (token == null) {
          iterator.remove();
          result.assignmentPruned++;
          continue;
        }
        try {
          var request = requestHandler.getRequestOrNull(token);
          if (request == null) {
            iterator.remove();
            result.assignmentPruned++;
            continue;
          }
          if (isTerminalState(request.getState())) {
            iterator.remove();
            result.assignmentPruned++;
            continue;
          }
          if (!isCreateShopOwnedRequest(standard, request)) {
            continue;
          }
          if (request.getRequest()
                  instanceof
                  com.minecolonies.api.colony.requestsystem.requestable.deliveryman.Delivery
              && request.getState() == RequestState.CREATED) {
            try {
              standard.assignRequest(token);
              result.deliveryAssignKicks++;
            } catch (Exception kickEx) {
              result.errors++;
              TheSettlerXCreate.LOGGER.warn(
                  "[CreateShop] reset_live_state assign kick failed token={} error={}",
                  token,
                  kickEx.getMessage() == null
                      ? kickEx.getClass().getSimpleName()
                      : kickEx.getMessage());
            }
          }
        } catch (Exception ex) {
          if (isStaleRequestGraphException(ex)) {
            iterator.remove();
            result.assignmentPruned++;
            continue;
          }
          result.errors++;
          TheSettlerXCreate.LOGGER.warn(
              "[CreateShop] reset_live_state reconcile assignment failed token={} error={}",
              token,
              ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
        }
      }
    }
  }

  private static void cancelCreateShopOwnedRequestsGraphAware(
      IStandardRequestManager standard, ResetLiveStateResult result) {
    if (standard == null || result == null) {
      return;
    }
    java.util.Set<IToken<?>> assignedTokens = collectAssignedRequestTokens(standard);
    if (assignedTokens.isEmpty()) {
      return;
    }

    java.util.Set<IToken<?>> visited = new java.util.LinkedHashSet<>();
    java.util.List<IToken<?>> roots = new java.util.ArrayList<>();
    for (IToken<?> token : assignedTokens) {
      if (token == null) {
        continue;
      }
      try {
        var request = standard.getRequestHandler().getRequestOrNull(token);
        if (request == null || !isCreateShopOwnedRootRequest(standard, request)) {
          continue;
        }
        roots.add(token);
      } catch (Exception ex) {
        if (isStaleRequestGraphException(ex)) {
          cleanupStaleToken(standard, token, result, "reset_live_state root scan");
        } else {
          result.errors++;
          TheSettlerXCreate.LOGGER.warn(
              "[CreateShop] reset_live_state root scan failed token={} error={}",
              token,
              ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
        }
      }
    }

    for (IToken<?> root : roots) {
      cancelRequestGraphPostOrder(standard, root, visited, result);
    }

    // Cancel orphaned assigned Create Shop requests that are not reachable from a root graph.
    for (IToken<?> token : assignedTokens) {
      if (token == null || visited.contains(token)) {
        continue;
      }
      try {
        var request = standard.getRequestHandler().getRequestOrNull(token);
        if (request == null || !isCreateShopOwnedRequest(standard, request)) {
          continue;
        }
        cancelSingleRequest(standard, request, result);
      } catch (Exception ex) {
        if (isStaleRequestGraphException(ex)) {
          cleanupStaleToken(standard, token, result, "reset_live_state orphan scan");
        } else {
          result.errors++;
          TheSettlerXCreate.LOGGER.warn(
              "[CreateShop] reset_live_state orphan scan failed token={} error={}",
              token,
              ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
        }
      }
    }
  }

  /**
   * Hard-reset pass used by live test cleanup: cancel every non-terminal assigned request graph,
   * regardless of current owner resolver. This prevents stuck retrying roots from surviving world
   * reloads and triggering duplicate orders in subsequent tests.
   */
  private static void cancelAllAssignedRequestsGraphAware(
      IStandardRequestManager standard, ResetLiveStateResult result) {
    if (standard == null || result == null) {
      return;
    }
    java.util.Set<IToken<?>> assignedTokens = collectAssignedRequestTokens(standard);
    if (assignedTokens.isEmpty()) {
      return;
    }

    java.util.Set<IToken<?>> visited = new java.util.LinkedHashSet<>();
    java.util.List<IToken<?>> roots = new java.util.ArrayList<>();
    for (IToken<?> token : assignedTokens) {
      if (token == null) {
        continue;
      }
      try {
        var request = standard.getRequestHandler().getRequestOrNull(token);
        if (request == null || request.hasParent()) {
          continue;
        }
        roots.add(token);
      } catch (Exception ex) {
        if (isStaleRequestGraphException(ex)) {
          cleanupStaleToken(standard, token, result, "reset_live_state all root scan");
        } else {
          result.errors++;
          TheSettlerXCreate.LOGGER.warn(
              "[CreateShop] reset_live_state all root scan failed token={} error={}",
              token,
              ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
        }
      }
    }

    for (IToken<?> root : roots) {
      cancelRequestGraphPostOrder(standard, root, visited, result);
    }

    for (IToken<?> token : assignedTokens) {
      if (token == null || visited.contains(token)) {
        continue;
      }
      try {
        var request = standard.getRequestHandler().getRequestOrNull(token);
        if (request == null) {
          cleanupStaleToken(standard, token, result, "reset_live_state all orphan missing");
          continue;
        }
        cancelSingleRequest(standard, request, result);
      } catch (Exception ex) {
        if (isStaleRequestGraphException(ex)) {
          cleanupStaleToken(standard, token, result, "reset_live_state all orphan scan");
        } else {
          result.errors++;
          TheSettlerXCreate.LOGGER.warn(
              "[CreateShop] reset_live_state all orphan scan failed token={} error={}",
              token,
              ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
        }
      }
    }
  }

  private static void cancelRequestGraphPostOrder(
      IStandardRequestManager standard,
      IToken<?> token,
      java.util.Set<IToken<?>> visited,
      ResetLiveStateResult result) {
    if (standard == null || token == null || visited == null || result == null) {
      return;
    }
    if (!visited.add(token)) {
      return;
    }

    com.minecolonies.api.colony.requestsystem.request.IRequest<?> request;
    try {
      request = standard.getRequestHandler().getRequestOrNull(token);
    } catch (Exception ex) {
      if (isStaleRequestGraphException(ex)) {
        cleanupStaleToken(standard, token, result, "reset_live_state graph fetch");
      } else {
        result.errors++;
        TheSettlerXCreate.LOGGER.warn(
            "[CreateShop] reset_live_state graph fetch failed token={} error={}",
            token,
            ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
      }
      return;
    }

    if (request == null) {
      cleanupStaleToken(standard, token, result, "reset_live_state graph missing");
      return;
    }

    if (request.hasChildren()
        && request.getChildren() != null
        && !request.getChildren().isEmpty()) {
      for (IToken<?> child : java.util.List.copyOf(request.getChildren())) {
        cancelRequestGraphPostOrder(standard, child, visited, result);
      }
    }

    cancelSingleRequest(standard, request, result);
  }

  private static void cancelSingleRequest(
      IStandardRequestManager standard,
      com.minecolonies.api.colony.requestsystem.request.IRequest<?> request,
      ResetLiveStateResult result) {
    if (standard == null || request == null || result == null) {
      return;
    }
    try {
      if (isTerminalState(request.getState())) {
        if (request.getState() == RequestState.CANCELLED) {
          standard.getRequestHandler().cleanRequestData(request.getId());
          result.staleCleaned++;
        }
        return;
      }
      standard.updateRequestState(request.getId(), RequestState.CANCELLED);
      result.requestsCancelled++;
    } catch (Exception ex) {
      if (isStaleRequestGraphException(ex)) {
        cleanupStaleToken(standard, request.getId(), result, "reset_live_state graph cancel");
        return;
      }
      result.errors++;
      TheSettlerXCreate.LOGGER.warn(
          "[CreateShop] reset_live_state cancel failed token={} error={}",
          request.getId(),
          ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
    }
  }

  private static void cleanupStaleToken(
      IStandardRequestManager standard,
      IToken<?> token,
      ResetLiveStateResult result,
      String reason) {
    if (standard == null || token == null || result == null) {
      return;
    }
    try {
      standard.getRequestHandler().cleanRequestData(token);
      result.staleCleaned++;
      TheSettlerXCreate.LOGGER.info("[CreateShop] {} stale cleanup token={}", reason, token);
    } catch (Exception cleanEx) {
      result.errors++;
      TheSettlerXCreate.LOGGER.warn(
          "[CreateShop] {} stale cleanup failed token={} error={}",
          reason,
          token,
          cleanEx.getMessage() == null ? cleanEx.getClass().getSimpleName() : cleanEx.getMessage());
    }
  }

  private static void clearWarehouseQueues(
      IColony colony, IStandardRequestManager standard, ResetLiveStateResult result) {
    if (colony == null || standard == null || result == null) {
      return;
    }
    var buildingManager = colony.getServerBuildingManager();
    if (buildingManager == null || buildingManager.getBuildings() == null) {
      return;
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
      if (queue == null
          || queue.getMutableRequestList() == null
          || queue.getMutableRequestList().isEmpty()) {
        continue;
      }
      java.util.Iterator<IToken<?>> iterator = queue.getMutableRequestList().iterator();
      while (iterator.hasNext()) {
        IToken<?> queuedToken = iterator.next();
        if (queuedToken == null) {
          iterator.remove();
          result.queueEntriesCleared++;
          continue;
        }
        try {
          var queuedRequest = standard.getRequestHandler().getRequestOrNull(queuedToken);
          if (queuedRequest == null) {
            iterator.remove();
            result.queueEntriesCleared++;
            result.staleCleaned++;
            continue;
          }
          if (!isTerminalState(queuedRequest.getState())) {
            // Reset should leave no active Create Shop queue residue behind.
            if (!isCreateShopOwnedRequest(standard, queuedRequest)) {
              continue;
            }
            try {
              standard.updateRequestState(queuedToken, RequestState.CANCELLED);
              result.queueRequestsCancelled++;
            } catch (Exception cancelEx) {
              if (!isStaleRequestGraphException(cancelEx)) {
                result.errors++;
                TheSettlerXCreate.LOGGER.warn(
                    "[CreateShop] reset_live_state queue active cancel failed token={} error={}",
                    queuedToken,
                    cancelEx.getMessage() == null
                        ? cancelEx.getClass().getSimpleName()
                        : cancelEx.getMessage());
              }
            }
            iterator.remove();
            result.queueEntriesCleared++;
            try {
              standard.getRequestHandler().cleanRequestData(queuedToken);
              result.staleCleaned++;
            } catch (Exception cleanEx) {
              if (!isStaleRequestGraphException(cleanEx)) {
                result.errors++;
                TheSettlerXCreate.LOGGER.warn(
                    "[CreateShop] reset_live_state queue active cleanup failed token={} error={}",
                    queuedToken,
                    cleanEx.getMessage() == null
                        ? cleanEx.getClass().getSimpleName()
                        : cleanEx.getMessage());
              }
            }
            continue;
          }
          iterator.remove();
          result.queueEntriesCleared++;
          if (queuedRequest.getState() == RequestState.CANCELLED) {
            try {
              standard.getRequestHandler().cleanRequestData(queuedToken);
              result.staleCleaned++;
            } catch (Exception cleanEx) {
              result.errors++;
              TheSettlerXCreate.LOGGER.warn(
                  "[CreateShop] reset_live_state queue stale cleanup failed token={} error={}",
                  queuedToken,
                  cleanEx.getMessage() == null
                      ? cleanEx.getClass().getSimpleName()
                      : cleanEx.getMessage());
            }
          }
        } catch (Exception ex) {
          if (isStaleRequestGraphException(ex)) {
            iterator.remove();
            result.queueEntriesCleared++;
            try {
              standard.getRequestHandler().cleanRequestData(queuedToken);
              result.staleCleaned++;
            } catch (Exception cleanEx) {
              result.errors++;
              TheSettlerXCreate.LOGGER.warn(
                  "[CreateShop] reset_live_state queue stale cleanup failed token={} error={}",
                  queuedToken,
                  cleanEx.getMessage() == null
                      ? cleanEx.getClass().getSimpleName()
                      : cleanEx.getMessage());
            }
            continue;
          }
          result.errors++;
          TheSettlerXCreate.LOGGER.warn(
              "[CreateShop] reset_live_state queue cancel failed token={} error={}",
              queuedToken,
              ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
        }
      }
    }
  }

  private static java.util.Set<BuildingCreateShop> collectCreateShops(IColony colony) {
    java.util.Set<BuildingCreateShop> shops = new java.util.LinkedHashSet<>();
    if (colony == null) {
      return shops;
    }
    var buildingManager = colony.getServerBuildingManager();
    if (buildingManager == null || buildingManager.getBuildings() == null) {
      return shops;
    }
    for (var entry : buildingManager.getBuildings().entrySet()) {
      var building = entry.getValue();
      if (building instanceof BuildingCreateShop shop) {
        shops.add(shop);
      }
    }
    return shops;
  }

  private static java.util.Set<IToken<?>> collectAssignedRequestTokens(
      IStandardRequestManager standard) {
    java.util.Set<IToken<?>> tokens = new java.util.LinkedHashSet<>();
    if (standard == null) {
      return tokens;
    }
    var assignments = standard.getRequestResolverRequestAssignmentDataStore().getAssignments();
    if (assignments == null || assignments.isEmpty()) {
      return tokens;
    }
    for (var assigned : assignments.values()) {
      if (assigned != null) {
        tokens.addAll(assigned);
      }
    }
    return tokens;
  }

  private static boolean isCreateShopOwnedRequest(
      IStandardRequestManager standard,
      com.minecolonies.api.colony.requestsystem.request.IRequest<?> request) {
    if (standard == null || request == null) {
      return false;
    }
    try {
      var owner = standard.getResolverHandler().getResolverForRequest(request);
      if (owner instanceof CreateShopRequestResolver) {
        return true;
      }
      if (!request.hasParent()) {
        return false;
      }
      var parent = standard.getRequestHandler().getRequest(request.getParent());
      if (parent == null) {
        return false;
      }
      var parentOwner = standard.getResolverHandler().getResolverForRequest(parent);
      return parentOwner instanceof CreateShopRequestResolver;
    } catch (Exception ignored) {
      return false;
    }
  }

  private static boolean isCreateShopOwnedRootRequest(
      IStandardRequestManager standard,
      com.minecolonies.api.colony.requestsystem.request.IRequest<?> request) {
    if (!isCreateShopOwnedRequest(standard, request)) {
      return false;
    }
    return request != null && !request.hasParent();
  }

  private static boolean hasActiveCreateShopRootRequests(IStandardRequestManager standard) {
    if (standard == null) {
      return false;
    }
    java.util.Set<IToken<?>> tokens = collectAssignedRequestTokens(standard);
    for (IToken<?> token : tokens) {
      if (token == null) {
        continue;
      }
      try {
        var request = standard.getRequestHandler().getRequestOrNull(token);
        if (request == null || !isCreateShopOwnedRootRequest(standard, request)) {
          continue;
        }
        if (!isTerminalState(request.getState())) {
          return true;
        }
      } catch (Exception ignored) {
        // Fail open: keep runtime tracking when assignment graph is unstable.
        return true;
      }
    }
    return false;
  }

  private static boolean isTerminalState(RequestState state) {
    return state == RequestState.CANCELLED
        || state == RequestState.COMPLETED
        || state == RequestState.FAILED
        || state == RequestState.RECEIVED
        || state == RequestState.RESOLVED;
  }

  private static boolean isStaleRequestGraphException(Exception ex) {
    if (ex == null) {
      return false;
    }
    String message = ex.getMessage();
    if (message == null || message.isEmpty()) {
      return false;
    }
    String normalized = message.toLowerCase(java.util.Locale.ROOT);
    boolean staleChildren =
        normalized.contains("haschildren()")
            && normalized.contains("request")
            && normalized.contains("null");
    boolean assignmentDrift = normalized.contains("intvalue()");
    return staleChildren || assignmentDrift;
  }

  private static final class Result {
    int colonies;
    int shops;
    int providerUnregister;
    int requestsCancelled;
    int errors;
  }

  private static final class LiveTestResult {
    int colonies;
    int shopsSeen;
    int created;
    int errors;
    String message = "";
  }

  private static final class ResetLiveStateResult {
    int colonies;
    int shops;
    int requestsCancelled;
    int staleCleaned;
    int runtimeTrackingCleared;
    int runtimeTrackingSkipped;
    int queueEntriesCleared;
    int queueRequestsCancelled;
    int blockedActiveDeliveries;
    int assignmentPruned;
    int deliveryAssignKicks;
    int deliveryRequestsCancelled;
    int drainRounds;
    int drainResiduals;
    int errors;
  }

  private static final class HarnessSnapshot {
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
    final IColony colony;
    final BuildingCreateShop shop;
    final TileEntityCreateShop tile;
    final CreateShopBlockEntity pickup;

    HarnessShopContext(
        IColony colony,
        BuildingCreateShop shop,
        TileEntityCreateShop tile,
        CreateShopBlockEntity pickup) {
      this.colony = colony;
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
