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

    assertTrue(mainSource.contains("onRegisterCommands"));
    assertTrue(mainSource.contains("CreateShopMaintenanceCommands.register"));
    assertTrue(routerSource.contains("thesettlerxcreate"));
    assertTrue(routerSource.contains("prepare_uninstall"));
    assertTrue(routerSource.contains("run_live_test"));
    assertTrue(routerSource.contains("reset_live_state"));
    assertTrue(routerSource.contains("force_warehouse_queue"));
    assertTrue(resetSource.contains("clearWarehouseQueues("));
    assertTrue(resetSource.contains("cancelCreateShopOwnedRequestsGraphAware("));
    assertTrue(resetSource.contains("cancelAllAssignedRequestsGraphAware("));
    assertTrue(resetSource.contains("cancelRequestGraphPostOrder("));
    assertTrue(resetSource.contains("cancelSingleRequest("));
    assertTrue(resetSource.contains("countShopsWithActiveLocalDeliveries("));
    assertTrue(resetSource.contains("reconcileAssignmentsAndKickCouriers("));
    assertTrue(resetSource.contains("cancelActiveLocalDeliveries("));
    assertTrue(resetSource.contains("drainRounds"));
  }
}
