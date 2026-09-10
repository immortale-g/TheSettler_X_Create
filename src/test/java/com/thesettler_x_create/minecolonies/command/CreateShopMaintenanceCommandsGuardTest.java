package com.thesettler_x_create.minecolonies.command;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CreateShopMaintenanceCommandsGuardTest {
  @Test
  void uninstallPrepareCommandIsRegistered() throws Exception {
    String mainSource =
        Files.readString(Path.of("src/main/java/com/thesettler_x_create/TheSettlerXCreate.java"));
    String routerSource =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/command/CreateShopMaintenanceCommands.java"));
    String resetSource =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/command/CreateShopResetCommands.java"));
    String cancellerSource =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/command/CreateShopRequestGraphCanceller.java"));

    assertTrue(mainSource.contains("onRegisterCommands"));
    assertTrue(mainSource.contains("CreateShopMaintenanceCommands.register"));
    assertTrue(routerSource.contains("thesettlerxcreate"));
    assertTrue(routerSource.contains("prepare_uninstall"));
    assertTrue(routerSource.contains("run_live_test"));
    assertTrue(routerSource.contains("reset_live_state"));
    assertTrue(routerSource.contains("force_warehouse_queue"));
    // resetLiveState orchestrates via the extracted reset_live_state collaborators (see
    // CreateShopRequestGraphCanceller/CreateShopLiveDeliveryDrainer/CreateShopAssignmentReconciler/
    // CreateShopWarehouseQueuePruner) rather than implementing the drain rounds itself.
    assertTrue(resetSource.contains("CreateShopWarehouseQueuePruner.clearWarehouseQueues("));
    assertTrue(
        resetSource.contains(
            "CreateShopRequestGraphCanceller.cancelCreateShopOwnedRequestsGraphAware("));
    assertTrue(
        resetSource.contains(
            "CreateShopRequestGraphCanceller.cancelAllAssignedRequestsGraphAware("));
    assertTrue(cancellerSource.contains("cancelRequestGraphPostOrder("));
    assertTrue(cancellerSource.contains("cancelSingleRequest("));
    assertTrue(
        resetSource.contains("CreateShopLiveDeliveryDrainer.countShopsWithActiveLocalDeliveries("));
    assertTrue(
        resetSource.contains(
            "CreateShopAssignmentReconciler.reconcileAssignmentsAndKickCouriers("));
    assertTrue(resetSource.contains("CreateShopLiveDeliveryDrainer.cancelActiveLocalDeliveries("));
    assertTrue(resetSource.contains("drainRounds"));
  }
}
