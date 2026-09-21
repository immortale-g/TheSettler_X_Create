package com.thesettler_x_create.stock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** How item counts are written into a narrow field and read back out of it. */
class AmountTextTest {

  @Test
  void smallCountsAreWrittenOutInFull() {
    assertEquals("1", AmountText.format(1));
    assertEquals("64", AmountText.format(64));
    assertEquals("999", AmountText.format(999));
  }

  @Test
  void thousandsAreShortened() {
    assertEquals("1k", AmountText.format(1000));
    assertEquals("1.5k", AmountText.format(1500));
    assertEquals("12k", AmountText.format(12000));
    assertEquals("12.5k", AmountText.format(12500));
  }

  @Test
  void whatDoesNotFitTheShortFormStaysAsItIs() {
    // 1234 as "1.2k" would drop the 34, and the field is what the value is read back from.
    assertEquals("1234", AmountText.format(1234));
    assertEquals("1050", AmountText.format(1050));
  }

  @Test
  void everyFormatReadsBackAsItself() {
    for (int amount : new int[] {1, 64, 999, 1000, 1500, 1234, 12000, 12500, 64000}) {
      assertEquals(amount, AmountText.parse(AmountText.format(amount)), "round trip of " + amount);
    }
  }

  @Test
  void theRoundTripHoldsAcrossTheWholeRangeThatGetsAbbreviated() {
    // Nine hand-picked values all happened to be ones a double could scale back exactly. 16,100
    // was not: "16.1k" came back as -1, and so did 783 further amounts below five million, among
    // them what the picker pre-fills its field with for a shop that keeps that many.
    for (int amount = 1000; amount <= 5_000_000; amount += 100) {
      assertEquals(amount, AmountText.parse(AmountText.format(amount)), "round trip of " + amount);
    }
  }

  @Test
  void aShorthandFinerThanTheFormatWritesIsStillRead() {
    assertEquals(1005, AmountText.parse("1.005k"));
    assertEquals(16100, AmountText.parse("16.1k"));
  }

  @Test
  void aFractionOfAnItemIsRefused() {
    assertEquals(-1, AmountText.parse("1.0005k"));
    assertEquals(-1, AmountText.parse("0.5"));
  }

  @Test
  void moreThanACountCanHoldIsRefused() {
    assertEquals(-1, AmountText.parse("99999999k"));
    assertEquals(-1, AmountText.parse("99999999999"));
  }

  @Test
  void shorthandIsAccepted() {
    assertEquals(1000, AmountText.parse("1k"));
    assertEquals(1000, AmountText.parse("1K"));
    assertEquals(2500, AmountText.parse("2.5k"));
    assertEquals(2500, AmountText.parse("2,5k"));
    assertEquals(5000, AmountText.parse(" 5k "));
    assertEquals(640, AmountText.parse("640"));
  }

  @Test
  void nonsenseIsRejectedRatherThanGuessed() {
    // -1 lets the caller keep the value it had. Falling back to some number would overwrite what
    // the player typed with something he never asked for.
    for (String text : new String[] {"", "   ", "k", "abc", "1.5", "12x", "1..5k", "-3", null}) {
      assertEquals(-1, AmountText.parse(text), "should be rejected: " + text);
    }
  }

  @Test
  void whatMayBeTypedIntoAnAmountFieldIsWhatCanBeRead() {
    for (char digit = '0'; digit <= '9'; digit++) {
      assertTrue(AmountText.isTypable(digit), "digit " + digit);
    }
    // The zero above is the whole point: Structurize' field ran every key press through a filter
    // that answered a "0" with nothing, so ten could not be typed anywhere in the game, only
    // eleven.
    for (char shorthand : new char[] {'.', ',', 'k', 'K'}) {
      assertTrue(AmountText.isTypable(shorthand), "shorthand " + shorthand);
    }
    for (char rejected : new char[] {'a', '-', ' ', '/', 'm'}) {
      assertFalse(AmountText.isTypable(rejected), "should not be typable: " + rejected);
    }
  }
}
