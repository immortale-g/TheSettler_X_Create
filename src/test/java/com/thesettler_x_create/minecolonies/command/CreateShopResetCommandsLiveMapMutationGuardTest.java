package com.thesettler_x_create.minecolonies.command;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Seam-audit finding s3-2: {@code reconcileAssignmentsAndKickCouriers} and {@code
 * clearWarehouseQueues} used to prune MineColonies' own live assignment/queue collections with a
 * raw {@code Iterator} while also calling MineColonies APIs ({@code standard.assignRequest}, {@code
 * standard.updateRequestState}) that are proven (via DeliverymenRequestResolver and
 * addRequestToResolver) to mutate those exact same collections from underneath us - a
 * ConcurrentModificationException / silent-corruption risk. Fixed by adopting the defensive-copy
 * pattern {@code cancelActiveLocalDeliveries} in this same file already used: snapshot with {@code
 * List.copyOf(...)} before iterating, mutate the live collection directly (never via a live
 * iterator) only for entries in the snapshot.
 */
class CreateShopResetCommandsLiveMapMutationGuardTest {

  private static final String[] RESET_COMMAND_FAMILY_FILES = {
    "src/main/java/com/thesettler_x_create/minecolonies/command/CreateShopResetCommands.java",
    "src/main/java/com/thesettler_x_create/minecolonies/command/CreateShopAssignmentReconciler.java",
    "src/main/java/com/thesettler_x_create/minecolonies/command/CreateShopWarehouseQueuePruner.java",
    "src/main/java/com/thesettler_x_create/minecolonies/command/CreateShopRequestGraphCanceller.java",
    "src/main/java/com/thesettler_x_create/minecolonies/command/CreateShopLiveDeliveryDrainer.java",
  };

  @Test
  void noRawIteratorRemoveOnLiveMineColoniesCollections() throws Exception {
    for (String path : RESET_COMMAND_FAMILY_FILES) {
      String source = Files.readString(Path.of(path));
      assertFalse(
          source.contains("iterator.remove()"),
          "expected no raw Iterator.remove() usage left in " + path);
    }
  }

  @Test
  void reconcileAssignmentsIteratesADefensiveSnapshot() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/command/CreateShopAssignmentReconciler.java"));

    int method = source.indexOf("static void reconcileAssignmentsAndKickCouriers(");
    assertTrue(method > 0);
    String body = source.substring(method, Math.min(source.length(), method + 2200));

    assertTrue(body.contains("java.util.List.copyOf(store.getAssignments().entrySet())"));
    assertTrue(body.contains("for (IToken<?> token : java.util.List.copyOf(assigned))"));
    assertTrue(body.contains("assigned.remove(token)"));
  }

  @Test
  void clearWarehouseQueuesIteratesADefensiveSnapshot() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/command/CreateShopWarehouseQueuePruner.java"));

    int method = source.indexOf("static void clearWarehouseQueues(");
    assertTrue(method > 0);
    String body = source.substring(method, Math.min(source.length(), method + 3600));

    assertTrue(body.contains("for (IToken<?> queuedToken : java.util.List.copyOf(liveQueue))"));
    assertTrue(body.contains("liveQueue.remove(queuedToken)"));
  }
}
