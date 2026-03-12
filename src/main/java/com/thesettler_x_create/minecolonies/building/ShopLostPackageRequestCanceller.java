package com.thesettler_x_create.minecolonies.building;

import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.request.RequestState;
import com.minecolonies.api.colony.requestsystem.requestable.IDeliverable;
import com.minecolonies.api.colony.requestsystem.requestable.deliveryman.Delivery;
import com.minecolonies.api.colony.requestsystem.resolver.IRequestResolver;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.core.colony.requestsystem.management.IStandardRequestManager;
import com.thesettler_x_create.minecolonies.requestsystem.resolver.CreateShopRequestResolver;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import net.minecraft.world.item.ItemStack;

/** Matches and cancels local create-shop root requests for a lost-package tuple. */
final class ShopLostPackageRequestCanceller {
  private final BuildingCreateShop shop;

  ShopLostPackageRequestCanceller(BuildingCreateShop shop) {
    this.shop = shop;
  }

  int cancelMatchingRequests(
      ItemStack stackKey, String requesterName, String address, long requestedAt) {
    if (stackKey == null || stackKey.isEmpty() || shop == null || shop.getColony() == null) {
      return 0;
    }
    if (!(shop.getColony().getRequestManager() instanceof IStandardRequestManager standard)) {
      return 0;
    }
    var assignments = standard.getRequestResolverRequestAssignmentDataStore().getAssignments();
    if (assignments == null || assignments.isEmpty()) {
      return 0;
    }
    Set<IToken<?>> tokens = new LinkedHashSet<>();
    for (var value : assignments.values()) {
      if (value != null) {
        tokens.addAll(value);
      }
    }
    boolean tupleScoped = requestedAt > 0L;
    int cancelled = 0;
    for (IToken<?> token : tokens) {
      try {
        IRequest<?> request = standard.getRequestHandler().getRequest(token);
        if (request == null || isTerminalRequestState(request.getState())) {
          continue;
        }
        if (request.hasParent()) {
          continue;
        }
        IRequestResolver<?> owner = standard.getResolverHandler().getResolverForRequest(request);
        if (!(owner instanceof CreateShopRequestResolver shopResolver)
            || !isLocalResolver(owner, shopResolver)) {
          continue;
        }
        if (!matchesLostPackageRequest(standard, request, stackKey)) {
          continue;
        }
        if (!matchesLostPackageRequester(standard, request, requesterName)) {
          continue;
        }
        if (!matchesLostPackageAddress(standard, request, address)) {
          continue;
        }
        standard.updateRequestState(request.getId(), RequestState.CANCELLED);
        cancelled++;
        if (tupleScoped) {
          // requestedAt is tuple-scoped; without stable request timestamps in MineColonies requests,
          // cancel only one matched root request to avoid collateral cancels.
          break;
        }
      } catch (Exception ex) {
        if (tryForceCleanRequest(standard, token, ex)) {
          cancelled++;
          continue;
        }
        if (BuildingCreateShop.isDebugRequests()) {
          com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
              "[CreateShop] lost-package cancel request failed token={} error={}",
              token,
              ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
        }
      }
    }
    return cancelled;
  }

