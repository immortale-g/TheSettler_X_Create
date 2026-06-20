package com.thesettler_x_create.minecolonies.building;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.requestsystem.resolver.IRequestResolver;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.api.util.constant.TypeConstants;
import com.minecolonies.core.colony.requestsystem.management.IStandardRequestManager;
import com.thesettler_x_create.minecolonies.requestsystem.resolver.CreateShopRequestResolver;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import org.jetbrains.annotations.Nullable;

/**
 * Handles resolver registration health checks, resolver selection, and ownership drift recovery for
 * the Create Shop building.
 *
 * <p>Extracted from {@link BuildingCreateShop} to keep resolver-lifecycle logic in one place.
 */
final class ShopResolverHealthCheck {
  private final BuildingCreateShop shop;
  private long lastHealthcheckTick = -1L;

  ShopResolverHealthCheck(BuildingCreateShop shop) {
    this.shop = shop;
  }

  /** Verifies resolver registration is healthy and repairs it if not. Called every colony tick. */
  void ensureResolverRegistrationHealthy(IColony colony) {
    if (colony == null || colony.getWorld() == null || colony.getWorld().isClientSide) {
      return;
    }
    long now = colony.getWorld().getGameTime();
    if (lastHealthcheckTick >= 0L && now - lastHealthcheckTick < 100L) {
      return;
    }
    lastHealthcheckTick = now;

    if (!(colony.getRequestManager() instanceof IStandardRequestManager manager)) {
      return;
    }
    CreateShopRequestResolver resolver = resolveLiveShopResolver(manager);
    if (resolver == null) {
      resolver = shop.getOrCreateShopResolver();
      if (resolver == null) {
        return;
      }
    }
    if (shop.getExistingShopResolver() == null
        || !shop.getExistingShopResolver().getId().equals(resolver.getId())) {
      shop.setResolverState(
          resolver, shop.getDeliveryResolverToken(), shop.getPickupResolverToken());
    }
    IToken<?> resolverId = resolver.getId();
    boolean resolverKnown = false;
    try {
      manager.getResolverHandler().getResolver(resolverId);
      resolverKnown = true;
    } catch (IllegalArgumentException ignored) {
      // Health-check handles this.
    }

    boolean providerContains =
        manager.getProviderHandler().getRegisteredResolvers(shop).contains(resolverId);
    var deliverableAssignments =
        manager
            .getRequestableTypeRequestResolverAssignmentDataStore()
            .getAssignments()
            .get(TypeConstants.DELIVERABLE);
    boolean typeContains =
        deliverableAssignments != null && deliverableAssignments.contains(resolverId);
    boolean hasAnyLocalProviderResolver = hasAnyLocalProviderResolver(manager);
    boolean hasAnyLocalDeliverableResolver =
        hasAnyLocalDeliverableResolver(manager, deliverableAssignments);

    if (resolverKnown
        && (providerContains || hasAnyLocalProviderResolver)
        && (typeContains || hasAnyLocalDeliverableResolver)) {
      return;
    }

    if (BuildingCreateShop.isDebugRequests()) {
      com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
          "[CreateShop] resolver health mismatch: resolverKnown={} providerContains={} typeContains={} resolver={}",
          resolverKnown,
          providerContains,
          typeContains,
          resolverId);
    }
    try {
      colony.getRequestManager().onProviderRemovedFromColony(shop);
    } catch (Exception ignored) {
      // Best effort cleanup before re-register.
    }
    try {
      colony.getRequestManager().onProviderAddedToColony(shop);
    } catch (Exception ex) {
      if (BuildingCreateShop.isDebugRequests()) {
        com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
            "[CreateShop] resolver provider repair failed: {}",
            ex.getMessage() == null ? "<null>" : ex.getMessage());
      }
      return;
    }
    if (BuildingCreateShop.isDebugRequests()) {
      com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
          "[CreateShop] resolver provider repair triggered for {}", resolverId);
    }
  }

  /**
   * Selects the best live resolver for this tick, recovering from ownership/assignment drift.
   * Returns null if no resolver can be found.
   */
  @Nullable
  CreateShopRequestResolver resolveTickResolver(IColony colony) {
    if (colony == null || colony.getRequestManager() == null) {
      return shop.getOrCreateShopResolver();
    }
    if (!(colony.getRequestManager() instanceof IStandardRequestManager manager)) {
      return shop.getOrCreateShopResolver();
    }
    CreateShopRequestResolver current = shop.getOrCreateShopResolver();
    var providerResolvers = manager.getProviderHandler().getRegisteredResolvers(shop);
    if (providerResolvers == null || providerResolvers.isEmpty()) {
      CreateShopRequestResolver fallback = resolveLiveShopResolver(manager);
      if (fallback != null) {
        if (current == null || !current.getId().equals(fallback.getId())) {
          shop.setResolverState(
              fallback, shop.getDeliveryResolverToken(), shop.getPickupResolverToken());
        }
        return fallback;
      }
      return current;
    }

    var deliverableAssignments =
        manager
            .getRequestableTypeRequestResolverAssignmentDataStore()
            .getAssignments()
            .get(TypeConstants.DELIVERABLE);
    Set<IToken<?>> prioritized = new LinkedHashSet<>();
    if (deliverableAssignments != null) {
      for (IToken<?> token : deliverableAssignments) {
        if (providerResolvers.contains(token)) {
          prioritized.add(token);
        }
      }
    }
    prioritized.addAll(providerResolvers);

    CreateShopRequestResolver selected = null;
    for (IToken<?> token : prioritized) {
      try {
        IRequestResolver<?> resolver = manager.getResolverHandler().getResolver(token);
        if (resolver instanceof CreateShopRequestResolver csr) {
          selected = csr;
          break;
        }
      } catch (IllegalArgumentException ignored) {
        // Ignore stale ids; health-check will repair registration.
      }
    }

    if (selected == null) {
      selected = resolveLiveShopResolver(manager);
    } else {
      // Prefer assignment-backed resolver when provider-prioritized resolver has no work.
      if (!hasAssignedRequestsForResolver(manager, selected.getId())) {
        CreateShopRequestResolver assignmentSelected = findResolverFromAssignments(manager);
        if (assignmentSelected != null
            && !assignmentSelected.getId().equals(selected.getId())
            && hasAssignedRequestsForResolver(manager, assignmentSelected.getId())) {
          if (BuildingCreateShop.isDebugRequests()) {
            com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
                "[CreateShop] resolver assignment drift detected: switching {} -> {}",
                selected.getId(),
                assignmentSelected.getId());
          }
          selected = assignmentSelected;
        } else {
          CreateShopRequestResolver ownershipSelected = findResolverFromRequestOwnership(manager);
          if (ownershipSelected != null && !ownershipSelected.getId().equals(selected.getId())) {
            if (BuildingCreateShop.isDebugRequests()) {
              com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
                  "[CreateShop] resolver ownership drift detected: switching {} -> {}",
                  selected.getId(),
                  ownershipSelected.getId());
            }
            selected = ownershipSelected;
          }
        }
      }
    }
    // Ownership is authoritative for pending processing; prefer it when drift is detected.
    CreateShopRequestResolver ownershipSelected = findResolverFromRequestOwnership(manager);
    if (ownershipSelected != null
        && (selected == null || !selected.getId().equals(ownershipSelected.getId()))) {
      if (BuildingCreateShop.isDebugRequests()) {
        com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
            "[CreateShop] resolver ownership priority switch {} -> {}",
            selected == null ? "<null>" : selected.getId(),
            ownershipSelected.getId());
      }
      selected = ownershipSelected;
    }
    if (selected == null) {
      return current;
    }
    if (current == null || !current.getId().equals(selected.getId())) {
      shop.setResolverState(
          selected, shop.getDeliveryResolverToken(), shop.getPickupResolverToken());
      if (BuildingCreateShop.isDebugRequests()) {
        com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
            "[CreateShop] resolver synced to registered token {} (previous={})",
            selected.getId(),
            current == null ? "<null>" : current.getId());
      }
    }
    return selected;
  }

  @Nullable
  private CreateShopRequestResolver findResolverFromAssignments(IStandardRequestManager manager) {
    var assignments = manager.getRequestResolverRequestAssignmentDataStore().getAssignments();
    if (assignments == null || assignments.isEmpty()) {
      return null;
    }
    for (IToken<?> resolverToken : assignments.keySet()) {
      try {
        IRequestResolver<?> resolver = manager.getResolverHandler().getResolver(resolverToken);
        if (resolver instanceof CreateShopRequestResolver csr && isLocalShopResolver(csr)) {
          return csr;
        }
      } catch (IllegalArgumentException ignored) {
        // Ignore stale tokens; health-check and reassignment paths handle cleanup.
      }
    }
    return null;
  }

  @Nullable
  private CreateShopRequestResolver findResolverFromRequestOwnership(
      IStandardRequestManager manager) {
    if (manager == null || manager.getRequestHandler() == null) {
      return null;
    }
    var assignments = manager.getRequestResolverRequestAssignmentDataStore().getAssignments();
    if (assignments == null || assignments.isEmpty()) {
      return null;
    }
    java.util.Map<IToken<?>, Integer> ownershipCounts = new java.util.HashMap<>();
    java.util.Map<IToken<?>, CreateShopRequestResolver> resolversById = new java.util.HashMap<>();
    for (java.util.Collection<IToken<?>> requestTokens : assignments.values()) {
      if (requestTokens == null || requestTokens.isEmpty()) {
        continue;
      }
      for (IToken<?> requestToken : requestTokens) {
        try {
          var request = manager.getRequestHandler().getRequest(requestToken);
          if (request == null) {
            continue;
          }
          IRequestResolver<?> owner = manager.getResolverHandler().getResolverForRequest(request);
          if (owner instanceof CreateShopRequestResolver csr && isLocalShopResolver(csr)) {
            IToken<?> ownerId = csr.getId();
            ownershipCounts.merge(ownerId, 1, Integer::sum);
            resolversById.putIfAbsent(ownerId, csr);
          }
        } catch (Exception ignored) {
          // Ignore stale request/resolver links.
        }
      }
    }
    if (ownershipCounts.isEmpty()) {
      return null;
    }
    IToken<?> dominantOwner = null;
    int dominantCount = 0;
    for (var entry : ownershipCounts.entrySet()) {
      if (entry.getValue() > dominantCount) {
        dominantOwner = entry.getKey();
        dominantCount = entry.getValue();
      }
    }
    return dominantOwner == null ? null : resolversById.get(dominantOwner);
  }

  @Nullable
  private CreateShopRequestResolver resolveLiveShopResolver(IStandardRequestManager manager) {
    if (manager == null) {
      return null;
    }
    var providerResolvers = manager.getProviderHandler().getRegisteredResolvers(shop);
    if (providerResolvers != null && !providerResolvers.isEmpty()) {
      for (IToken<?> token : providerResolvers) {
        CreateShopRequestResolver local = resolveLocalShopResolver(manager, token);
        if (local != null) {
          return local;
        }
      }
    }
    CreateShopRequestResolver byAssignments = findResolverFromAssignments(manager);
    if (byAssignments != null) {
      return byAssignments;
    }
    return findResolverFromRequestOwnership(manager);
  }

  private boolean hasAnyLocalProviderResolver(IStandardRequestManager manager) {
    if (manager == null) {
      return false;
    }
    var providerResolvers = manager.getProviderHandler().getRegisteredResolvers(shop);
    if (providerResolvers == null || providerResolvers.isEmpty()) {
      return false;
    }
    for (IToken<?> token : providerResolvers) {
      if (resolveLocalShopResolver(manager, token) != null) {
        return true;
      }
    }
    return false;
  }

  private boolean hasAnyLocalDeliverableResolver(
      IStandardRequestManager manager, Collection<IToken<?>> deliverableAssignments) {
    if (manager == null || deliverableAssignments == null || deliverableAssignments.isEmpty()) {
      return false;
    }
    for (IToken<?> token : deliverableAssignments) {
      if (resolveLocalShopResolver(manager, token) != null) {
        return true;
      }
    }
    return false;
  }

  @Nullable
  private CreateShopRequestResolver resolveLocalShopResolver(
      IStandardRequestManager manager, IToken<?> token) {
    if (manager == null || token == null) {
      return null;
    }
    try {
      IRequestResolver<?> resolver = manager.getResolverHandler().getResolver(token);
      if (resolver instanceof CreateShopRequestResolver csr && isLocalShopResolver(csr)) {
        return csr;
      }
    } catch (Exception ignored) {
      // Ignore stale token links.
    }
    return null;
  }

  private boolean hasAssignedRequestsForResolver(
      IStandardRequestManager manager, IToken<?> resolverToken) {
    if (manager == null || resolverToken == null) {
      return false;
    }
    var assignments = manager.getRequestResolverRequestAssignmentDataStore().getAssignments();
    if (assignments == null || assignments.isEmpty()) {
      return false;
    }
    var resolverAssignments = assignments.get(resolverToken);
    return resolverAssignments != null && !resolverAssignments.isEmpty();
  }

  private boolean isLocalShopResolver(CreateShopRequestResolver resolver) {
    if (resolver == null || resolver.getLocation() == null || shop.getLocation() == null) {
      return false;
    }
    return resolver.getLocation().getDimension().equals(shop.getLocation().getDimension())
        && resolver
            .getLocation()
            .getInDimensionLocation()
            .equals(shop.getLocation().getInDimensionLocation());
  }
}
