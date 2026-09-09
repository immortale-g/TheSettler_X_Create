package com.thesettler_x_create.minecolonies.command;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.request.RequestState;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.core.colony.requestsystem.management.IStandardRequestManager;
import com.thesettler_x_create.minecolonies.building.BuildingCreateShop;
import com.thesettler_x_create.minecolonies.requestsystem.resolver.CreateShopRequestResolver;
import com.thesettler_x_create.minecolonies.requestsystem.resolver.RequestStateUtil;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;

/**
 * Shared helpers used by both {@link CreateShopUninstallCommands} and {@link
 * CreateShopResetCommands} (and by {@link CreateShopTestHarnessCommands}'s snapshotting), split out
 * so neither command class needs to depend on the other's internals.
 */
final class CreateShopCommandSupport {
  private CreateShopCommandSupport() {}

  /**
   * Scopes destructive maintenance commands to the colony the command was invoked from, instead of
   * blindly touching every colony on the server. Falls back to every colony only when no colony can
   * be resolved from the source's position (e.g. invoked from a server console without a location),
   * preserving that existing headless-admin workflow.
   */
  static Iterable<IColony> resolveTargetColonies(CommandSourceStack source) {
    IColony scoped = resolveSourceColony(source);
    if (scoped != null) {
      return java.util.List.of(scoped);
    }
    return IColonyManager.getInstance().getAllColonies();
  }

  private static IColony resolveSourceColony(CommandSourceStack source) {
    if (source == null) {
      return null;
    }
    var level = source.getLevel();
    if (level == null) {
      return null;
    }
    BlockPos pos;
    try {
      pos = BlockPos.containing(source.getPosition());
    } catch (Exception ex) {
      return null;
    }
    return IColonyManager.getInstance().getColonyByPosFromWorld(level, pos);
  }

  static java.util.Set<BuildingCreateShop> collectCreateShops(IColony colony) {
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

  static java.util.Set<IToken<?>> collectAssignedRequestTokens(IStandardRequestManager standard) {
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

  static boolean isCreateShopOwnedRequest(IStandardRequestManager standard, IRequest<?> request) {
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

  static boolean isTerminalState(RequestState state) {
    return RequestStateUtil.isTerminalRequestState(state);
  }
}
