package com.thesettler_x_create.minecolonies.command;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Seam-audit finding s3-4: {@code prepareUninstall}/{@code resetLiveState} used to loop over every
 * colony on the server ({@code IColonyManager.getInstance().getAllColonies()}) regardless of where
 * the command was invoked from - so running the command in one colony's territory would touch every
 * other colony on a multi-colony server too. Fixed to scope to the colony resolved from the command
 * source's position, falling back to every colony only when no colony can be resolved (e.g. invoked
 * from a headless server console). The scoping helper itself lives in {@code
 * CreateShopCommandSupport}, shared by {@code CreateShopUninstallCommands} (prepare_uninstall) and
 * {@code CreateShopResetCommands} (reset_live_state) - see s3-5.
 */
class CreateShopResetCommandsColonyScopingGuardTest {

  @Test
  void prepareUninstallAndResetLiveStateAreSourceScoped() throws Exception {
    String uninstallSource =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/command/CreateShopUninstallCommands.java"));
    String resetSource =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/command/CreateShopResetCommands.java"));

    assertTrue(
        uninstallSource.contains("static Result prepareUninstall(CommandSourceStack source)"));
    assertTrue(
        uninstallSource.contains(
            "for (var colony : CreateShopCommandSupport.resolveTargetColonies(source))"));

    assertTrue(resetSource.contains("static ResetLiveStateResult resetLiveState("));
    assertTrue(
        resetSource.contains("CommandSourceStack source, boolean forceWarehouseQueueClear)"));
    assertTrue(
        resetSource.contains(
            "for (IColony colony : CreateShopCommandSupport.resolveTargetColonies(source))"));
  }

  @Test
  void colonyScopingHelperFallsBackToAllColoniesWhenUnresolvable() throws Exception {
    String supportSource =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/command/CreateShopCommandSupport.java"));

    int helper = supportSource.indexOf("static Iterable<IColony> resolveTargetColonies(");
    assertTrue(helper > 0);
    String helperBody =
        supportSource.substring(helper, Math.min(supportSource.length(), helper + 500));
    assertTrue(helperBody.contains("resolveSourceColony(source)"));
    assertTrue(helperBody.contains("IColonyManager.getInstance().getAllColonies()"));

    int resolver = supportSource.indexOf("private static IColony resolveSourceColony(");
    assertTrue(resolver > 0);
    String resolverBody =
        supportSource.substring(resolver, Math.min(supportSource.length(), resolver + 700));
    assertTrue(resolverBody.contains("getColonyByPosFromWorld"));
  }
}