  private boolean tryForceCleanRequest(
      IStandardRequestManager standard, IToken<?> token, Exception cause) {
    if (standard == null || token == null || cause == null) {
      return false;
    }
    String message = cause.getMessage();
    if (message == null || message.isEmpty()) {
      return false;
    }
    String normalized = message.toLowerCase(Locale.ROOT);
    boolean staleGraph =
        (normalized.contains("haschildren()") && normalized.contains("request"))
            || normalized.contains("intvalue()");
    if (!staleGraph) {
      return false;
    }
    try {
      standard.getRequestHandler().cleanRequestData(token);
      if (BuildingCreateShop.isDebugRequests()) {
        com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
            "[CreateShop] lost-package cancel force-clean token={} reason={}", token, message);
      }
      return true;
    } catch (Exception ignored) {
      return false;
    }
  }

  private boolean isLocalResolver(IRequestResolver<?> owner, CreateShopRequestResolver resolver) {
    if (owner == null
        || resolver == null
        || resolver.getLocation() == null
        || shop.getLocation() == null) {
      return false;
    }
    return resolver.getLocation().equals(shop.getLocation());
  }

  private static boolean isTerminalRequestState(RequestState state) {
    return state == RequestState.CANCELLED
        || state == RequestState.COMPLETED
        || state == RequestState.FAILED
        || state == RequestState.RECEIVED
        || state == RequestState.RESOLVED;
  }

  private static boolean matchesLostPackageDeliverable(
      IDeliverable deliverable, ItemStack stackKey) {
    if (deliverable == null || stackKey == null || stackKey.isEmpty()) {
      return false;
    }
    ItemStack result = deliverable.getResult();
    if (result == null || result.isEmpty()) {
      return false;
    }
    if (ItemStack.isSameItemSameComponents(result, stackKey)) {
      return true;
    }
    return ItemStack.isSameItem(result, stackKey);
  }

  private static boolean matchesLostPackageRequest(
      IStandardRequestManager manager, IRequest<?> request, ItemStack stackKey) {
    if (manager == null || request == null || stackKey == null || stackKey.isEmpty()) {
      return false;
    }
    if (matchesLostPackageRequestPayload(request, stackKey)) {
      return true;
    }
    if (!request.hasChildren()) {
      return false;
    }
    for (IToken<?> childToken : request.getChildren()) {
      if (childToken == null) {
        continue;
      }
      IRequest<?> child = manager.getRequestHandler().getRequest(childToken);
      if (child != null && matchesLostPackageRequestPayload(child, stackKey)) {
        return true;
      }
    }
    return false;
  }

  private static boolean matchesLostPackageRequestPayload(IRequest<?> request, ItemStack stackKey) {
    if (request == null || stackKey == null || stackKey.isEmpty()) {
      return false;
    }
    if (request.getRequest() instanceof IDeliverable deliverable
        && matchesLostPackageDeliverable(deliverable, stackKey)) {
      return true;
    }
    if (request.getRequest() instanceof Delivery delivery) {
      ItemStack deliveryStack = delivery.getStack();
      if (ItemStack.isSameItemSameComponents(deliveryStack, stackKey)
          || ItemStack.isSameItem(deliveryStack, stackKey)) {
        return true;
      }
    }
    return false;
  }

  private boolean matchesLostPackageRequester(
      IStandardRequestManager manager, IRequest<?> request, String requesterName) {
    String expected = normalizeLabel(requesterName);
    if (isUnknownRequesterLabel(expected)) {
      return true;
    }
    String actual = resolveRequesterDisplay(manager, request);
    if (actual.isEmpty()) {
      return false;
    }
    if (actual.equals(expected)) {
      return true;
    }
    return actual.contains(expected) || expected.contains(actual);
  }

  private boolean matchesLostPackageAddress(
      IStandardRequestManager manager, IRequest<?> request, String address) {
    String expected = normalizeLabel(address);
    if (expected.isEmpty() || expected.equals("unknown address") || expected.equals("<unknown>")) {
      return true;
    }
    String requesterDisplay = resolveRequesterDisplay(manager, request);
    if (!requesterDisplay.isEmpty()
        && (requesterDisplay.contains(expected) || expected.contains(requesterDisplay))) {
      return true;
    }
    String shortDisplay = normalizeLabel(request == null ? "" : request.getShortDisplayString().getString());
    if (!shortDisplay.isEmpty()
        && (shortDisplay.contains(expected) || expected.contains(shortDisplay))) {
      return true;
    }
    String longDisplay = normalizeLabel(request == null ? "" : request.getLongDisplayString().getString());
    if (!longDisplay.isEmpty()
        && (longDisplay.contains(expected) || expected.contains(longDisplay))) {
      return true;
    }
    return false;
  }

  private String resolveRequesterDisplay(IStandardRequestManager manager, IRequest<?> request) {
    if (request == null) {
      return "";
    }
    try {
      var requester = request.getRequester();
      if (requester == null) {
        return "";
      }
      var display = requester.getRequesterDisplayName(manager, request);
      if (display == null) {
        return "";
      }
      return normalizeLabel(display.getString());
    } catch (Exception ignored) {
      return "";
    }
  }

  private static String normalizeLabel(String value) {
    if (value == null) {
      return "";
    }
    String normalized = value.trim().toLowerCase(Locale.ROOT);
    return normalized.isEmpty() ? "" : normalized;
  }

  private static boolean isUnknownRequesterLabel(String requester) {
    if (requester == null || requester.isEmpty()) {
      return true;
    }
    return requester.equals("unknown")
        || requester.equals("<unknown>")
        || requester.equals("unknown requester");
  }
}
