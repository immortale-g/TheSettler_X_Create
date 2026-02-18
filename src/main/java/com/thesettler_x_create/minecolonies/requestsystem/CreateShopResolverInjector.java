package com.thesettler_x_create.minecolonies.requestsystem;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.util.constant.TypeConstants;
import com.minecolonies.core.colony.requestsystem.management.IStandardRequestManager;
import com.thesettler_x_create.Config;
import com.thesettler_x_create.TheSettlerXCreate;
import com.thesettler_x_create.minecolonies.building.BuildingCreateShop;
import com.thesettler_x_create.minecolonies.requestsystem.resolver.CreateShopRequestResolver;

/** Hooks Create Shop request resolvers into the MineColonies request system. */
public final class CreateShopResolverInjector {
  private static final java.util.Map<
          com.minecolonies.api.colony.requestsystem.token.IToken<?>, Long>
      REASSIGN_ATTEMPTS = new java.util.concurrent.ConcurrentHashMap<>();
  private static final java.util.Map<
          com.minecolonies.api.colony.requestsystem.token.IToken<?>, String>
      DELIVERY_DUMP_CACHE = new java.util.concurrent.ConcurrentHashMap<>();
  private static long lastDebugLogTime = 0L;
  private static long lastDeliveryDebugTime = 0L;
  private static int lastIdentityCount = -1;
  private static long lastIdentityLogTime = 0L;
  private static final java.util.Set<String> REFLECTION_WARNED =
      java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());
  private static final java.util.Set<String> CHILD_CYCLE_LOGGED =
      java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());
  private static long lastChainSanitizeTime = 0L;
  private static final java.util.Map<
          com.minecolonies.api.colony.requestsystem.token.IToken<?>, String>
      REQUEST_STATE_CACHE = new java.util.concurrent.ConcurrentHashMap<>();
  private static long lastPerfLogTime = 0L;
  private static long lastEnsureNanos = 0L;
  private static final java.util.Set<com.minecolonies.api.colony.requestsystem.token.IToken<?>>
      DISABLED_DELIVERY_RESOLVERS =
          java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());

  private CreateShopResolverInjector() {}

  public static void ensureGlobalResolver(IColony colony) {
    if (colony == null) {
      return;
    }
    long perfStart = System.nanoTime();
    if (!(colony.getRequestManager() instanceof IStandardRequestManager manager)) {
      TheSettlerXCreate.LOGGER.info("[CreateShop] Global resolver skipped (no standard manager)");
      return;
    }
    var level = colony.getWorld();
    long now = level == null ? 0L : level.getGameTime();
    boolean allowDebugLog =
        Config.DEBUG_LOGGING.getAsBoolean()
            && (now == 0L
                || now - lastDebugLogTime >= Config.RESOLVER_INJECTOR_DEBUG_COOLDOWN.getAsLong());

    var resolverHandler = manager.getResolverHandler();
    var store = manager.getRequestableTypeRequestResolverAssignmentDataStore();
    if (resolverHandler == null || store == null) {
      if (allowDebugLog) {
        TheSettlerXCreate.LOGGER.info(
            "[CreateShop] Global resolver skipped (resolver handler/store missing)");
      }
      return;
    }
    var assignments = store.getAssignments();
    if (assignments == null) {
      if (allowDebugLog) {
        TheSettlerXCreate.LOGGER.info("[CreateShop] Global resolver skipped (assignments missing)");
      }
      return;
    }
    var deliverableList =
        assignments.computeIfAbsent(TypeConstants.DELIVERABLE, key -> new java.util.ArrayList<>());
    var requestableList =
        assignments.computeIfAbsent(TypeConstants.REQUESTABLE, key -> new java.util.ArrayList<>());
    var toolList =
        assignments.computeIfAbsent(TypeConstants.TOOL, key -> new java.util.ArrayList<>());

    // Clean stale resolver tokens that are no longer registered.
    var iterator = deliverableList.iterator();
    while (iterator.hasNext()) {
      var token = iterator.next();
      try {
        resolverHandler.getResolver(token);
      } catch (IllegalArgumentException ex) {
        iterator.remove();
      }
    }
    iterator = requestableList.iterator();
    while (iterator.hasNext()) {
      var token = iterator.next();
      try {
        resolverHandler.getResolver(token);
      } catch (IllegalArgumentException ex) {
        iterator.remove();
      }
    }
    iterator = toolList.iterator();
    while (iterator.hasNext()) {
      var token = iterator.next();
      try {
        resolverHandler.getResolver(token);
      } catch (IllegalArgumentException ex) {
        iterator.remove();
      }
    }

    int injected = 0;
    int shops = 0;
    int pruned = 0;
    java.util.Set<com.minecolonies.api.colony.requestsystem.token.IToken<?>> allowedShopResolvers =
        new java.util.HashSet<>();
    java.util.Set<com.minecolonies.api.colony.requestsystem.token.IToken<?>>
        disabledDeliveryResolvers = new java.util.HashSet<>();
    for (var entry : colony.getServerBuildingManager().getBuildings().entrySet()) {
      if (!(entry.getValue() instanceof BuildingCreateShop shop)) {
        continue;
      }
      shops++;
      if (allowDebugLog) {
        TheSettlerXCreate.LOGGER.info(
            "[CreateShop] Found shop at {}", shop.getLocation().getInDimensionLocation());
      }
      // Never force resolver creation here. During building placement MineColonies registers
      // provider resolvers itself; creating/registering early can duplicate resolver tokens.
      CreateShopRequestResolver shopResolver = shop.getShopResolver();
      if (shopResolver == null) {
        continue;
      }
      allowedShopResolvers.add(shopResolver.getId());
      if (allowDebugLog) {
        TheSettlerXCreate.LOGGER.info("[CreateShop] Shop resolver id {}", shopResolver.getId());
      }
      boolean registered = false;
      try {
        resolverHandler.getResolver(shopResolver.getId());
        registered = true;
      } catch (IllegalArgumentException ignored) {
        // Not registered yet. Skip until MineColonies provider registration finishes.
      }
      if (registered && !deliverableList.contains(shopResolver.getId())) {
        deliverableList.add(shopResolver.getId());
        injected++;
      }
      if (registered && !toolList.contains(shopResolver.getId())) {
        toolList.add(shopResolver.getId());
        injected++;
      }

      // Disable shop delivery resolver if no deliveryman is assigned to the shop.
      var deliveryResolverToken = shop.getDeliveryResolverTokenPublic();
      if (deliveryResolverToken != null) {
        if (shopHasDeliveryman(shop)) {
          if (!requestableList.contains(deliveryResolverToken)) {
            requestableList.add(deliveryResolverToken);
            injected++;
          }
        } else {
          disabledDeliveryResolvers.add(deliveryResolverToken);
        }
      }
    }

    // Remove delivery resolvers that belong to shops without deliverymen.
    DISABLED_DELIVERY_RESOLVERS.clear();
    if (!disabledDeliveryResolvers.isEmpty()) {
      DISABLED_DELIVERY_RESOLVERS.addAll(disabledDeliveryResolvers);
      var removeDelivery = requestableList.iterator();
      while (removeDelivery.hasNext()) {
        var token = removeDelivery.next();
        if (disabledDeliveryResolvers.contains(token)) {
          removeDelivery.remove();
          pruned++;
        }
      }
    }

    // Prune duplicate CreateShop resolvers from DELIVERABLE list.
    var prune = deliverableList.iterator();
    while (prune.hasNext()) {
      var token = prune.next();
      try {
        var resolver = resolverHandler.getResolver(token);
        if (resolver instanceof CreateShopRequestResolver
            && !allowedShopResolvers.contains(token)) {
          prune.remove();
          pruned++;
        }
      } catch (IllegalArgumentException ignored) {
        // Already removed above.
      }
    }
    prune = requestableList.iterator();
    while (prune.hasNext()) {
      var token = prune.next();
      try {
        var resolver = resolverHandler.getResolver(token);
        if (resolver instanceof CreateShopRequestResolver) {
          prune.remove();
          pruned++;
        }
      } catch (IllegalArgumentException ignored) {
        // Already removed above.
      }
    }
    prune = toolList.iterator();
    while (prune.hasNext()) {
      var token = prune.next();
      try {
        var resolver = resolverHandler.getResolver(token);
        if (resolver instanceof CreateShopRequestResolver
            && !allowedShopResolvers.contains(token)) {
          prune.remove();
          pruned++;
        }
      } catch (IllegalArgumentException ignored) {
        // Already removed above.
      }
    }
    if (injected > 0 || pruned > 0) {
      TheSettlerXCreate.LOGGER.info("[CreateShop] Global resolver injected count={}", injected);
    } else if (allowDebugLog) {
      lastDebugLogTime = now;
      TheSettlerXCreate.LOGGER.info(
          "[CreateShop] Global resolver tick: shops={}, injected=0", shops);
      TheSettlerXCreate.LOGGER.info(
          "[CreateShop] DELIVERABLE list entries={}", deliverableList.size());
      for (var token : deliverableList) {
        try {
          var resolver = resolverHandler.getResolver(token);
          TheSettlerXCreate.LOGGER.info(
              "[CreateShop] DELIVERABLE resolver entry: {} -> {}",
              token,
              resolver.getClass().getName());
          TheSettlerXCreate.LOGGER.info(
              "[CreateShop] DELIVERABLE resolver detail: priority={} requestType={}",
              resolver.getPriority(),
              resolver.getRequestType());
        } catch (IllegalArgumentException ex) {
          TheSettlerXCreate.LOGGER.info(
              "[CreateShop] DELIVERABLE resolver entry: {} -> <missing>", token);
        }
      }
      TheSettlerXCreate.LOGGER.info(
          "[CreateShop] REQUESTABLE list entries={}", requestableList.size());
      for (var token : requestableList) {
        try {
          var resolver = resolverHandler.getResolver(token);
          TheSettlerXCreate.LOGGER.info(
              "[CreateShop] REQUESTABLE resolver entry: {} -> {}",
              token,
              resolver.getClass().getName());
          TheSettlerXCreate.LOGGER.info(
              "[CreateShop] REQUESTABLE resolver detail: priority={} requestType={}",
              resolver.getPriority(),
              resolver.getRequestType());
        } catch (IllegalArgumentException ex) {
          TheSettlerXCreate.LOGGER.info(
              "[CreateShop] REQUESTABLE resolver entry: {} -> <missing>", token);
        }
      }
      TheSettlerXCreate.LOGGER.info("[CreateShop] TOOL list entries={}", toolList.size());
      for (var token : toolList) {
        try {
          var resolver = resolverHandler.getResolver(token);
          TheSettlerXCreate.LOGGER.info(
              "[CreateShop] TOOL resolver entry: {} -> {}", token, resolver.getClass().getName());
          TheSettlerXCreate.LOGGER.info(
              "[CreateShop] TOOL resolver detail: priority={} requestType={}",
              resolver.getPriority(),
              resolver.getRequestType());
        } catch (IllegalArgumentException ex) {
          TheSettlerXCreate.LOGGER.info("[CreateShop] TOOL resolver entry: {} -> <missing>", token);
        }
      }
    }

    if (shops > 0) {
      tryReassignOpenDeliverables(manager);
    }
    lastEnsureNanos = System.nanoTime() - perfStart;
    maybeLogPerf(colony);
    if (allowDebugLog && shops > 0) {
      try {
        var assignmentStore = manager.getRequestResolverRequestAssignmentDataStore();
        var resolverAssignments = assignmentStore == null ? null : assignmentStore.getAssignments();
        int assignedTotal = 0;
        if (resolverAssignments != null) {
          for (var token : allowedShopResolvers) {
            var list = resolverAssignments.get(token);
            assignedTotal += list == null ? 0 : list.size();
          }
        }
        TheSettlerXCreate.LOGGER.info(
            "[CreateShop] resolver assignments shops={} assignedTotal={} resolverIds={}",
            shops,
            assignedTotal,
            allowedShopResolvers);
      } catch (Exception ex) {
        TheSettlerXCreate.LOGGER.info(
            "[CreateShop] resolver assignment log failed {}", ex.getMessage());
      }
    }
  }

  private static boolean shopHasDeliveryman(BuildingCreateShop shop) {
    if (shop == null) {
      return false;
    }
    var modules =
        shop.getModulesByType(
            com.thesettler_x_create.minecolonies.module.CreateShopCourierModule.class);
    if (modules == null || modules.isEmpty()) {
      return false;
    }
    var module = modules.get(0);
    var citizens = module.getAssignedCitizen();
    if (citizens == null || citizens.isEmpty()) {
      return false;
    }
    for (var citizen : citizens) {
      if (citizen == null) {
        continue;
      }
      if (citizen.getJob() instanceof com.minecolonies.core.colony.jobs.JobDeliveryman) {
        return true;
      }
    }
    return false;
  }

  private static void maybeLogPerf(IColony colony) {
    if (!Config.DEBUG_LOGGING.getAsBoolean()) {
      return;
    }
    if (colony == null || colony.getWorld() == null) {
      return;
    }
    long now = colony.getWorld().getGameTime();
    if (now != 0L && now - lastPerfLogTime < Config.PERF_LOG_COOLDOWN.getAsLong()) {
      return;
    }
    lastPerfLogTime = now;
    TheSettlerXCreate.LOGGER.info(
        "[CreateShop] perf injector ensureGlobalResolver={}us", lastEnsureNanos / 1000L);
  }

  private static void tryReassignOpenDeliverables(IStandardRequestManager manager) {
    var colony = manager.getColony();
    var level = colony == null ? null : colony.getWorld();
    long gameTime = level == null ? 0L : level.getGameTime();
    var resolverHandler = manager.getResolverHandler();
    var requestHandler = manager.getRequestHandler();
    var identities = manager.getRequestIdentitiesDataStore().getIdentities();
    if (resolverHandler == null || requestHandler == null || identities == null) {
      return;
    }
    var assignmentStore = manager.getRequestResolverRequestAssignmentDataStore();
    var typeAssignmentsStore = manager.getRequestableTypeRequestResolverAssignmentDataStore();
    var typeAssignments =
        typeAssignmentsStore == null ? null : typeAssignmentsStore.getAssignments();
    sanitizeAllRequestChains(manager, requestHandler, gameTime);
    if (Config.DEBUG_LOGGING.getAsBoolean()) {
      boolean logIdentity =
          identities.size() != lastIdentityCount
              || gameTime == 0L
              || gameTime - lastIdentityLogTime
                  >= Config.RESOLVER_INJECTOR_DEBUG_COOLDOWN.getAsLong();
      if (logIdentity) {
        lastIdentityCount = identities.size();
        lastIdentityLogTime = gameTime;
        TheSettlerXCreate.LOGGER.info("[CreateShop] Request identities size={}", identities.size());
      }
    }
    if (identities.isEmpty()) {
      return;
    }

    int reassignCount = 0;
    int unassignedDeliveries = 0;
    var identitySnapshot = new java.util.ArrayList<>(identities.entrySet());
    for (var entry : identitySnapshot) {
      var token = entry.getKey();
      com.minecolonies.api.colony.requestsystem.request.IRequest<?> request = entry.getValue();
      sanitizeChildren(token, request, requestHandler);
      if (Config.DEBUG_LOGGING.getAsBoolean()) {
        if (request == null) {
          TheSettlerXCreate.LOGGER.info("[CreateShop] request {} -> <null>", token);
        } else {
          String type =
              request.getRequest() == null ? "<null>" : request.getRequest().getClass().getName();
          String state = String.valueOf(request.getState());
          if (assignmentStore != null) {
            var assignedToken = assignmentStore.getAssignmentForValue(token);
            String assignedInfo = "<none>";
            if (assignedToken != null) {
              try {
                var assignedResolver = resolverHandler.getResolver(assignedToken);
                assignedInfo =
                    assignedToken
                        + " -> "
                        + assignedResolver.getClass().getName()
                        + " (priority="
                        + assignedResolver.getPriority()
                        + ")";
              } catch (IllegalArgumentException ex) {
                assignedInfo = assignedToken + " -> <missing>";
              }
            }
            String snapshot = type + "|" + state + "|" + assignedInfo;
            String last = REQUEST_STATE_CACHE.put(token, snapshot);
            if (!snapshot.equals(last)) {
              TheSettlerXCreate.LOGGER.info(
                  "[CreateShop] request {} type={} state={}", token, type, state);
              TheSettlerXCreate.LOGGER.info(
                  "[CreateShop] request {} assigned resolver {}", token, assignedInfo);
            }
          } else {
            String snapshot = type + "|" + state + "|<no-assignment>";
            String last = REQUEST_STATE_CACHE.put(token, snapshot);
            if (!snapshot.equals(last)) {
              TheSettlerXCreate.LOGGER.info(
                  "[CreateShop] request {} type={} state={}", token, type, state);
            }
          }
        }
      }
      if (request != null
          && request.getRequest()
              instanceof com.minecolonies.api.colony.requestsystem.requestable.deliveryman.Delivery
          && assignmentStore != null
          && assignmentStore.getAssignmentForValue(token) == null) {
        unassignedDeliveries++;
      }
      if (Config.DEBUG_LOGGING.getAsBoolean() && request != null) {
        Object payload = request.getRequest();
        if (payload
                instanceof
                com.minecolonies.api.colony.requestsystem.requestable.deliveryman.Delivery
            || payload
                instanceof
                com.minecolonies.api.colony.requestsystem.requestable.deliveryman
                    .IDeliverymanRequestable) {
          String dump = buildDeliveryDebugDump(token, request, assignmentStore);
          String last = DELIVERY_DUMP_CACHE.put(token, dump);
          if (!dump.equals(last)) {
            TheSettlerXCreate.LOGGER.info("[CreateShop] delivery debug: {}", dump);
          }
        }
      }
      reassignCount +=
          tryReassignRequest(
              token, request, gameTime, resolverHandler, requestHandler, assignmentStore);
    }
    if (reassignCount > 0 && Config.DEBUG_LOGGING.getAsBoolean()) {
      TheSettlerXCreate.LOGGER.info(
          "[CreateShop] Reassigned {} deliverable requests for CreateShop", reassignCount);
    }
    if (Config.DEBUG_LOGGING.getAsBoolean()
        && unassignedDeliveries > 0
        && (gameTime == 0L
            || gameTime - lastDeliveryDebugTime
                >= Config.RESOLVER_DELIVERY_DEBUG_COOLDOWN.getAsLong())) {
      lastDeliveryDebugTime = gameTime;
      java.util.Collection<com.minecolonies.api.colony.requestsystem.token.IToken<?>>
          requestableList =
              typeAssignments == null ? null : typeAssignments.get(TypeConstants.REQUESTABLE);
      int requestableCount = requestableList == null ? 0 : requestableList.size();
      int matchingResolvers = 0;
      java.util.List<String> matchingResolverNames = new java.util.ArrayList<>();
      java.util.List<String> deliveryResolverAssignments = new java.util.ArrayList<>();
      if (requestableList != null) {
        for (var resolverToken : requestableList) {
          try {
            var resolver = resolverHandler.getResolver(resolverToken);
            com.google.common.reflect.TypeToken<?> typeToken = resolver.getRequestType();
            Class<?> requestType = typeToken == null ? null : typeToken.getRawType();
            boolean matches =
                requestType != null
                    && (requestType.isAssignableFrom(
                            com.minecolonies.api.colony.requestsystem.requestable.deliveryman
                                .Delivery.class)
                        || requestType.isAssignableFrom(
                            com.minecolonies.api.colony.requestsystem.requestable.deliveryman
                                .IDeliverymanRequestable.class)
                        || requestType.isAssignableFrom(
                            com.minecolonies.api.colony.requestsystem.requestable.IRequestable
                                .class));
            if (matches) {
              matchingResolvers++;
              if (matchingResolverNames.size() < 5) {
                matchingResolverNames.add(resolver.getClass().getName());
              }
            }
            if (resolver
                instanceof
                com.minecolonies.core.colony.requestsystem.resolvers.DeliveryRequestResolver) {
              var resolverAssignmentStore = manager.getRequestResolverRequestAssignmentDataStore();
              var assignments =
                  resolverAssignmentStore == null ? null : resolverAssignmentStore.getAssignments();
              var assignedRequests = assignments == null ? null : assignments.get(resolverToken);
              int assignedCount = assignedRequests == null ? 0 : assignedRequests.size();
              deliveryResolverAssignments.add(resolver.getClass().getName() + "=" + assignedCount);
            }
          } catch (IllegalArgumentException ignored) {
            // Missing resolver.
          }
        }
      }
      TheSettlerXCreate.LOGGER.info(
          "[CreateShop] Delivery requests unassigned={} REQUESTABLE resolvers={} matchingDeliveryResolvers={} examples={}",
          unassignedDeliveries,
          requestableCount,
          matchingResolvers,
          matchingResolverNames.isEmpty() ? "<none>" : matchingResolverNames);
      if (!deliveryResolverAssignments.isEmpty()) {
        TheSettlerXCreate.LOGGER.info(
            "[CreateShop] DeliveryRequestResolver assigned counts: {}",
            deliveryResolverAssignments);
      }
    }
  }

  private static int tryReassignRequest(
      com.minecolonies.api.colony.requestsystem.token.IToken<?> token,
      com.minecolonies.api.colony.requestsystem.request.IRequest<?> request,
      long gameTime,
      com.minecolonies.api.colony.requestsystem.management.IResolverHandler resolverHandler,
      com.minecolonies.api.colony.requestsystem.management.IRequestHandler requestHandler,
      com.minecolonies.api.colony.requestsystem.data.IRequestResolverRequestAssignmentDataStore
          assignmentStore) {
    if (request == null) {
      return 0;
    }
    Long lastAttempt = REASSIGN_ATTEMPTS.get(token);
    if (lastAttempt != null && gameTime > 0L && (gameTime - lastAttempt) < 200L) {
      return 0;
    }
    REASSIGN_ATTEMPTS.put(token, gameTime);
    if (request.hasChildren()) {
      int reassigned = 0;
      if (Config.DEBUG_LOGGING.getAsBoolean()) {
        TheSettlerXCreate.LOGGER.info(
            "[CreateShop] request {} has {} child(ren)", token, request.getChildren().size());
      }
      for (var childToken : request.getChildren()) {
        com.minecolonies.api.colony.requestsystem.request.IRequest<?> child;
        try {
          child = requestHandler.getRequest(childToken);
        } catch (IllegalArgumentException ex) {
          child = null;
        }
        if (Config.DEBUG_LOGGING.getAsBoolean()) {
          String childType =
              child == null || child.getRequest() == null
                  ? "<null>"
                  : child.getRequest().getClass().getName();
          String childState = child == null ? "<null>" : String.valueOf(child.getState());
          TheSettlerXCreate.LOGGER.info(
              "[CreateShop] child {} type={} state={}", childToken, childType, childState);
          if (child == null) {
            TheSettlerXCreate.LOGGER.info(
                "[CreateShop] child {} missing in request handler (parent={})", childToken, token);
          }
        }
        reassigned +=
            tryReassignRequest(
                childToken, child, gameTime, resolverHandler, requestHandler, assignmentStore);
      }
      if (Config.DEBUG_LOGGING.getAsBoolean() && reassigned == 0) {
        TheSettlerXCreate.LOGGER.info("[CreateShop] skip reassign {} (has children)", token);
      }
      return reassigned;
    }
    if (Config.DEBUG_LOGGING.getAsBoolean()) {
      String type =
          request.getRequest() == null ? "<null>" : request.getRequest().getClass().getName();
      TheSettlerXCreate.LOGGER.info(
          "[CreateShop] inspect request {} type={} state={}", token, type, request.getState());
    }
    var state = request.getState();
    boolean assignedToRetrying = false;
    boolean assignedToPlayer = false;
    com.minecolonies.api.colony.requestsystem.token.IToken<?> assignedToken = null;
    if (assignmentStore != null) {
      assignedToken = assignmentStore.getAssignmentForValue(token);
      if (assignedToken != null) {
        try {
          var assignedResolver = resolverHandler.getResolver(assignedToken);
          assignedToRetrying =
              assignedResolver
                  instanceof
                  com.minecolonies.core.colony.requestsystem.resolvers
                      .StandardRetryingRequestResolver;
          assignedToPlayer =
              assignedResolver
                  instanceof
                  com.minecolonies.core.colony.requestsystem.resolvers
                      .StandardPlayerRequestResolver;
        } catch (IllegalArgumentException ignored) {
          // Missing resolver; treat as unassigned.
        }
      }
    }
    Object payload = request.getRequest();
    boolean isDeliverable =
        payload instanceof com.minecolonies.api.colony.requestsystem.requestable.IDeliverable;
    boolean isDeliveryRequest =
        payload
                instanceof
                com.minecolonies.api.colony.requestsystem.requestable.deliveryman.Delivery
            || payload
                instanceof
                com.minecolonies.api.colony.requestsystem.requestable.deliveryman
                    .IDeliverymanRequestable;
    if (isDeliveryRequest
        && assignedToken != null
        && !DISABLED_DELIVERY_RESOLVERS.contains(assignedToken)) {
      if (Config.DEBUG_LOGGING.getAsBoolean()) {
        TheSettlerXCreate.LOGGER.info("[CreateShop] skip reassign {} (delivery request)", token);
      }
      return 0;
    }
    boolean allowDeliveryReassign =
        isDeliveryRequest
            && assignedToken != null
            && DISABLED_DELIVERY_RESOLVERS.contains(assignedToken);
    if (state == com.minecolonies.api.colony.requestsystem.request.RequestState.IN_PROGRESS
            && assignedToken != null
            && !assignedToRetrying
            && !assignedToPlayer
            && !allowDeliveryReassign
        || state == com.minecolonies.api.colony.requestsystem.request.RequestState.RESOLVED
        || state == com.minecolonies.api.colony.requestsystem.request.RequestState.COMPLETED
        || state == com.minecolonies.api.colony.requestsystem.request.RequestState.CANCELLED
        || state == com.minecolonies.api.colony.requestsystem.request.RequestState.FAILED
        || state == com.minecolonies.api.colony.requestsystem.request.RequestState.RECEIVED
        || state == com.minecolonies.api.colony.requestsystem.request.RequestState.FINALIZING
        || state
            == com.minecolonies.api.colony.requestsystem.request.RequestState
                .FOLLOWUP_IN_PROGRESS) {
      if (Config.DEBUG_LOGGING.getAsBoolean() && isDeliveryRequest) {
        TheSettlerXCreate.LOGGER.info(
            "[CreateShop] skip reassign {} (delivery state={} assigned={})",
            token,
            state,
            assignedToken == null ? "<none>" : assignedToken);
      }
      return 0;
    }
    if (!isDeliverable && !isDeliveryRequest) {
      return 0;
    }
    boolean assigned = true;
    try {
      var assignedResolver = resolverHandler.getResolverForRequest(request);
      if (assignedResolver instanceof CreateShopRequestResolver) {
        return 0;
      }
    } catch (IllegalArgumentException ignored) {
      assigned = false;
    }
    try {
      if (assigned) {
        requestHandler.reassignRequest(request, java.util.Collections.emptyList());
      } else {
        requestHandler.assignRequest(request);
      }
      if (Config.DEBUG_LOGGING.getAsBoolean()) {
        TheSettlerXCreate.LOGGER.info(
            "[CreateShop] reassigned request {} (assigned={})", token, assigned);
        if (isDeliveryRequest && assignmentStore != null) {
          var postAssignToken = assignmentStore.getAssignmentForValue(token);
          if (postAssignToken != null) {
            try {
              var assignedResolver = resolverHandler.getResolver(postAssignToken);
              TheSettlerXCreate.LOGGER.info(
                  "[CreateShop] delivery {} now assigned to {} -> {}",
                  token,
                  postAssignToken,
                  assignedResolver.getClass().getName());
            } catch (IllegalArgumentException ex) {
              TheSettlerXCreate.LOGGER.info(
                  "[CreateShop] delivery {} now assigned to {} -> <missing>",
                  token,
                  postAssignToken);
            }
          } else {
            TheSettlerXCreate.LOGGER.info(
                "[CreateShop] delivery {} still unassigned after reassign", token);
          }
        }
      }
      return 1;
    } catch (IllegalArgumentException ex) {
      if (Config.DEBUG_LOGGING.getAsBoolean()) {
        TheSettlerXCreate.LOGGER.info(
            "[CreateShop] reassign failed {}: {}", token, ex.getMessage());
      }
      return 0;
    }
  }

  private static void sanitizeChildren(
      com.minecolonies.api.colony.requestsystem.token.IToken<?> token,
      com.minecolonies.api.colony.requestsystem.request.IRequest<?> request,
      com.minecolonies.api.colony.requestsystem.management.IRequestHandler requestHandler) {
    if (request == null || token == null || requestHandler == null) {
      return;
    }
    var children = request.getChildren();
    if (children == null || children.isEmpty()) {
      return;
    }
    if (children.contains(token)) {
      request.removeChild(token);
      if (Config.DEBUG_LOGGING.getAsBoolean()) {
        String key = "self:" + token;
        if (CHILD_CYCLE_LOGGED.add(key)) {
          TheSettlerXCreate.LOGGER.info("[CreateShop] removed self-child cycle for {}", token);
        }
      }
    }
    for (var childToken : children) {
      if (childToken == null || childToken.equals(token)) {
        continue;
      }
      com.minecolonies.api.colony.requestsystem.request.IRequest<?> child;
      try {
        child = requestHandler.getRequest(childToken);
      } catch (IllegalArgumentException ex) {
        child = null;
      }
      if (child == null) {
        request.removeChild(childToken);
        if (Config.DEBUG_LOGGING.getAsBoolean()) {
          String key = "missing:" + token + ":" + childToken;
          if (CHILD_CYCLE_LOGGED.add(key)) {
            TheSettlerXCreate.LOGGER.info(
                "[CreateShop] removed missing child token parent={} child={}", token, childToken);
          }
        }
        continue;
      }
      if (child == null || child.getChildren() == null || child.getChildren().isEmpty()) {
        continue;
      }
      if (child.getChildren().contains(token)) {
        child.removeChild(token);
        if (Config.DEBUG_LOGGING.getAsBoolean()) {
          String key = "pair:" + token + ":" + childToken;
          if (CHILD_CYCLE_LOGGED.add(key)) {
            TheSettlerXCreate.LOGGER.info(
                "[CreateShop] removed child-parent cycle parent={} child={}", token, childToken);
          }
        }
      }
    }
  }

  private static void sanitizeAllRequestChains(
      IStandardRequestManager manager,
      com.minecolonies.api.colony.requestsystem.management.IRequestHandler requestHandler,
      long gameTime) {
    if (manager == null || requestHandler == null) {
      return;
    }
    if (gameTime != 0L
        && gameTime - lastChainSanitizeTime < Config.RESOLVER_CHAIN_SANITIZE_COOLDOWN.getAsLong()) {
      return;
    }
    lastChainSanitizeTime = gameTime;
    var identities = manager.getRequestIdentitiesDataStore().getIdentities();
    if (identities == null || identities.isEmpty()) {
      return;
    }
    java.util.Set<com.minecolonies.api.colony.requestsystem.token.IToken<?>> visited =
        new java.util.HashSet<>();
    int removedCycles = 0;
    for (var entry : identities.entrySet()) {
      var root = entry.getValue();
      if (root == null || root.getId() == null) {
        continue;
      }
      if (!visited.add(root.getId())) {
        continue;
      }
      removedCycles += sanitizeRequestChain(root, requestHandler, visited);
    }
    if (Config.DEBUG_LOGGING.getAsBoolean() && removedCycles > 0) {
      TheSettlerXCreate.LOGGER.info(
          "[CreateShop] sanitized request chains removedCycles={}", removedCycles);
    }
  }

  private static int sanitizeRequestChain(
      com.minecolonies.api.colony.requestsystem.request.IRequest<?> root,
      com.minecolonies.api.colony.requestsystem.management.IRequestHandler requestHandler,
      java.util.Set<com.minecolonies.api.colony.requestsystem.token.IToken<?>> visited) {
    if (root == null || requestHandler == null || root.getId() == null) {
      return 0;
    }
    java.util.Set<com.minecolonies.api.colony.requestsystem.token.IToken<?>> visiting =
        new java.util.HashSet<>();
    java.util.ArrayDeque<com.minecolonies.api.colony.requestsystem.request.IRequest<?>> stack =
        new java.util.ArrayDeque<>();
    java.util.ArrayDeque<
            java.util.Iterator<com.minecolonies.api.colony.requestsystem.token.IToken<?>>>
        itStack = new java.util.ArrayDeque<>();
    stack.push(root);
    itStack.push(
        root.getChildren() == null
            ? java.util.Collections
                .<com.minecolonies.api.colony.requestsystem.token.IToken<?>>emptyList()
                .iterator()
            : root.getChildren().iterator());
    visiting.add(root.getId());
    int removed = 0;
    int steps = 0;
    while (!stack.isEmpty() && steps < 1024) {
      steps++;
      var it = itStack.peek();
      if (it == null || !it.hasNext()) {
        var done = stack.pop();
        itStack.pop();
        if (done != null && done.getId() != null) {
          visiting.remove(done.getId());
        }
        continue;
      }
      var childToken = it.next();
      var parent = stack.peek();
      if (childToken == null || parent == null || parent.getId() == null) {
        continue;
      }
      if (childToken.equals(parent.getId())) {
        parent.removeChild(childToken);
        removed++;
        if (Config.DEBUG_LOGGING.getAsBoolean()) {
          String key = "self:" + parent.getId();
          if (CHILD_CYCLE_LOGGED.add(key)) {
            TheSettlerXCreate.LOGGER.info("[CreateShop] removed self-cycle {}", parent.getId());
          }
        }
        continue;
      }
      if (visiting.contains(childToken)) {
        parent.removeChild(childToken);
        removed++;
        if (Config.DEBUG_LOGGING.getAsBoolean()) {
          String key = "cycle:" + parent.getId() + ":" + childToken;
          if (CHILD_CYCLE_LOGGED.add(key)) {
            TheSettlerXCreate.LOGGER.info(
                "[CreateShop] removed request chain cycle parent={} child={}",
                parent.getId(),
                childToken);
          }
        }
        continue;
      }
      if (visited.contains(childToken)) {
        continue;
      }
      com.minecolonies.api.colony.requestsystem.request.IRequest<?> child;
      try {
        child = requestHandler.getRequest(childToken);
      } catch (IllegalArgumentException ex) {
        child = null;
      }
      if (child == null) {
        continue;
      }
      visited.add(childToken);
      visiting.add(childToken);
      stack.push(child);
      itStack.push(
          child.getChildren() == null
              ? java.util.Collections
                  .<com.minecolonies.api.colony.requestsystem.token.IToken<?>>emptyList()
                  .iterator()
              : child.getChildren().iterator());
    }
    return removed;
  }

  private static String buildDeliveryDebugDump(
      com.minecolonies.api.colony.requestsystem.token.IToken<?> token,
      com.minecolonies.api.colony.requestsystem.request.IRequest<?> request,
      com.minecolonies.api.colony.requestsystem.data.IRequestResolverRequestAssignmentDataStore
          assignmentStore) {
    String state = request == null ? "<null>" : String.valueOf(request.getState());
    String assigned = "<none>";
    if (assignmentStore != null) {
      var assignedToken = assignmentStore.getAssignmentForValue(token);
      if (assignedToken != null) {
        assigned = assignedToken.toString();
      }
    }
    Object payload = request == null ? null : request.getRequest();
    String requester = "<n/a>";
    String pickup = "<n/a>";
    String delivery = "<n/a>";
    String stack = "<n/a>";
    Object requestRequester = request == null ? null : tryInvoke(request, "getRequester");
    if (requestRequester != null) {
      requester = requestRequester.toString();
    }
    if (payload != null) {
      Object requesterValue = tryInvoke(payload, "getRequester");
      Object pickupValue = tryInvoke(payload, "getPickupLocation");
      Object deliveryValue = tryInvoke(payload, "getDeliveryLocation");
      Object fromValue = tryInvoke(payload, "getFrom");
      Object toValue = tryInvoke(payload, "getTo");
      Object startValue = tryInvoke(payload, "getStart");
      Object targetValue = tryInvoke(payload, "getTarget");
      Object stackValue = tryInvoke(payload, "getStack");
      if (requesterValue != null) {
        requester = requesterValue.toString();
      }
      if (pickupValue != null) {
        pickup = pickupValue.toString();
      } else if (fromValue != null) {
        pickup = fromValue.toString();
      } else if (startValue != null) {
        pickup = startValue.toString();
      }
      if (deliveryValue != null) {
        delivery = deliveryValue.toString();
      } else if (toValue != null) {
        delivery = toValue.toString();
      } else if (targetValue != null) {
        delivery = targetValue.toString();
      }
      if (stackValue != null) {
        stack = stackValue.toString();
      }
    }
    return "token="
        + token
        + " state="
        + state
        + " assigned="
        + assigned
        + " requester="
        + requester
        + " pickup="
        + pickup
        + " delivery="
        + delivery
        + " stack="
        + stack;
  }

  private static Object tryInvoke(Object target, String methodName) {
    if (target == null) {
      return null;
    }
    try {
      var method = target.getClass().getMethod(methodName);
      return method.invoke(target);
    } catch (Exception ex) {
      logReflectionFailure(target, methodName, ex);
      return null;
    }
  }

  private static void logReflectionFailure(Object target, String methodName, Exception ex) {
    if (!Config.DEBUG_LOGGING.getAsBoolean() || target == null) {
      return;
    }
    String key =
        target.getClass().getName() + "#" + methodName + ":" + ex.getClass().getSimpleName();
    if (!REFLECTION_WARNED.add(key)) {
      return;
    }
    TheSettlerXCreate.LOGGER.info(
        "[CreateShop] reflection call failed {} err={}",
        key,
        ex.getMessage() == null ? "<null>" : ex.getMessage());
  }
}
