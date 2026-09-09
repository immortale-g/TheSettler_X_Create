package com.thesettler_x_create.create.compat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CreatePlacementHandlersGuardTest {

  @Test
  void registersRemapAndBeltHandlersFromCommonSetup() throws Exception {
    String registration =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/create/compat/CreatePlacementHandlers.java"));

    assertTrue(registration.contains("PlacementHandlers.add(new CompositeBlockItemRemapHandler("));
    assertTrue(registration.contains("PlacementHandlers.add(new CreateBeltPlacementHandler());"));

    // Every composite kinetic casing that has no item of its own must map to its real component.
    assertTrue(registration.contains("create(\"andesite_encased_shaft\"), create(\"shaft\")"));
    assertTrue(registration.contains("create(\"brass_encased_shaft\"), create(\"shaft\")"));
    assertTrue(
        registration.contains("create(\"andesite_encased_cogwheel\"), create(\"cogwheel\")"));
    assertTrue(registration.contains("create(\"brass_encased_cogwheel\"), create(\"cogwheel\")"));
    assertTrue(
        registration.contains(
            "create(\"andesite_encased_large_cogwheel\"), create(\"large_cogwheel\")"));
    assertTrue(
        registration.contains(
            "create(\"brass_encased_large_cogwheel\"), create(\"large_cogwheel\")"));

    String modEntry =
        Files.readString(Path.of("src/main/java/com/thesettler_x_create/TheSettlerXCreate.java"));
    assertTrue(modEntry.contains("CreatePlacementHandlers.register();"));
  }

  @Test
  void beltHandlerBuffersSegmentsUntilFullRunIsKnown() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/create/compat/CreateBeltPlacementHandler.java"));

    // The core correctness property: a belt run must never be committed to the world (or its
    // resource cost reported) before every segment referencing the same Controller has arrived.
    assertTrue(source.contains("if (length <= 0 || segments.size() < length) {"));
    assertTrue(source.contains("if (length <= 0 || knownSegments.size() < length) {"));
    assertTrue(source.contains("placedSoFar.forEach(pos -> level.removeBlock(pos, false));"));
    assertTrue(source.contains("PlacementHandlers.handleTileEntityPlacement("));
    assertTrue(source.contains("segment.tileEntityData(), level, segment.pos(), rotationMirror);"));
    assertFalse(source.contains("import com.simibubi.create.AllBlocks;"));

    // Confirmed against a real in-game scan: Create's BeltBlockEntity serializes Controller as
    // an int-array tag (e.g. [I;x,y,z]), not a compound with X/Y/Z sub-tags.
    assertTrue(source.contains("tileEntityData.getIntArray(\"Controller\")"));
    assertFalse(source.contains("getCompound(\"Controller\")"));
  }
}
