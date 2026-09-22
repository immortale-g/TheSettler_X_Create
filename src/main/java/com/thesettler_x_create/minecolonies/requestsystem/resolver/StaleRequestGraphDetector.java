package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import java.util.Locale;

/**
 * Shared classification of the "the request graph went stale underneath us" failure: MineColonies
 * dereferences a request, a parent or a citizen mapping that is no longer there, and throws from
 * inside its own code while we are mid-traversal.
 *
 * <p>The two shapes we actually see, both plain NPEs:
 *
 * <ul>
 *   <li>{@code RequestHandler.onRequestCancelledDirectly} (and its neighbours) call {@code
 *       request.hasChildren()} on a token whose request is already gone.
 *   <li>{@code AbstractBuilding.onRequestedRequestCancelled} unboxes {@code
 *       getCitizensByRequest().remove(id)} without a containsKey guard, so a request the building
 *       has forgotten blows up on {@code Integer.intValue()}.
 * </ul>
 *
 * <p>Classification is by exception type plus where the throw came from - a frame in {@code
 * com.minecolonies.} is what makes the failure theirs rather than a bug in our own call. The
 * message text is only the last filter, for the case where the JVM hands us an exception with no
 * stack trace at all (a repeatedly thrown NPE loses both under {@code OmitStackTraceInFastThrow}).
 */
public final class StaleRequestGraphDetector {
  private StaleRequestGraphDetector() {}

  /** Class-name prefix that marks a frame as MineColonies' own code. */
  static final String MINECOLONIES_PACKAGE = "com.minecolonies.";

  /**
   * Members named by the helpful-NPE text of the two known failures, lowercased. Only consulted
   * when the exception carries no stack trace; see {@link #namesKnownStaleGraphMember(String)}.
   */
  static final String MEMBER_HAS_CHILDREN = "haschildren()";

  static final String MEMBER_INT_VALUE = "intvalue()";

  private static final int MAX_CAUSE_DEPTH = 8;

  /** Walks the cause chain, so a wrapped MineColonies failure is still recognised. */
  public static boolean isStaleRequestGraph(Throwable failure) {
    Throwable current = failure;
    for (int depth = 0; current != null && depth < MAX_CAUSE_DEPTH; depth++) {
      if (isStaleRequestGraphThrow(current)) {
        return true;
      }
      Throwable cause = current.getCause();
      current = cause == current ? null : cause;
    }
    return false;
  }

  private static boolean isStaleRequestGraphThrow(Throwable failure) {
    if (!(failure instanceof NullPointerException) && !(failure instanceof IllegalStateException)) {
      return false;
    }
    StackTraceElement[] trace = failure.getStackTrace();
    if (trace == null || trace.length == 0) {
      return namesKnownStaleGraphMember(failure.getMessage());
    }
    String originClass = trace[0].getClassName();
    return originClass != null && originClass.startsWith(MINECOLONIES_PACKAGE);
  }

  /**
   * Last-resort filter for a stack-trace-less exception: the helpful-NPE text names the member that
   * was dereferenced. This is JVM-generated wording, not a MineColonies contract, so it is never
   * asked first and a miss here only means we log the failure instead of cleaning up after it.
   */
  static boolean namesKnownStaleGraphMember(String message) {
    if (message == null || message.isEmpty()) {
      return false;
    }
    String normalized = message.toLowerCase(Locale.ROOT);
    return (normalized.contains(MEMBER_HAS_CHILDREN) && normalized.contains("request"))
        || normalized.contains(MEMBER_INT_VALUE);
  }
}
