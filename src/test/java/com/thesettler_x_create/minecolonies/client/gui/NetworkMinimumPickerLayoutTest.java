package com.thesettler_x_create.minecolonies.client.gui;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * A window and its layout only meet at run time: {@code findPaneOfTypeByID} answers with null and
 * {@code registerButton} with silence when an id is not in the xml, so a renamed pane shows up as a
 * button that does nothing, in game, and nowhere else.
 *
 * <p>These windows are the way a Create network minimum is set, and they were written because
 * Structurize' own picker cannot take a zero or anything past a thousand.
 */
class NetworkMinimumPickerLayoutTest {
  private static final Pattern LOOKED_UP =
      Pattern.compile("(?:findPaneOfTypeByID|registerButton)\\(\\s*\"([^\"]+)\"");

  @Test
  void theAddPageFindsEveryPaneItAsksFor() throws Exception {
    assertEveryIdIsInTheLayout(
        "src/main/java/com/thesettler_x_create/minecolonies/client/gui/CreateShopAddMinimumWindow.java",
        "src/main/resources/assets/thesettler_x_create/gui/layoutcreateshop_addminimum.xml");
  }

  @Test
  void theTabFindsEveryPaneItAsksFor() throws Exception {
    assertEveryIdIsInTheLayout(
        "src/main/java/com/thesettler_x_create/minecolonies/client/gui/CreateShopNetworkMinimumModuleWindow.java",
        "src/main/resources/assets/thesettler_x_create/gui/layouthuts/layoutcreateshop_networkminimum.xml");
  }

  private static void assertEveryIdIsInTheLayout(String javaFile, String layoutFile)
      throws Exception {
    String source = Files.readString(Path.of(javaFile));
    String layout = Files.readString(Path.of(layoutFile));

    Set<String> ids = new LinkedHashSet<>();
    Matcher matcher = LOOKED_UP.matcher(source);
    while (matcher.find()) {
      ids.add(matcher.group(1));
    }
    assertTrue(ids.size() > 1, "no pane ids found in " + javaFile + ", has it been renamed?");

    for (String id : ids) {
      assertTrue(
          layout.contains("id=\"" + id + "\""),
          "the window looks for a pane called " + id + ", which " + layoutFile + " does not have");
    }
  }
}
