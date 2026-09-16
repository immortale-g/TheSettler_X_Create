package com.thesettler_x_create;

/**
 * The one switch for the mod's debug logging.
 *
 * <p>Guard anything that builds log arguments with {@link #enabled()}; use {@link #info} when the
 * arguments are already at hand, so nothing is computed while debug logging is off.
 */
public final class DebugLog {
  private DebugLog() {}

  /**
   * Whether debug logging is on. False while the config is not loaded yet, which happens early in
   * startup and in unit tests.
   */
  public static boolean enabled() {
    try {
      return Config.DEBUG_LOGGING.getAsBoolean();
    } catch (IllegalStateException notLoaded) {
      return false;
    }
  }

  /** Logs at info level when debug logging is on. */
  public static void info(String message, Object... args) {
    if (enabled()) {
      TheSettlerXCreate.LOGGER.info(message, args);
    }
  }
}
