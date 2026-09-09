package com.thesettler_x_create;

/** Small text helpers shared across chat interactions and inflight bookkeeping. */
public final class TextUtil {
  private TextUtil() {}

  /** Trims a possibly-null string, returning {@code ""} for null or blank input. */
  public static String sanitize(String value) {
    if (value == null) {
      return "";
    }
    return value.trim();
  }
}
