package com.thesettler_x_create.minecolonies.ai;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * How the shopkeeper chooses between its two carrying jobs.
 *
 * <p>Clearing the arrival rack comes before moving surplus into the hut buffer, and unlike that
 * move it is not held back while a courier gathers a delivery. The gate exists so the shopkeeper
 * does not take goods out of the rack pool at that moment; carrying a stack from one rack to
 * another takes nothing out of it, and holding it back would shut the inbound rack exactly when the
 * shop is busiest.
 */
class ShopkeeperArrivalRackCarryGuardTest {
  private static final Path AI =
      Path.of("src/main/java/com/thesettler_x_create/minecolonies/ai/EntityAIWorkCreateShop.java");
  private static final Path BUILDING =
      Path.of(
          "src/main/java/com/thesettler_x_create/minecolonies/building/BuildingCreateShop.java");

  @Test
  void theArrivalRackIsAskedFirst() throws Exception {
    String body = methodBody(AI, "private boolean pickNextHousekeepingJob(");
    int arrival = body.indexOf("tile.findNextArrivalRackItem()");
    int toHut = body.indexOf("tile.findNextUnreservedRackItem(pickup)");

    assertTrue(arrival > 0 && toHut > 0, "both jobs must still exist");
    assertTrue(arrival < toHut, "a tight arrival rack blocks every delivery, so it goes first");
  }

  @Test
  void theDeliveryGateOnlyHoldsBackTheMoveIntoTheHut() throws Exception {
    String picker = methodBody(AI, "private boolean pickNextHousekeepingJob(");
    int arrival = picker.indexOf("tile.findNextArrivalRackItem()");
    int gate = picker.indexOf("building.isHousekeepingAllowed()");

    assertTrue(gate > arrival, "the arrival rack must be cleared even while a courier gathers");

    String building = Files.readString(BUILDING);
    assertTrue(
        building.contains(
            "return tile.hasArrivalRackWork() || (isHousekeepingAllowed() && hasIncomingRackWork());"),
        "the state entry has to agree with the picker, or the shopkeeper walks in and turns around");
  }

  @Test
  void carryingBetweenRacksIgnoresReservations() throws Exception {
    String body = methodBody(AI, "private IAIState housekeepingFetch()");

    // Reservations name an item and an amount, never a rack, so reserved stock may travel too.
    // Passing the pickup would apply the unreserved budget and leave a full rack full.
    assertTrue(body.contains("pendingArrivalRackPos == null ? pickup : null"));
  }

  @Test
  void aCarriedStackIsNeverLeftInTheHand() throws Exception {
    String body = methodBody(AI, "private void depositIntoAnotherRack(");

    assertTrue(body.contains("tile.insertIntoOtherRacks(pendingArrivalRackPos"));
    assertTrue(
        body.contains("tile.insertIntoRacks(leftovers);"),
        "if every other rack filled up meanwhile it goes back, rather than riding along in a hand");
    assertTrue(body.contains("pendingCarriedItem = null;"));
  }

  private static String methodBody(Path source, String signature) throws Exception {
    String text = Files.readString(source);
    int start = text.indexOf(signature);
    assertTrue(start > 0, signature + " not found");
    int end = text.indexOf("\n  private ", start + signature.length());
    return text.substring(start, end > 0 ? end : text.length());
  }
}
