package com.thesettler_x_create.minecolonies.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/**
 * Registers all Create Shop server commands and delegates execution to the dedicated command
 * classes.
 *
 * <ul>
 *   <li>{@link CreateShopResetCommands} – prepare_uninstall, reset_live_state
 *   <li>{@link CreateShopDiagnosticCommands} – run_live_test
 *   <li>{@link CreateShopTestHarnessCommands} – auto_test_harness, auto_test_harness_full_all
 * </ul>
 */
public final class CreateShopMaintenanceCommands {
  private CreateShopMaintenanceCommands() {}

  public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
    var root = Commands.literal("thesettlerxcreate").requires(source -> source.hasPermission(2));

    root.then(
        Commands.literal("prepare_uninstall")
            .executes(
                context -> {
                  CreateShopResetCommands.Result result =
                      CreateShopResetCommands.prepareUninstall();
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
            .executes(
                context ->
                    CreateShopDiagnosticCommands.runLiveTestCommand(context.getSource(), 8, 8))
            .then(
                Commands.argument("requests", IntegerArgumentType.integer(1, 256))
                    .executes(
                        context ->
                            CreateShopDiagnosticCommands.runLiveTestCommand(
                                context.getSource(),
                                IntegerArgumentType.getInteger(context, "requests"),
                                8))
                    .then(
                        Commands.argument("amount", IntegerArgumentType.integer(1, 64))
                            .executes(
                                context ->
                                    CreateShopDiagnosticCommands.runLiveTestCommand(
                                        context.getSource(),
                                        IntegerArgumentType.getInteger(context, "requests"),
                                        IntegerArgumentType.getInteger(context, "amount"))))));

    root.then(
        Commands.literal("reset_live_state")
            .executes(
                context -> {
                  CreateShopResetCommands.ResetLiveStateResult result =
                      CreateShopResetCommands.resetLiveState(false);
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
                          CreateShopResetCommands.ResetLiveStateResult result =
                              CreateShopResetCommands.resetLiveState(true);
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
            .executes(
                context ->
                    CreateShopTestHarnessCommands.runAutoHarnessStart(
                        context.getSource(), 1, 8, false))
            .then(
                Commands.literal("start")
                    .executes(
                        context ->
                            CreateShopTestHarnessCommands.runAutoHarnessStart(
                                context.getSource(), 1, 8, false))
                    .then(
                        Commands.argument("requests", IntegerArgumentType.integer(1, 256))
                            .executes(
                                context ->
                                    CreateShopTestHarnessCommands.runAutoHarnessStart(
                                        context.getSource(),
                                        IntegerArgumentType.getInteger(context, "requests"),
                                        8,
                                        false))
                            .then(
                                Commands.argument("amount", IntegerArgumentType.integer(1, 64))
                                    .executes(
                                        context ->
                                            CreateShopTestHarnessCommands.runAutoHarnessStart(
                                                context.getSource(),
                                                IntegerArgumentType.getInteger(context, "requests"),
                                                IntegerArgumentType.getInteger(context, "amount"),
                                                false)))))
            .then(
                Commands.literal("start_force_queue")
                    .executes(
                        context ->
                            CreateShopTestHarnessCommands.runAutoHarnessStart(
                                context.getSource(), 1, 8, true))
                    .then(
                        Commands.argument("requests", IntegerArgumentType.integer(1, 256))
                            .executes(
                                context ->
                                    CreateShopTestHarnessCommands.runAutoHarnessStart(
                                        context.getSource(),
                                        IntegerArgumentType.getInteger(context, "requests"),
                                        8,
                                        true))
                            .then(
                                Commands.argument("amount", IntegerArgumentType.integer(1, 64))
                                    .executes(
                                        context ->
                                            CreateShopTestHarnessCommands.runAutoHarnessStart(
                                                context.getSource(),
                                                IntegerArgumentType.getInteger(context, "requests"),
                                                IntegerArgumentType.getInteger(context, "amount"),
                                                true)))))
            .then(
                Commands.literal("snapshot")
                    .executes(
                        context ->
                            CreateShopTestHarnessCommands.runAutoHarnessSnapshot(
                                context.getSource())))
            .then(
                Commands.literal("lost_inject")
                    .executes(
                        context ->
                            CreateShopTestHarnessCommands.runAutoHarnessLostInject(
                                context.getSource(), 8, 20 * 60))
                    .then(
                        Commands.argument("amount", IntegerArgumentType.integer(1, 64))
                            .executes(
                                context ->
                                    CreateShopTestHarnessCommands.runAutoHarnessLostInject(
                                        context.getSource(),
                                        IntegerArgumentType.getInteger(context, "amount"),
                                        20 * 60))
                            .then(
                                Commands.argument(
                                        "age_ticks", IntegerArgumentType.integer(1, 20 * 3600))
                                    .executes(
                                        context ->
                                            CreateShopTestHarnessCommands.runAutoHarnessLostInject(
                                                context.getSource(),
                                                IntegerArgumentType.getInteger(context, "amount"),
                                                IntegerArgumentType.getInteger(
                                                    context, "age_ticks"))))))
            .then(
                Commands.literal("lost_reorder")
                    .executes(
                        context ->
                            CreateShopTestHarnessCommands.runAutoHarnessLostReorder(
                                context.getSource())))
            .then(
                Commands.literal("lost_handover_sim")
                    .executes(
                        context ->
                            CreateShopTestHarnessCommands.runAutoHarnessLostHandoverSim(
                                context.getSource())))
            .then(
                Commands.literal("lost_cancel")
                    .executes(
                        context ->
                            CreateShopTestHarnessCommands.runAutoHarnessLostCancel(
                                context.getSource())))
            .then(
                Commands.literal("full")
                    .executes(
                        context ->
                            CreateShopTestHarnessCommands.runAutoHarnessFull(
                                context.getSource(), 3, 1, 8, true))
                    .then(
                        Commands.argument("rounds", IntegerArgumentType.integer(1, 8))
                            .executes(
                                context ->
                                    CreateShopTestHarnessCommands.runAutoHarnessFull(
                                        context.getSource(),
                                        IntegerArgumentType.getInteger(context, "rounds"),
                                        1,
                                        8,
                                        true))
                            .then(
                                Commands.argument("requests", IntegerArgumentType.integer(1, 256))
                                    .executes(
                                        context ->
                                            CreateShopTestHarnessCommands.runAutoHarnessFull(
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
                                                    CreateShopTestHarnessCommands
                                                        .runAutoHarnessFull(
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
                context ->
                    CreateShopTestHarnessCommands.runAutoHarnessFullAll(
                        context.getSource(), 2, 1, 8, 8, 20 * 60, true)));

    root.then(
        Commands.literal("diag_output_block")
            .executes(
                context ->
                    CreateShopOutputBlockTestCommands.runOutputBlockDiag(context.getSource())));

    root.then(
        Commands.literal("test_output_packaging")
            .executes(
                context ->
                    CreateShopOutputBlockTestCommands.runOutputBlockTest(context.getSource())));

    dispatcher.register(root);
  }
}
