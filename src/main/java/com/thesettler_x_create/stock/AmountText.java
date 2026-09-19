package com.thesettler_x_create.stock;

/**
 * Item counts as a player reads and types them. A Create network holds thousands of an item, and a
 * five digit number in a list column is both wide and hard to read, so a thousand is written and
 * accepted as {@code 1k}.
 *
 * <p>Reading and writing are the same mapping in both directions: what {@link #format} produces,
 * {@link #parse} turns back into the same number. A field can therefore show its own value without
 * the round trip changing it.
 *
 * <p>Knows nothing about Minecraft.
 */
public final class AmountText {
  private static final int THOUSAND = 1000;

  private AmountText() {}

  /** {@code 999} stays 999, {@code 1000} becomes 1k, {@code 1500} becomes 1.5k. */
  public static String format(int amount) {
    if (amount < THOUSAND) {
      return Integer.toString(amount);
    }
    int thousands = amount / THOUSAND;
    int rest = amount % THOUSAND;
    if (rest == 0) {
      return thousands + "k";
    }
    // One decimal is enough to stay short; anything finer is written out in full so no digit is
    // lost on the way to the field and back.
    if (rest % 100 == 0) {
      return thousands + "." + (rest / 100) + "k";
    }
    return Integer.toString(amount);
  }

  /**
   * Whether this character may be typed into an amount field: a digit, a decimal separator, or the
   * {@code k} that stands for a thousand. Everything else is turned away at the key press, so the
   * field only ever holds something {@link #parse} has a chance with.
   *
   * <p>Nothing is judged here beyond the single character. A field reading {@code 1..5k} is refused
   * by {@link #parse}, not by this; a filter that looks at one key press cannot know what the rest
   * of the field says, and one that swallows keys leaves the player typing into a field that
   * silently drops what he types.
   */
  public static boolean isTypable(char character) {
    return Character.isDigit(character)
        || character == '.'
        || character == ','
        || character == 'k'
        || character == 'K';
  }

  /**
   * The number behind what was typed, or {@code -1} when that is not a number: empty, a stray
   * letter, a second dot, a negative, a fraction of an item, or more than a count holds. Nothing is
   * guessed and nothing falls back to a default, so a caller can leave the value alone instead of
   * replacing it with something the player never asked for.
   */
  public static int parse(String text) {
    if (text == null) {
      return -1;
    }
    String cleaned = text.trim().toLowerCase(java.util.Locale.ROOT).replace(",", ".");
    if (cleaned.isEmpty()) {
      return -1;
    }
    boolean thousands = cleaned.endsWith("k");
    if (thousands) {
      cleaned = cleaned.substring(0, cleaned.length() - 1).trim();
      if (cleaned.isEmpty()) {
        return -1;
      }
    }
    if (!thousands && cleaned.contains(".")) {
      // "1.5" without a k is half an item, which does not exist.
      return -1;
    }
    java.math.BigDecimal value;
    try {
      value = new java.math.BigDecimal(cleaned);
    } catch (NumberFormatException notANumber) {
      return -1;
    }
    // Decimal, not binary. A double turns 16.1 into a hair more than 16.1, so 16.1k times a
    // thousand missed being whole by a rounding step and was refused - one of every sixty-odd
    // values format itself writes, among them what the picker pre-fills a field with.
    java.math.BigDecimal scaled =
        thousands ? value.multiply(java.math.BigDecimal.valueOf(THOUSAND)) : value;
    if (scaled.signum() < 0) {
      return -1;
    }
    try {
      return scaled.intValueExact();
    } catch (ArithmeticException notAWholeNumberOfItems) {
      // A fraction of an item, or more items than a count can hold.
      return -1;
    }
  }
}
