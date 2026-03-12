package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.resolver.IRequestResolver;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.core.colony.requestsystem.management.IStandardRequestManager;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Ownership/locality checks and assignment recovery helpers for the local resolver. */
final class CreateShopResolverOwnership {
  private final CreateShopRequestResolver resolver;

  CreateShopResolverOwnership(CreateShopRequestResolver resolver) {
    this.resolver = resolver;
  }

  Set<IToken<?>> collectAssignedTokensFromLocalResolvers(
      IStandardRequestManager manager, Map<IToken<?>, java.util.Collection<IToken<?>>> assignments) {
    Set<IToken<?>> recovered = new LinkedHashSet<>();
    if (manager == null || assignments == null || assignments.isEmpty()) {
      return recovered;
    }
    for (Map.Entry<IToken<?>, java.util.Collection<IToken<?>>> entry : assignments.entrySet()) {
      java.util.Collection<IToken<?>> values = entry.getValue();
      if (values == null || values.isEmpty()) {
        continue;
      }
      IToken<?> resolverToken = entry.getKey();
      IRequestResolver<?> candidate;
      try {
        candidate = manager.getResolverHandler().getResolver(resolverToken);
      } catch (Exception ignored) {
        continue;
      }
      if (candidate instanceof CreateShopRequestResolver shopResolver
          && isLocalShopResolver(shopResolver)) {
        recovered.addAll(values);
      }
    }
    return recovered;
  }

  Set<IToken<?>> collectAssignedTokensByRequestResolver(
      IStandardRequestManager manager, Map<IToken<?>, java.util.Collection<IToken<?>>> assignments) {
    Set<IToken<?>> recovered = new LinkedHashSet<>();
    if (manager == null || assignments == null || assignments.isEmpty()) {
      return recovered;
    }
    var requestHandler = manager.getRequestHandler();
    if (requestHandler == null) {
      return recovered;
    }
    for (java.util.Collection<IToken<?>> values : assignments.values()) {
      if (values == null || values.isEmpty()) {
        continue;
      }
      for (IToken<?> token : values) {
        IRequest<?> request;
        try {
          request = requestHandler.getRequest(token);
        } catch (Exception ignored) {
          continue;
        }
        if (request == null) {
          continue;
        }
        IRequestResolver<?> ownerResolver;
        try {
          ownerResolver = manager.getResolverHandler().getResolverForRequest(request);
        } catch (Exception ignored) {
          continue;
        }
        if (ownerResolver instanceof CreateShopRequestResolver shopResolver
            && isLocalShopResolver(shopResolver)) {
          recovered.add(token);
        }
      }
    }
    return recovered;
  }

  boolean isRequestOwnedByLocalResolver(IStandardRequestManager manager, IRequest<?> request) {
    if (manager == null || request == null) {
      return false;
    }
    try {
      IRequestResolver<?> owner = manager.getResolverHandler().getResolverForRequest(request);
      if (owner instanceof CreateShopRequestResolver shopResolver) {
        return isLocalShopResolver(shopResolver);
      }
    } catch (Exception ignored) {
      // Treat unresolved owner as stale for this resolver tick.
    }
    return false;
  }

  boolean isLocalShopResolver(CreateShopRequestResolver shopResolver) {
    if (shopResolver == null || shopResolver.getLocation() == null || resolver.getLocation() == null) {
      return false;
    }
    return shopResolver.getLocation().getDimension().equals(resolver.getLocation().getDimension())
        && shopResolver
            .getLocation()
            .getInDimensionLocation()
            .equals(resolver.getLocation().getInDimensionLocation());
  }
}
