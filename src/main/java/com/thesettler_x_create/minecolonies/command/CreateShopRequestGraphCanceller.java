package com.thesettler_x_create.minecolonies.command;

import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.request.RequestState;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.core.colony.requestsystem.management.IStandardRequestManager;
import com.thesettler_x_create.TheSettlerXCreate;

/**
 * Cancels Create Shop-owned request graphs (root-first discovery, post-order cancellation) as part
 * of {@code reset_live_state} - both the "only Create Shop's own roots" pass and the "every
 * non-terminal assigned root, any owner" hard-reset pass. Extracted from {@link
 * CreateShopResetCommands}, which still owns the drain-round orchestration and the shared {@link
 * CreateShopResetCommands#handleGraphException} classifier.
 */
final class CreateShopRequestGraphCanceller {
  private CreateShopRequestGraphCanceller() {}

  static void cancelCreateShopOwnedRequestsGraphAware(
      IStandardRequestManager standard, ResetLiveStateResult result) {
    if (standard == null || result == null) {
      return;
    }
    java.util.Set<IToken<?>> assignedTokens =
        CreateShopCommandSupport.collectAssignedRequestTokens(standard);
    if (assignedTokens.isEmpty()) {
      return;
    }

    java.util.Set<IToken<?>> visited = new java.util.LinkedHashSet<>();
    java.util.List<IToken<?>> roots = new java.util.ArrayList<>();
    for (IToken<?> token : assignedTokens) {
      if (token == null) {
        continue;
      }
      try {
        var request = standard.getRequestHandler().getRequestOrNull(token);
        if (request == null || !isCreateShopOwnedRootRequest(standard, request)) {
          continue;
        }
        roots.add(token);
      } catch (Exception ex) {
        if (CreateShopResetCommands.handleGraphException(
            ex, token, "reset_live_state root scan", result)) {
          cleanupStaleToken(standard, token, result, "reset_live_state root scan");
        }
      }
    }

    for (IToken<?> root : roots) {
      cancelRequestGraphPostOrder(standard, root, visited, result);
    }

    // Cancel orphaned assigned Create Shop requests not reachable from a root graph.
    for (IToken<?> token : assignedTokens) {
      if (token == null || visited.contains(token)) {
        continue;
      }
      try {
        var request = standard.getRequestHandler().getRequestOrNull(token);
        if (request == null
            || !CreateShopCommandSupport.isCreateShopOwnedRequest(standard, request)) {
          continue;
        }
        cancelSingleRequest(standard, request, result);
      } catch (Exception ex) {
        if (CreateShopResetCommands.handleGraphException(
            ex, token, "reset_live_state orphan scan", result)) {
          cleanupStaleToken(standard, token, result, "reset_live_state orphan scan");
        }
      }
    }
  }

  /**
   * Hard-reset pass: cancel every non-terminal assigned request graph, regardless of owner
   * resolver. Prevents stuck retrying roots from surviving world reloads.
   */
  static void cancelAllAssignedRequestsGraphAware(
      IStandardRequestManager standard, ResetLiveStateResult result) {
    if (standard == null || result == null) {
      return;
    }
    java.util.Set<IToken<?>> assignedTokens =
        CreateShopCommandSupport.collectAssignedRequestTokens(standard);
    if (assignedTokens.isEmpty()) {
      return;
    }

    java.util.Set<IToken<?>> visited = new java.util.LinkedHashSet<>();
    java.util.List<IToken<?>> roots = new java.util.ArrayList<>();
    for (IToken<?> token : assignedTokens) {
      if (token == null) {
        continue;
      }
      try {
        var request = standard.getRequestHandler().getRequestOrNull(token);
        if (request == null || request.hasParent()) {
          continue;
        }
        roots.add(token);
      } catch (Exception ex) {
        if (CreateShopResetCommands.handleGraphException(
            ex, token, "reset_live_state all root scan", result)) {
          cleanupStaleToken(standard, token, result, "reset_live_state all root scan");
        }
      }
    }

    for (IToken<?> root : roots) {
      cancelRequestGraphPostOrder(standard, root, visited, result);
    }

    for (IToken<?> token : assignedTokens) {
      if (token == null || visited.contains(token)) {
        continue;
      }
      try {
        var request = standard.getRequestHandler().getRequestOrNull(token);
        if (request == null) {
          cleanupStaleToken(standard, token, result, "reset_live_state all orphan missing");
          continue;
        }
        cancelSingleRequest(standard, request, result);
      } catch (Exception ex) {
        if (CreateShopResetCommands.handleGraphException(
            ex, token, "reset_live_state all orphan scan", result)) {
          cleanupStaleToken(standard, token, result, "reset_live_state all orphan scan");
        }
      }
    }
  }

