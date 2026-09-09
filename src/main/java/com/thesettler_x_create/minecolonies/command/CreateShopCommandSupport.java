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

  /**
   * Detects the "request graph went stale underneath us" failure mode (a request/resolver was
   * concurrently removed by MineColonies while we were mid-traversal) so callers can clean up and
   * move on instead of logging it as a real error.
   *
   * <p>This used to match on exact substrings of the exception message (e.g. {@code
   * "hasChildren()"}), but that text is JVM-generated helpful-NPE detail, not a MineColonies
   * contract - it can change with the JDK or with unrelated MineColonies refactors and silently
   * stop matching. Instead, match on the exception's type (the two known failure shapes are both
   * NPE/ISE from dereferencing a request that vanished mid-traversal) and on the exception having
   * actually originated inside MineColonies' own request-system code, which is what makes it "a
   * stale request graph" rather than an unrelated failure in our own code.
   */
  static boolean isStaleRequestGraphException(Exception ex) {
    if (!(ex instanceof NullPointerException) && !(ex instanceof IllegalStateException)) {
      return false;
    }
    StackTraceElement[] trace = ex.getStackTrace();
    if (trace == null || trace.length == 0) {
      return false;
    }
    String originClass = trace[0].getClassName();
    return originClass != null && originClass.startsWith("com.minecolonies.");
  }
}
