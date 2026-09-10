package com.thesettler_x_create.create.compat;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Seam-audit finding s2-2: the belt-run buffer used to be keyed only by the blueprint's {@code
 * Controller} position - a value that is blueprint-local, not translated to world coordinates,
 * since Structurize has no notion of it being a position field for a foreign mod's custom tile
 * data. Two builds of the same blueprint (a second colony on the server, or the same colony
 * rebuilding after an abandoned/cancelled first attempt) therefore produced an identical key, so
 * one build's segments could leak into another's buffer. Fixed by widening the key to include the
 * dimension and the colony resolved at each segment's own position, and by discarding a buffer
 * that's been open longer than {@code Config.BELT_PLACEMENT_BUFFER_TTL_TICKS} before reuse.
 */
class CreateBeltPlacementHandlerCollisionGuardTest {

  @Test
  void bufferKeyIncludesDimensionAndColonyNotJustControllerPos() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/create/compat/CreateBeltPlacementHandler.java"));

    int keyRecord = source.indexOf("private record ControllerKey(");
    assertTrue(keyRecord > 0);
    String recordDecl = source.substring(keyRecord, Math.min(source.length(), keyRecord + 200));
    assertTrue(recordDecl.contains("ResourceKey<Level> dimension"));
    assertTrue(recordDecl.contains("int colonyId"));
    assertTrue(recordDecl.contains("BlockPos controllerPos"));

    assertTrue(
        source.contains(
            "IColony colony = IColonyManager.getInstance().getColonyByPosFromWorld(level,"
                + " segmentPos);"));

    // The run's buffered items, segments and start time must all be keyed by the widened key, not
    // raw BlockPos - bundled into one PendingBeltRun per key so they can't drift out of sync.
    assertTrue(source.contains("Map<ControllerKey, PendingBeltRun> pendingRunsByController"));
    assertTrue(source.contains("Map<BlockPos, List<ItemStack>> itemsBySegment"));
    assertTrue(source.contains("TreeMap<BlockPos, BeltSegment> segments"));
    assertTrue(source.contains("long startedAt"));
  }

  @Test
  void staleBufferIsDiscardedBeforeReuse() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/create/compat/CreateBeltPlacementHandler.java"));

    int method = source.indexOf("private PendingBeltRun markBufferTouched(");
    assertTrue(method > 0);
    String body = source.substring(method, Math.min(source.length(), method + 900));

    assertTrue(body.contains("Config.BELT_PLACEMENT_BUFFER_TTL_TICKS.get()"));
    assertTrue(body.contains("clearBuffers(key)"));
  }

  @Test
  void configDefinesBeltPlacementBufferTtl() throws Exception {
    String configSource =
        Files.readString(Path.of("src/main/java/com/thesettler_x_create/Config.java"));

    assertTrue(configSource.contains("BELT_PLACEMENT_BUFFER_TTL_TICKS"));
    assertTrue(configSource.contains("\"beltPlacementBufferTtlTicks\""));
  }
}
