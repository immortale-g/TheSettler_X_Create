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
    String commandSource =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/command/CreateShopMaintenanceCommands.java"));

    assertTrue(mainSource.contains("onRegisterCommands"));
    assertTrue(mainSource.contains("CreateShopMaintenanceCommands.register"));
    assertTrue(commandSource.contains("thesettlerxcreate"));
    assertTrue(commandSource.contains("prepare_uninstall"));
    assertTrue(commandSource.contains("run_live_test"));
    assertTrue(commandSource.contains("reset_live_state"));
    assertTrue(commandSource.contains("force_warehouse_queue"));
    assertTrue(commandSource.contains("clearWarehouseQueues("));
    assertTrue(commandSource.contains("cancelCreateShopOwnedRequestsGraphAware("));
    assertTrue(commandSource.contains("cancelRequestGraphPostOrder("));
    assertTrue(commandSource.contains("cancelSingleRequest("));
    assertTrue(commandSource.contains("countShopsWithActiveLocalDeliveries("));
    assertTrue(commandSource.contains("reconcileAssignmentsAndKickCouriers("));
    assertTrue(commandSource.contains("cancelActiveLocalDeliveries("));
    assertTrue(commandSource.contains("drainRounds"));
  }
}
