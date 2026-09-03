package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import com.minecolonies.api.colony.requestsystem.request.RequestState;

/** Shared classification of which {@link RequestState}s are terminal (the request is done). */
public final class RequestStateUtil {
  private RequestStateUtil() {}

  public static boolean isTerminalRequestState(RequestState state) {
    return state == RequestState.CANCELLED
        || state == RequestState.COMPLETED
        || state == RequestState.FAILED
        || state == RequestState.RECEIVED
        || state == RequestState.RESOLVED;
  }
}