  private static void cancelRequestGraphPostOrder(
      IStandardRequestManager standard,
      IToken<?> token,
      java.util.Set<IToken<?>> visited,
      ResetLiveStateResult result) {
    if (standard == null || token == null || visited == null || result == null) {
      return;
    }
    if (!visited.add(token)) {
      return;
    }

    IRequest<?> request;
    try {
      request = standard.getRequestHandler().getRequestOrNull(token);
    } catch (Exception ex) {
      if (CreateShopResetCommands.handleGraphException(
          ex, token, "reset_live_state graph fetch", result)) {
        cleanupStaleToken(standard, token, result, "reset_live_state graph fetch");
      }
      return;
    }

    if (request == null) {
      cleanupStaleToken(standard, token, result, "reset_live_state graph missing");
      return;
    }

    if (request.hasChildren()
        && request.getChildren() != null
        && !request.getChildren().isEmpty()) {
      for (IToken<?> child : java.util.List.copyOf(request.getChildren())) {
        cancelRequestGraphPostOrder(standard, child, visited, result);
      }
    }

    cancelSingleRequest(standard, request, result);
  }

  private static void cancelSingleRequest(
      IStandardRequestManager standard, IRequest<?> request, ResetLiveStateResult result) {
    if (standard == null || request == null || result == null) {
      return;
    }
    try {
      if (CreateShopCommandSupport.isTerminalState(request.getState())) {
        if (request.getState() == RequestState.CANCELLED) {
          standard.getRequestHandler().cleanRequestData(request.getId());
          result.staleCleaned++;
        }
        return;
      }
      standard.updateRequestState(request.getId(), RequestState.CANCELLED);
      result.requestsCancelled++;
    } catch (Exception ex) {
      if (CreateShopResetCommands.handleGraphException(
          ex, request.getId(), "reset_live_state graph cancel", result)) {
        cleanupStaleToken(standard, request.getId(), result, "reset_live_state graph cancel");
      }
    }
  }

  private static void cleanupStaleToken(
      IStandardRequestManager standard,
      IToken<?> token,
      ResetLiveStateResult result,
      String reason) {
    if (standard == null || token == null || result == null) {
      return;
    }
    try {
      standard.getRequestHandler().cleanRequestData(token);
      result.staleCleaned++;
      TheSettlerXCreate.LOGGER.info("[CreateShop] {} stale cleanup token={}", reason, token);
    } catch (Exception cleanEx) {
      result.errors++;
      TheSettlerXCreate.LOGGER.warn(
          "[CreateShop] {} stale cleanup failed token={} error={}",
          reason,
          token,
          cleanEx.getMessage() == null ? cleanEx.getClass().getSimpleName() : cleanEx.getMessage());
    }
  }

  private static boolean isCreateShopOwnedRootRequest(
      IStandardRequestManager standard, IRequest<?> request) {
    if (!CreateShopCommandSupport.isCreateShopOwnedRequest(standard, request)) {
      return false;
    }
    return request != null && !request.hasParent();
  }

  static boolean hasActiveCreateShopRootRequests(IStandardRequestManager standard) {
    if (standard == null) {
      return false;
    }
    java.util.Set<IToken<?>> tokens =
        CreateShopCommandSupport.collectAssignedRequestTokens(standard);
    for (IToken<?> token : tokens) {
      if (token == null) {
        continue;
      }
      try {
        var request = standard.getRequestHandler().getRequestOrNull(token);
        if (request == null || !isCreateShopOwnedRootRequest(standard, request)) {
          continue;
        }
        if (!CreateShopCommandSupport.isTerminalState(request.getState())) {
          return true;
        }
      } catch (Exception ignored) {
        return true;
      }
    }
    return false;
  }
}
