package com.thesettler_x_create.minecolonies.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;

/**
 * Real behavioral test - not a source-text guard - for the {@code reset_live_state} summary line.
 *
 * <p>Both command variants report through {@link ResetLiveStateResult#toSummaryMessage(boolean)}
 * (Clean Code Audit a4-3). This pins two things the previous hand-built copies could not guarantee:
 * every declared tally field actually appears in the rendered message, and the two variants differ
 * only in their headline label.
 *
 * <p>Most of the command layer cannot be tested this way because it needs a bootstrapped Minecraft
 * registry (see the {@code *GuardTest} files in this package). This class can, because it holds
 * nothing but ints.
 */
class ResetLiveStateResultSummaryTest {

  private static ResetLiveStateResult numbered() {
    ResetLiveStateResult result = new ResetLiveStateResult();
    int value = 1;
    for (Field field : ResetLiveStateResult.class.getDeclaredFields()) {
      if (field.getType() == int.class) {
        try {
          field.setInt(result, value++);
        } catch (IllegalAccessException ex) {
          throw new AssertionError(ex);
        }
      }
    }
    return result;
  }

  @Test
  void everyTallyFieldIsReported() {
    ResetLiveStateResult result = numbered();
    String message = result.toSummaryMessage(false);

    for (Field field : ResetLiveStateResult.class.getDeclaredFields()) {
      if (field.getType() != int.class) {
        continue;
      }
      int value;
      try {
        value = field.getInt(result);
      } catch (IllegalAccessException ex) {
        throw new AssertionError(ex);
      }
      assertTrue(
          message.contains(field.getName() + "=" + value),
          "summary is missing tally field '"
              + field.getName()
              + "' - add it to toSummaryMessage(): "
              + message);
    }
  }

  @Test
  void bothVariantsDifferOnlyInTheHeadlineLabel() {
    ResetLiveStateResult result = numbered();

    assertEquals(
        result
            .toSummaryMessage(false)
            .replace("Live state reset:", "Live state reset (force queue):"),
        result.toSummaryMessage(true));
  }

  @Test
  void headlineNamesTheForcedQueueVariant() {
    ResetLiveStateResult result = new ResetLiveStateResult();

    assertTrue(result.toSummaryMessage(false).startsWith("[CreateShop] Live state reset: "));
    assertTrue(
        result.toSummaryMessage(true).startsWith("[CreateShop] Live state reset (force queue): "));
  }
}
