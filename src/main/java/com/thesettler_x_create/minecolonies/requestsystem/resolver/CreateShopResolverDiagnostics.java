package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.core.colony.requestsystem.management.IStandardRequestManager;
import com.thesettler_x_create.Config;
import com.thesettler_x_create.TheSettlerXCreate;

final class CreateShopResolverDiagnostics {
  private final CreateShopRequestResolver resolver;

  CreateShopResolverDiagnostics(CreateShopRequestResolver resolver) {
    this.resolver = resolver;
  }

  void logParentChildrenState(
      IStandardRequestManager manager, IToken<?> parentToken, String phase) {
    if (!Config.DEBUG_LOGGING.getAsBoolean()) {
      return;
    }
    var handler = manager.getRequestHandler();
    logRequestStateChange(manager, parentToken, phase);
    IRequest<?> parent = handler.getRequest(parentToken);
    var children = java.util.Objects.requireNonNull(parent.getChildren(), "children");
    StringBuilder builder = new StringBuilder();
    builder.append("count=").append(children.size());
    for (IToken<?> child : children) {
      if (child == null) {
        continue;
      }
      logRequestStateChange(manager, child, phase + "-child");
      IRequest<?> childReq = handler.getRequest(child);
      String childState = childReq == null ? "<null>" : childReq.getState().toString();
      builder.append(" ").append(child).append(":").append(childState);
    }
    String snapshot = builder.toString();
    String previous = resolver.getParentChildrenSnapshots().put(parentToken, snapshot);
    if (!snapshot.equals(previous)) {
      TheSettlerXCreate.LOGGER.info(
          "[CreateShop] parent children {} parent={} {}", phase, parentToken, snapshot);
    }
  }

  void logRequestStateChange(IStandardRequestManager manager, IToken<?> token, String phase) {
    if (!Config.DEBUG_LOGGING.getAsBoolean()) {
      return;
    }
    try {
      var handler = manager.getRequestHandler();
      IRequest<?> request = handler.getRequest(token);
      if (request == null) {
        return;
      }
      String state = request.getState().toString();
      String previous = resolver.getRequestStateSnapshots().put(token, state);
      if (state.equals(previous)) {
        return;
      }
      String parent = request.getParent() == null ? "<none>" : request.getParent().toString();
      String type = request.getRequest().getClass().getName();
      boolean hasChildren = request.hasChildren();
      TheSettlerXCreate.LOGGER.info(
          "[CreateShop] request state {} token={} state={} prev={} parent={} children={} type={}",
          phase,
          token,
          state,
          previous == null ? "<none>" : previous,
          parent,
          hasChildren,
          type);
    } catch (Exception ignored) {
      // Ignore lookup errors.
    }
  }

  void logPendingReasonChange(IToken<?> token, String reason) {
    if (!Config.DEBUG_LOGGING.getAsBoolean()) {
      return;
    }
    String previous = resolver.getPendingReasonSnapshots().put(token, reason);
    if (reason.equals(previous)) {
      return;
    }
    TheSettlerXCreate.LOGGER.info(
        "[CreateShop] tickPending reason token={} reason={} prev={}",
        token,
        reason,
        previous == null ? "<none>" : previous);
  }

  void recordPendingSource(IToken<?> token, String reason) {
    if (!Config.DEBUG_LOGGING.getAsBoolean() || token == null || reason == null) {
      return;
    }
    String previous = resolver.getPendingTracker().getReason(token);
    resolver.getPendingTracker().setReason(token, reason);
    if (reason.equals(previous)) {
      return;
    }
    TheSettlerXCreate.LOGGER.info(
        "[CreateShop] pending source token={} reason={} prev={}",
        token,
        reason,
        previous == null ? "<none>" : previous);
  }
}
