package com.thesettler_x_create.create.compat;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.thesettler_x_create.CompiledClassFacts;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Pins the two tags {@link CreateBeltPlacementHandler} reads out of a belt's blueprint NBT.
 *
 * <p>{@code Length} decides when a belt run is complete and may be committed to the world, {@code
 * Controller} groups the segments of one run. Both are read by name from a compound tag, so a
 * rename in Create answers with a zero length and an empty int array instead of an error: the
 * handler would then buffer every segment forever and the builder would never finish a belt.
 *
 * <p>Read from Create's compiled belt block entity, which is what writes those tags, so the nightly
 * compat run against a new Create release is where this shows up.
 */
class CreateBeltNbtCompatTest {
  private static final String BELT_BLOCK_ENTITY =
      "com/simibubi/create/content/kinetics/belt/BeltBlockEntity";
  private static final Path HANDLER =
      Path.of(
          "src/main/java/com/thesettler_x_create/create/compat/CreateBeltPlacementHandler.java");

  @Test
  void createStillWritesTheBeltTagsTheHandlerReads() throws Exception {
    Set<String> constants = CompiledClassFacts.stringConstantsOf(BELT_BLOCK_ENTITY);

    assertTrue(
        constants.contains("Length"),
        BELT_BLOCK_ENTITY
            + " no longer mentions the NBT tag Length. The belt placement handler reads the run's"
            + " length from it to know when every segment has arrived; without it no belt is ever"
            + " committed to the world.");
    assertTrue(
        constants.contains("Controller"),
        BELT_BLOCK_ENTITY
            + " no longer mentions the NBT tag Controller. The belt placement handler groups the"
            + " segments of one run by it; without it every segment gets a buffer of its own.");
  }

  @Test
  void theHandlerStillReadsExactlyThoseTwoTags() throws Exception {
    String source = Files.readString(HANDLER);

    // Keeps the test honest: it only means something as long as these are the names the handler
    // actually asks for.
    assertTrue(source.contains("tileEntityData.getInt(\"Length\")"), "the handler renamed Length");
    assertTrue(
        source.contains("tileEntityData.getIntArray(\"Controller\")"),
        "the handler renamed Controller");
  }
}
