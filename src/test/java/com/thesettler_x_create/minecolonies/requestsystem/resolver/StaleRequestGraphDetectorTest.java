package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * A cancel that fails because MineColonies' own request graph is already broken has to be told
 * apart from a cancel that fails because we did something wrong: the first one is cleaned up and
 * forgotten, the second one has to stay visible in the log. The old test for this asked whether the
 * exception message contained {@code "haschildren()"}, which is JVM wording rather than anything
 * MineColonies promises.
 */
class StaleRequestGraphDetectorTest {

  /** {@code RequestHandler.onRequestCancelledDirectly} -> {@code request.hasChildren()}. */
  private static final String HANDLER_CLASS =
      "com.minecolonies.core.colony.requestsystem.management.handlers.RequestHandler";

  /** {@code AbstractBuilding.onRequestedRequestCancelled} -> unboxed {@code Integer.intValue()}. */
  private static final String BUILDING_CLASS =
      "com.minecolonies.core.colony.buildings.AbstractBuilding";

  @Test
  void aVanishedRequestThrownFromMineColoniesCountsAsAStaleGraph() {
    NullPointerException failure =
        thrownAt(
            new NullPointerException(
                "Cannot invoke \"com.minecolonies.api.colony.requestsystem.request.IRequest"
                    + ".hasChildren()\" because \"request\" is null"),
            HANDLER_CLASS,
            "onRequestCancelledDirectly");

    assertTrue(StaleRequestGraphDetector.isStaleRequestGraph(failure));
  }

  @Test
  void aForgottenCitizenMappingCountsAsAStaleGraph() {
    NullPointerException failure =
        thrownAt(
            new NullPointerException(
                "Cannot invoke \"java.lang.Integer.intValue()\" because the return value of"
                    + " \"java.util.Map.remove(Object)\" is null"),
            BUILDING_CLASS,
            "onRequestedRequestCancelled");

    assertTrue(StaleRequestGraphDetector.isStaleRequestGraph(failure));
  }

  @Test
  void theSameFailureIsStillRecognisedAfterMineColoniesRewordsIt() {
    NullPointerException reworded =
        thrownAt(
            new NullPointerException("the request is gone"), HANDLER_CLASS, "onRequestCancelled");

    assertTrue(
        StaleRequestGraphDetector.isStaleRequestGraph(reworded),
        "the message text no longer names a member, so only the throw site can decide this");
  }

  @Test
  void aBrokenGraphReachedThroughAWrapperIsStillRecognised() {
    Exception wrapped =
        new IllegalArgumentException(
            "cancel failed",
            thrownAt(
                new NullPointerException("gone"), HANDLER_CLASS, "onRequestCancelledDirectly"));

    assertTrue(StaleRequestGraphDetector.isStaleRequestGraph(wrapped));
  }

  @Test
  void aFailureThrownInOurOwnCodeIsNotAStaleGraph() {
    NullPointerException ours =
        thrownAt(
            new NullPointerException(
                "Cannot invoke \"java.lang.Integer.intValue()\" because \"count\" is null"),
            "com.thesettler_x_create.minecolonies.building.ShopLostPackageRequestCanceller",
            "cancelMatchingRequests");

    assertFalse(
        StaleRequestGraphDetector.isStaleRequestGraph(ours),
        "a bug of ours must not be force-cleaned away just because the wording matches");
  }

  @Test
  void anUnrelatedMineColoniesFailureIsNotAStaleGraph() {
    Exception unrelated =
        thrownAt(
            new IllegalArgumentException("Can not reassign"), HANDLER_CLASS, "reassignRequest");

    assertFalse(StaleRequestGraphDetector.isStaleRequestGraph(unrelated));
  }

  @Test
  void aStackTraceLessNpeFallsBackToTheMemberItNames() {
    NullPointerException stripped =
        new NullPointerException(
            "Cannot invoke \"com.minecolonies.api.colony.requestsystem.request.IRequest"
                + ".hasChildren()\" because \"request\" is null");
    stripped.setStackTrace(new StackTraceElement[0]);

    assertTrue(
        StaleRequestGraphDetector.isStaleRequestGraph(stripped),
        "a repeatedly thrown NPE loses its stack trace, and then the text is all there is");
  }

  @Test
  void aStackTraceLessFailureThatNamesNothingKnownStaysVisible() {
    NullPointerException stripped = new NullPointerException();
    stripped.setStackTrace(new StackTraceElement[0]);

    assertFalse(StaleRequestGraphDetector.isStaleRequestGraph(stripped));
  }

  @Test
  void theFallbackNamesTheMineColoniesMembersItStandsFor() {
    assertTrue(
        StaleRequestGraphDetector.namesKnownStaleGraphMember(
            "Cannot invoke \"com.minecolonies.api.colony.requestsystem.request.IRequest"
                + ".hasChildren()\" because \"request\" is null"),
        "RequestHandler.onRequestCancelledDirectly dereferences a request that is already gone;"
            + " if MineColonies stopped calling IRequest.hasChildren() there, drop"
            + " StaleRequestGraphDetector.MEMBER_HAS_CHILDREN instead of leaving it dead");
    assertTrue(
        StaleRequestGraphDetector.namesKnownStaleGraphMember(
            "Cannot invoke \"java.lang.Integer.intValue()\" because the return value of"
                + " \"java.util.Map.remove(Object)\" is null"),
        "AbstractBuilding.onRequestedRequestCancelled unboxes getCitizensByRequest().remove(id)"
            + " without a containsKey guard; if that guard appears, drop"
            + " StaleRequestGraphDetector.MEMBER_INT_VALUE instead of leaving it dead");
    assertFalse(StaleRequestGraphDetector.namesKnownStaleGraphMember("connection reset"));
  }

  private static <T extends Throwable> T thrownAt(T failure, String className, String methodName) {
    failure.setStackTrace(
        new StackTraceElement[] {
          new StackTraceElement(className, methodName, className + ".java", 1),
          new StackTraceElement(
              "com.thesettler_x_create.minecolonies.building.ShopLostPackageRequestCanceller",
              "cancelMatchingRequests",
              "ShopLostPackageRequestCanceller.java",
              73)
        });
    return failure;
  }
}
