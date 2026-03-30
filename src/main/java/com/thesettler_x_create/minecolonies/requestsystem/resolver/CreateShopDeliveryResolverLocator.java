package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import com.minecolonies.api.colony.requestsystem.manager.IRequestManager;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.core.colony.requestsystem.management.IStandardRequestManager;
import com.thesettler_x_create.Config;
import com.thesettler_x_create.TheSettlerXCreate;

/** Resolves callback ownership for delivery-child requests. */
final class CreateShopDeliveryResolverLocator {
  private CreateShopDeliveryResolverLocator() {}

  static CreateShopRequestResolver findResolverForDelivery(
      IRequestManager manager, IRequest<?> request) {
    if (manager == null) {
      return null;
    }
    IStandardRequestManager standard = CreateShopRequestResolver.unwrapStandardManager(manager);
    if (standard == null) {
      return null;
    }
    IRequest<?> parent = null;
    try {
      IToken<?> parentToken = request == null ? null : request.getParent();
      if (parentToken != null && standard.getRequestHandler() != null) {
        parent = standard.getRequestHandler().getRequest(parentToken);
      }
    } catch (Exception ignored) {
      // Ignore lookup errors.
    }
    try {
      var resolver =
          parent == null
              ? standard.getResolverHandler().getResolverForRequest(request)
              : standard.getResolverHandler().getResolverForRequest(parent);
      if (resolver instanceof CreateShopRequestResolver shopResolver) {
        return shopResolver;
      }
    } catch (Exception ignored) {
      // Ignore lookup errors.
    }
    return null;
  }

  static CreateShopRequestResolver findResolverByDeliveryToken(
      IRequestManager manager, IRequest<?> request) {
    if (manager == null || request == null) {
      return null;
    }
    IToken<?> deliveryToken = request.getId();
    if (deliveryToken == null) {
      return null;
    }
    IToken<?> parentToken = findParentTokenByChild(manager, deliveryToken);
    if (parentToken == null) {
      return null;
    }
    IStandardRequestManager standard = CreateShopRequestResolver.unwrapStandardManager(manager);
    if (standard == null) {
      return null;
    }
    try {
      IRequest<?> parent = standard.getRequestHandler().getRequest(parentToken);
      if (parent == null) {
        return null;
      }
      var resolver = standard.getResolverHandler().getResolverForRequest(parent);
      if (resolver instanceof CreateShopRequestResolver shopResolver) {
        return shopResolver;
      }
    } catch (Exception ignored) {
      // Ignore lookup errors.
    }
    return null;
  }

  static IToken<?> findParentTokenByChild(IRequestManager manager, IToken<?> childToken) {
    if (manager == null || childToken == null) {
      return null;
    }
    IStandardRequestManager standard = CreateShopRequestResolver.unwrapStandardManager(manager);
    if (standard == null) {
      return null;
    }
    var assignments = standard.getRequestResolverRequestAssignmentDataStore().getAssignments();
    if (assignments == null || assignments.isEmpty() || standard.getRequestHandler() == null) {
      return null;
    }
    for (java.util.Collection<IToken<?>> requestTokens : assignments.values()) {
      if (requestTokens == null || requestTokens.isEmpty()) {
        continue;
      }
      for (IToken<?> requestToken : requestTokens) {
        IRequest<?> candidateParent;
        try {
          candidateParent = standard.getRequestHandler().getRequest(requestToken);
        } catch (Exception ignored) {
          continue;
        }
        if (candidateParent == null || !candidateParent.hasChildren()) {
          continue;
        }
        var children = candidateParent.getChildren();
        if (children != null && children.contains(childToken)) {
          return candidateParent.getId();
        }
      }
    }
    return null;
  }

  static IToken<?> resolveParentTokenForDelivery(IRequestManager manager, IRequest<?> request) {
    if (request == null) {
      return null;
    }
    IToken<?> parentToken = request.getParent();
    if (parentToken != null) {
      return parentToken;
    }
    return findParentTokenByChild(manager, request.getId());
  }

  static void logUnresolvedDeliveryCallback(
      String stage, IRequestManager manager, IRequest<?> request) {
    if (!isDebugLoggingEnabled()) {
      return;
    }
    IStandardRequestManager standard = CreateShopRequestResolver.unwrapStandardManager(manager);
    IToken<?> requestToken = request == null ? null : request.getId();
    IToken<?> parentToken = request == null ? null : request.getParent();
    if (parentToken == null) {
      parentToken = findParentTokenByChild(manager, requestToken);
    }
    String requestState = request == null ? "<null>" : String.valueOf(request.getState());
    IToken<?> assignmentResolver = null;
    String assignmentResolverClass = "<none>";
    String ownerResolverClass = "<none>";
    try {
      if (standard != null && requestToken != null) {
        assignmentResolver =
            standard
                .getRequestResolverRequestAssignmentDataStore()
                .getAssignmentForValue(requestToken);
        if (assignmentResolver != null) {
          Object resolver = standard.getResolverHandler().getResolver(assignmentResolver);
          assignmentResolverClass = resolver == null ? "<null>" : resolver.getClass().getName();
        }
      }
    } catch (Exception ignored) {
      assignmentResolverClass = "<error>";
    }
    try {
      if (standard != null && request != null) {
        Object owner = standard.getResolverHandler().getResolverForRequest(request);
        ownerResolverClass = owner == null ? "<null>" : owner.getClass().getName();
      }
    } catch (Exception ignored) {
      ownerResolverClass = "<error>";
    }
    TheSettlerXCreate.LOGGER.info(
        "[CreateShop] root-cause unresolved-delivery-callback stage={} token={} parent={} state={} assignmentResolver={} assignmentResolverClass={} ownerResolverClass={}",
        stage,
        requestToken,
        parentToken == null ? "<none>" : parentToken,
        requestState,
        assignmentResolver == null ? "<none>" : assignmentResolver,
        assignmentResolverClass,
        ownerResolverClass);
  }

  private static boolean isDebugLoggingEnabled() {
    try {
      return Config.DEBUG_LOGGING.getAsBoolean();
    } catch (IllegalStateException ignored) {
      return false;
    }
  }
}
