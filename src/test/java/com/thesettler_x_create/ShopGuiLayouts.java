package com.thesettler_x_create;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * The mod's BlockUI layout files, found rather than listed, so a new window is checked by every
 * layout test from the day it is added.
 */
public final class ShopGuiLayouts {
  private static final Path GUI_ROOT = Path.of("src/main/resources/assets/thesettler_x_create/gui");

  private ShopGuiLayouts() {}

  public static List<Path> all() throws IOException {
    try (Stream<Path> files = Files.walk(GUI_ROOT)) {
      List<Path> layouts =
          files
              .filter(path -> path.getFileName().toString().endsWith(".xml"))
              .sorted(Comparator.comparing(Path::toString))
              .toList();
      assertFalse(
          layouts.isEmpty(), "no layout files under " + GUI_ROOT + ", has the folder moved?");
      return layouts;
    }
  }
}
