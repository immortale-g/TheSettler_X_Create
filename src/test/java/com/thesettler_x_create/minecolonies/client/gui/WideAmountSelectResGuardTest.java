package com.thesettler_x_create.minecolonies.client.gui;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * A Create network minimum runs into the thousands, and Structurize' picker stops that from being
 * typed twice over: its amount field carries ONLY_POSITIVE_NUMBERS_MAX1k, which refuses a keystroke
 * that would take the value past a thousand, and the field is thirty pixels wide, where BlockUI
 * stops typing at the width of the field. Widening alone changes nothing while the cap sits on top.
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
  void theFieldIsWideEnoughToTypeInto() throws Exception {
    String layout =
        Files.readString(
            Path.of("src/main/resources/assets/thesettler_x_create/gui/layoutselectres_wide.xml"));

    // Structurize' own layout has size="30 18" here, which fits three digits.
    assertTrue(layout.contains("<input id=\"count\" size=\"72 18\""), "count field must stay wide");
  }
}
