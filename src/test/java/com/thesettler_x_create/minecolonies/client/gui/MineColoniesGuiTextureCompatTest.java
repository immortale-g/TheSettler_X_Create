package com.thesettler_x_create.minecolonies.client.gui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.thesettler_x_create.ShopGuiLayouts;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Every texture our windows borrow from another mod has to still be in that mod's jar.
 *
 * <p>The shop's tabs are drawn on MineColonies' own paper and buttons, named by resource location
 * in the layout xml. A texture that disappears upstream is a window with a hole in it - and nothing
 * says so before a player opens the tab, because a missing texture is not an error anywhere in the
 * build.
 *
 * <p>The paths are collected from the layouts themselves instead of being listed here, so a new
 * window, or a new borrowed texture in an existing one, is covered without touching this test.
 */
class MineColoniesGuiTextureCompatTest {
  private static final String OWN_NAMESPACE = "thesettler_x_create";
  private static final Pattern TEXTURE_REFERENCE =
      Pattern.compile("\"([a-z0-9_.-]+):(textures/[a-z0-9_./-]+)\"");

  @Test
  void everyBorrowedTextureIsStillInTheJarItComesFrom() throws Exception {
    Map<String, Path> borrowed = borrowedTextures();

    assertFalse(
        borrowed.isEmpty(),
        "no foreign textures found in any layout - the shop's windows are drawn on MineColonies'"
            + " paper, so either a pattern here or the layouts themselves have changed");

    borrowed.forEach(
        (resource, layout) ->
            assertTrue(
                MineColoniesGuiTextureCompatTest.class.getClassLoader().getResource(resource)
                    != null,
                layout
                    + " draws on "
                    + resource
                    + ", which that mod no longer ships. The window keeps opening in game and"
                    + " simply misses its background."));
  }

  /** Every {@code <modid>:textures/...} path in the layouts, as a classpath resource. */
  private static Map<String, Path> borrowedTextures() throws Exception {
    Map<String, Path> found = new LinkedHashMap<>();
    for (Path layout : ShopGuiLayouts.all()) {
      Matcher matcher = TEXTURE_REFERENCE.matcher(Files.readString(layout));
      while (matcher.find()) {
        String namespace = matcher.group(1);
        if (OWN_NAMESPACE.equals(namespace)) {
          continue;
        }
        found.putIfAbsent("assets/" + namespace + "/" + matcher.group(2), layout);
      }
    }
    return found;
  }
}
