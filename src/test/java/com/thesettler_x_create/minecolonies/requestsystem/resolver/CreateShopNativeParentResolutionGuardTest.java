package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Guards that MineColonies closes Create Shop parents itself. Detaching a completed delivery child
 * inside the completion callback made MineColonies skip resolveRequest, which left fully delivered
 * parents open forever and blocked top-ups after partial deliveries.
 */
class CreateShopNativeParentResolutionGuardTest {
  private static final String DIR =
      "src/main/java/com/thesettler_x_create/minecolonies/requestsystem/resolver/";

  @Test
  void deliveryCompletionNeverDetachesTheChildOrResolvesTheParent() throws Exception {
    String source = Files.readString(Path.of(DIR + "CreateShopDeliveryCompletionService.java"));

    assertFalse(source.contains("setParent(null)"));
    assertFalse(source.contains("removeChild("));
    assertFalse(source.contains("RequestState.RESOLVED"));
  }

  @Test
  void resolveRequestFinishesOnlyThroughTheDeliveredAmount() throws Exception {
    String source =
        Files.readString(Path.of(DIR + "CreateShopTerminalRequestLifecycleService.java"));

    assertTrue(
        source.contains("finishIfDelivered(resolver, manager, request, \"resolveRequest\")"));
    assertTrue(source.contains("outstandingNeededService.compute(request, deliverable, 0) > 0"));
    assertTrue(source.contains("|| request.hasChildren()"));
    assertFalse(source.contains("cooldown.isOrdered("));
    assertFalse(source.contains("resolveViaWarehouse("));
  }

  @Test
  void noOtherResolverServiceSetsRequestsResolvedDirectly() throws Exception {
    for (String file :
        new String[] {
          "CreateShopPendingRequestProcessorService.java",
          "CreateShopChildReconciliationService.java",
          "CreateShopDeliveryCompletionService.java"
        }) {
      String source = Files.readString(Path.of(DIR + file));
      // Reading a child's RESOLVED state is fine; only setting it is the parent's job.
      assertFalse(source.contains("RequestState.RESOLVED)"), file);
    }
  }

  @Test
  void topupIsNotBlockedByAnEarlierDelivery() throws Exception {
    String topup = Files.readString(Path.of(DIR + "CreateShopPendingTopupService.java"));
    String attempt = Files.readString(Path.of(DIR + "CreateShopAttemptResolveService.java"));

    assertFalse(topup.contains("block-auto-reorder-started"));
    assertFalse(attempt.contains("block-auto-reorder-started"));
  }

  @Test
  void trackerExpiresOnInactivityNotFiveMinutesAfterCreation() throws Exception {
    String source = Files.readString(Path.of(DIR + "CreateShopPendingDeliveryTracker.java"));

    assertTrue(source.contains("expireAfterAccess(5, TimeUnit.MINUTES)"));
    assertFalse(source.contains("expireAfterWrite("));
  }
}
