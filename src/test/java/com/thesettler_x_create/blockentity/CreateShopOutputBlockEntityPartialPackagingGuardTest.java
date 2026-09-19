package com.thesettler_x_create.blockentity;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * A gauge order ships in parts, and the part that goes out has to be booked against the order.
 *
 * <p>This replaces the all-or-nothing rule from seam-audit finding s1-6. That rule made a real pull
 * return nothing whenever the racks held less than the whole order, while the preview still showed
 * a package of what was there. Create ships what the preview shows and asks again, so the same
 * goods left the shop every one and a half seconds: 648 torches out of 12 in one in-game test on
 * 2026-09-18.
 *
 * <p>What keeps that from coming back is not the rule that was removed but these two properties:
 * preview and real pull report the same amount, and a real pull books what it shipped. Then the
 * next look finds the racks empty and the order smaller.
 */
class CreateShopOutputBlockEntityPartialPackagingGuardTest {
  private static final Path SOURCE =
      Path.of("src/main/java/com/thesettler_x_create/blockentity/CreateShopOutputBlockEntity.java");

  @Test
  void whatWasPackagedIsBookedAgainstTheGaugeTask() throws Exception {
    String body = methodBody("private ItemStack assemblePackage(");

    assertTrue(
        body.contains("building.deliverPartOfGaugeTask(task.requestId(), extracted.getCount())"));
    // Completing the whole task on a partial package is what lost the shortfall before.
    assertFalse(body.contains("completeNextGaugeTask"));
  }

  @Test
  void theSimulatedPullReportsTheSameAmountAsTheRealOne() throws Exception {
    String body = methodBody("private ItemStack extractFromRacks(");

    // Any branch that changes the returned amount for one of the two modes reopens the loop: the
    // packager would ship a preview the real pull never hands out, or hand out more than shown.
    assertFalse(body.contains("!simulate && remaining"));
    assertFalse(body.contains("rollBack("));
    // simulate stays the flag passed down to the racks, and nothing else.
    assertTrue(body.contains("handler.extractItem(slot, remaining, simulate)"));
  }

  private static String methodBody(String signature) throws Exception {
    String source = Files.readString(SOURCE);
    int start = source.indexOf(signature);
    assertTrue(start > 0, signature + " not found");
    int end = source.indexOf("\n    private ", start + signature.length());
    return source.substring(start, end > 0 ? end : source.length());
  }
}
