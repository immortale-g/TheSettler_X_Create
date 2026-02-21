package com.thesettler_x_create.minecolonies.command;

import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.requestsystem.request.RequestState;
import com.minecolonies.core.colony.requestsystem.management.IStandardRequestManager;
import com.mojang.brigadier.CommandDispatcher;
import com.thesettler_x_create.TheSettlerXCreate;
import com.thesettler_x_create.minecolonies.building.BuildingCreateShop;
import com.thesettler_x_create.minecolonies.requestsystem.resolver.CreateShopRequestResolver;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/** Server commands for Create Shop maintenance and uninstall preparation. */
public final class CreateShopMaintenanceCommands {
  private CreateShopMaintenanceCommands() {}

  public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
    dispatcher.register(
        Commands.literal("thesettlerxcreate")
            .requires(source -> source.hasPermission(2))
            .then(
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
                        })));
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

  private static final class Result {
    int colonies;
    int shops;
    int providerUnregister;
    int requestsCancelled;
    int errors;
  }
}
