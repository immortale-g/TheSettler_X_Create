package com.thesettler_x_create.minecolonies.command;

import com.minecolonies.api.colony.requestsystem.request.RequestState;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.core.colony.requestsystem.management.IStandardRequestManager;
import com.thesettler_x_create.TheSettlerXCreate;

/**
 * Prunes stale/terminal request-resolver assignments and kicks stuck {@code CREATED}-state
 * Delivery requests, as part of {@code reset_live_state}. Extracted from {@link
 * CreateShopResetCommands}, which still owns the drain-round orchestration and the shared {@link
 * CreateShopResetCommands#handleGraphException} classifier.
 */
final class CreateShopAssignmentReconciler {
  private CreateShopAssignmentReconciler() {}

  static void reconcileAssignmentsAndKickCouriers(
      IStandardRequestManager standard, ResetLiveStateResult result) {
    if (standard == null || result == null) {
      return;
    }
    var store = standard.getRequestResolverRequestAssignmentDataStore();
    if (store == null || store.getAssignments() == null || store.getAssignments().isEmpty()) {
      return;
    }
    var requestHandler = standard.getRequestHandler();
    if (requestHandler == null) {
      return;
    }

    for (var assignmentEntry : java.util.List.copyOf(store.getAssignments().entrySet())) {
      java.util.Collection<IToken<?>> assigned = assignmentEntry.getValue();
      if (assigned == null || assigned.isEmpty()) {
        continue;
      }
      // Iterate a defensive snapshot, not the live per-resolver collection: standard.assignRequest
      // below can mutate that very collection (MineColonies' addRequestToResolver adds to it). A
      // raw Iterator would be unsafe here, since a mutation from inside assignRequest would
      // invalidate it out from under us.
      for (IToken<?> token : java.util.List.copyOf(assigned)) {
        if (token == null) {
          assigned.remove(token);
          result.assignmentPruned++;
          continue;
        }
        try {
          var request = requestHandler.getRequestOrNull(token);
          if (request == null) {
            assigned.remove(token);
            result.assignmentPruned++;
            continue;
          }
          if (CreateShopCommandSupport.isTerminalState(request.getState())) {
            assigned.remove(token);
            result.assignmentPruned++;
            continue;
          }
          if (!CreateShopCommandSupport.isCreateShopOwnedRequest(standard, request)) {
            continue;
          }
          if (request.getRequest()
                  instanceof
                  com.minecolonies.api.colony.requestsystem.requestable.deliveryman.Delivery
              && request.getState() == RequestState.CREATED) {
            try {
              // Seam-audit finding s3-2: a raw assignRequest() here re-ran resolver search
              // without first clearing the stale forward-map entry that got us into this loop
              // (this token is only visible here because it's already present in
              // store.getAssignments()) - risking a second, orphaned assignment-store entry for
              // the same token. reassignRequest() explicitly unassigns via the same
              // resolver-assignment data store before reassigning, closing that gap; it's the
              // same repair idiom BuildingCreateShop.repairOpenPickupRequest already uses.
              standard.reassignRequest(token, java.util.Collections.emptyList());
              result.deliveryAssignKicks++;
            } catch (Exception kickEx) {
              result.errors++;
              TheSettlerXCreate.LOGGER.warn(
                  "[CreateShop] reset_live_state assign kick failed token={} error={}",
                  token,
                  kickEx.getMessage() == null
                      ? kickEx.getClass().getSimpleName()
                      : kickEx.getMessage());
            }
          }
        } catch (Exception ex) {
          if (CreateShopResetCommands.handleGraphException(
              ex, token, "reset_live_state reconcile assignment", result)) {
            assigned.remove(token);
            result.assignmentPruned++;
          }
        }
      }
    }
  }
}
