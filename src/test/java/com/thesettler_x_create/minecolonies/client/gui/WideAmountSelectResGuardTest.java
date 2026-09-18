package com.thesettler_x_create.minecolonies.client.gui;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Structurize' amount field cannot be typed into properly, and a Create network minimum needs five
 * digits.
 *
 * <p>{@code TextField.writeText} hands the filter the typed character on its own, and {@code
 * ONLY_POSITIVE_NUMBERS_MAX1k} answers a "0" with an empty string, since what it parses is not
 * positive. A zero therefore never arrives, at any cursor position, and 1 to 9 are all that can be
 * typed. On top of that the filter caps at 999 and the field is thirty pixels wide, where BlockUI
 * stops typing at the width of the field.
 *
 * <p>MineColonies' warehouse minimum uses the same field and has the same hole, which is how this
 * was finally pinned down in game on 2026-09-18: 10 could not be entered there either, only 11.
 */
class WideAmountSelectResGuardTest {

  @Test
  void theThousandCapIsReplacedAfterStructurizeSetsIt() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/client/gui/WideAmountSelectRes.java"));

    // The cap arrives with the confirm step, so it has to be replaced after that call, not before.
    int superCall = source.indexOf("super.secondaryConfirm(button);");
    int setFilter = source.indexOf("setFilter(InputFilters.ONLY_NUMBERS)");
    assertTrue(superCall > 0, "the confirm step must still run");
    assertTrue(setFilter > superCall, "the filter has to be replaced after Structurize sets it");
  }

  @Test
  void theCursorStartsBehindWhatIsAlreadyInTheField() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/client/gui/WideAmountSelectRes.java"));

    // Not what kept the zero out, but typing in front of the value that is already there reads
    // backwards all the same.
    assertTrue(source.contains("setCursorPosition(count.getText().length())"));
  }

  @Test
  void theFieldIsWideEnoughToTypeInto() throws Exception {
    String layout =
        Files.readString(
            Path.of("src/main/resources/assets/thesettler_x_create/gui/layoutselectres_wide.xml"));

    // Structurize' own layout has size="30 18" here, which fits three digits.
    assertTrue(layout.contains("<input id=\"count\" size=\"72 18\""), "count field must stay wide");
  }
}
