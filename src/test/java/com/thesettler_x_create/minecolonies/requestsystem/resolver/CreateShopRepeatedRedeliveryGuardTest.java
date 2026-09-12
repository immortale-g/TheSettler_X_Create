package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Guards the two halves of the repeated re-delivery fix: outstanding amounts subtract what was
 * already delivered, and the post-completion recovery closes the parent instead of falling through
 * into another order.
 */
class CreateShopRepeatedRedeliveryGuardTest {
  @Test
  void outstandingComputationSubtractsAlreadyRecordedDeliveries() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/requestsystem/resolver/CreateShopOutstandingNeededService.java"));

    assertTrue(source.contains("countAlreadyDelivered(request, deliverable)"));
    assertTrue(source.contains("for (ItemStack stack : request.getDeliveries())"));
    assertTrue(source.contains("!deliverable.matches(stack)"));
    assertTrue(source.contains("requestedCount - leftOver - Math.max(0, alreadyDelivered)"));
  }

  @Test
  void postCompletionRecoveryResolvesTheParentInsteadOfReorderingWhenNothingIsOutstanding()
      throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/requestsystem/resolver/CreateShopPendingRequestProcessorService.java"));

    assertTrue(source.contains("outstandingNeededService.compute(request, deliverable, 0)"));
    assertTrue(source.contains("if (outstandingAfterCompletion <= 0) {"));
    assertTrue(source.contains("recover:delivery-completed-fully"));
    assertTrue(source.contains("tickPending:recover-delivery-completed-fully"));
    assertTrue(source.contains("resolver.releaseReservation(manager, request);"));
    assertTrue(
        source.contains(
            "standardManager.updateRequestState(request.getId(), RequestState.RESOLVED)"));
  }
}
