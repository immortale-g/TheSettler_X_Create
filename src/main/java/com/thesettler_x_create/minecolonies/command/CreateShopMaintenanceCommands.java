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
}
