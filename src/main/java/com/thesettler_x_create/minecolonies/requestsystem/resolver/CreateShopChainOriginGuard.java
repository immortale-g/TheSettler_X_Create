package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.requestsystem.manager.IRequestManager;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.requestable.IConcreteDeliverable;
import com.minecolonies.api.colony.requestsystem.requestable.IDeliverable;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.core.colony.requestsystem.management.IStandardRequestManager;
import com.thesettler_x_create.minecolonies.building.BuildingCreateShop;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Keeps goods from running in a circle. A Colony Gauge asks the colony for something so it can go
 * into the Create network; if a shop then served that request out of the very network it is meant
 * to fill, the same items would travel out and back in forever, and the crafter who was supposed to
 * make them would never be asked.
 *
 * <p>So a shop does not serve what a shop asked the colony for, for the item it asked for. The
 * chain is walked to its root because MineColonies answers a request the warehouse cannot fully
 * cover with a child request of its own, made by the warehouse, for the same goods. Going by the
 * requester of the root rather than by a list of tokens also covers several shops on one network,
 * which cannot see each other's orders.
 *
 * <p>Only the item asked for is off limits. What the crafter needs to make it is a different item
 * and may come from the network, which is the point of having both systems.
 */
final class CreateShopChainOriginGuard {
  /** Deep enough for warehouse, crafting and delivery layers, bounded against a broken graph. */
  private static final int MAX_DEPTH = 32;

  private CreateShopChainOriginGuard() {}

  /**
   * Whether this request exists to fill a Create Shop's own order for the same goods.
   *
   * @return true when the shop must leave it alone
   */
  static boolean servesAShopsOwnOrder(
      IRequestManager manager, IRequest<? extends IDeliverable> request) {
    if (manager == null || request == null) {
      return false;
    }
    IStandardRequestManager standard = CreateShopRequestResolver.unwrapStandardManager(manager);
    if (standard == null || standard.getRequestHandler() == null) {
      return false;
    }
    IRequest<?> root = rootOf(standard, request);
    if (root == null) {
      // Deeper than the walk goes. Which order this belongs to cannot be said, and saying "not a
      // shop's" would be the answer that reopens the circle, so the shop stays out of it.
      return true;
    }
    if (!isShopRequest(manager.getColony(), root)) {
      return false;
    }
    return wantsTheSameGoods(request.getRequest(), root);
  }

  /**
   * The request the chain started from, or {@code null} when the walk ran into its own depth limit
   * before reaching one. A parent that cannot be read is different: the deepest request that could
   * be read is then the start of everything still known about this chain.
   */
  @Nullable
  private static IRequest<?> rootOf(IStandardRequestManager standard, IRequest<?> request) {
    IRequest<?> root = request;
    int depth = 0;
    for (; depth < MAX_DEPTH && root.hasParent(); depth++) {
      IToken<?> parentToken = root.getParent();
      if (parentToken == null) {
        break;
      }
      IRequest<?> parent;
      try {
        parent = standard.getRequestHandler().getRequest(parentToken);
      } catch (Exception ignored) {
        // A token can outlive its request; the deepest one we could read is the root then.
        break;
      }
      if (parent == null) {
        break;
      }
      root = parent;
    }
    return depth >= MAX_DEPTH && root.hasParent() ? null : root;
  }

  private static boolean isShopRequest(IColony colony, IRequest<?> root) {
    if (colony == null
        || colony.getServerBuildingManager() == null
        || root.getRequester() == null
        || root.getRequester().getLocation() == null) {
      return false;
    }
    BlockPos position = root.getRequester().getLocation().getInDimensionLocation();
    return position != null
        && colony.getServerBuildingManager().getBuilding(position) instanceof BuildingCreateShop;
  }

  /**
   * Whether {@code deliverable} would be served with the goods the shop asked for. A root whose
   * wanted items cannot be read counts as a match: not selling is a lost delivery, serving a circle
   * is a loop that never ends.
   */
  private static boolean wantsTheSameGoods(IDeliverable deliverable, IRequest<?> root) {
    if (!(root.getRequest() instanceof IConcreteDeliverable concrete)) {
      return true;
    }
    var wanted = concrete.getRequestedItems();
    if (wanted == null || wanted.isEmpty()) {
      return true;
    }
    for (ItemStack stack : wanted) {
      if (stack != null && !stack.isEmpty() && deliverable.matches(stack)) {
        return true;
      }
    }
    return false;
  }
}
