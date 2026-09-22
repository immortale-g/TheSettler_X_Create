package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.requestable.IDeliverable;
import com.minecolonies.api.colony.requestsystem.resolver.retrying.IRetryingRequestResolver;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.core.colony.requestsystem.management.IStandardRequestManager;
import com.thesettler_x_create.DebugLog;
import com.thesettler_x_create.ProblemLog;
import java.util.Map;
import net.minecraft.world.level.Level;

/**
 * Handles reassigning MineColonies retrying-resolver requests back to Create Shop when resolvable.
 */
final class CreateShopRetryingReassignService {
  void reassignResolvableRetryingRequests(
      CreateShopRequestResolver resolver, IStandardRequestManager manager, Level level) {
    if (resolver == null || manager == null || level == null) {
      return;
    }
    var assignmentStore = manager.getRequestResolverRequestAssignmentDataStore();
    var requestHandler = manager.getRequestHandler();
    if (assignmentStore == null || requestHandler == null) {
      return;
    }
    Map<IToken<?>, java.util.Collection<IToken<?>>> assignments = assignmentStore.getAssignments();
    if (assignments == null || assignments.isEmpty()) {
      return;
    }
    java.util.List<Map.Entry<IToken<?>, java.util.Collection<IToken<?>>>> assignmentSnapshot =
        new java.util.ArrayList<>(assignments.entrySet());
    long now = level.getGameTime();
    for (Map.Entry<IToken<?>, java.util.Collection<IToken<?>>> entry : assignmentSnapshot) {
      IToken<?> ownerToken = entry.getKey();
      java.util.Collection<IToken<?>> tokens = entry.getValue();
      if (ownerToken == null || tokens == null || tokens.isEmpty()) {
        continue;
      }
      com.minecolonies.api.colony.requestsystem.resolver.IRequestResolver<?> ownerResolver;
      try {
        ownerResolver = manager.getResolverHandler().getResolver(ownerToken);
      } catch (Exception ignored) {
        continue;
      }
      if (!(ownerResolver instanceof IRetryingRequestResolver)) {
        continue;
      }
      java.util.List<IToken<?>> tokenSnapshot = new java.util.ArrayList<>(tokens);
      for (IToken<?> requestToken : tokenSnapshot) {
        if (requestToken == null) {
          continue;
        }
        Long last = resolver.getRetryingReassignAttempt(requestToken);
        if (last != null && now - last < 40L) {
          continue;
        }
        IRequest<?> request;
        try {
          request = requestHandler.getRequest(requestToken);
        } catch (Exception ignored) {
          continue;
        }
        if (request == null
            || !(request.getRequest() instanceof IDeliverable)
            || request.getState()
                == com.minecolonies.api.colony.requestsystem.request.RequestState.CANCELLED) {
          continue;
        }
        boolean deliveryWindowHold = resolver.getPendingTracker().hasDeliveryStarted(requestToken);
        @SuppressWarnings("unchecked")
        IRequest<? extends IDeliverable> casted = (IRequest<? extends IDeliverable>) request;
        if (!deliveryWindowHold && !resolver.canResolveRequest(manager, casted)) {
          continue;
        }
        resolver.markRetryingReassignAttempt(requestToken, now);
        try {
          IToken<?> newResolver =
              manager.reassignRequest(requestToken, java.util.List.of(ownerToken));
          DebugLog.info(
              "[CreateShop] retrying reassign token={} from={} to={} hold={}",
              requestToken,
              ownerToken,
              newResolver,
              deliveryWindowHold);
          return;
        } catch (Exception ex) {
          ProblemLog.once(
              "reassign-failed:" + requestToken,
              "could not hand request {} on from resolver {} ({}). It stays with a resolver that"
                  + " has already given up on it and will not be served.",
              requestToken,
              ownerToken,
              ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
        }
      }
    }
  }
}
