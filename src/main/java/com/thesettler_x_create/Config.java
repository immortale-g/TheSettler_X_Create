package com.thesettler_x_create;

import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

public class Config {
  private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

  public static final ModConfigSpec.BooleanValue DEBUG_LOGGING =
      BUILDER
          .comment("Enable extra debug logging for TheSettler_x_Create")
          .define("debugLogging", true);

  public static final ModConfigSpec.IntValue GAUGE_MIN_BUILDING_LEVEL =
      BUILDER
          .comment("Minimum Create Shop level required to order for a Colony Factory Gauge.")
          .defineInRange("gaugeMinBuildingLevel", 2, 1, 5);

  public static final ModConfigSpec.LongValue MISSING_NETWORK_WARNING_COOLDOWN =
      BUILDER
          .comment("Cooldown (ticks) between missing network warning messages.")
          .defineInRange("missingNetworkWarningCooldown", 6000L, 20L, 24000L);

  public static final ModConfigSpec.LongValue RACK_FULL_WARNING_COOLDOWN =
      BUILDER
          .comment("Cooldown (ticks) between rack-full warning messages.")
          .defineInRange("rackFullWarningCooldown", 6000L, 20L, 24000L);

  public static final ModConfigSpec.LongValue HOUSEKEEPING_MIN_AGE_TICKS =
      BUILDER
          .comment(
              "Ticks unreserved stock must sit in the Create Shop racks before the shopkeeper",
              "moves it to the hut for a warehouse pickup.")
          .defineInRange("housekeepingMinAgeTicks", 20L * 60L * 5L, 0L, 72000L);

  public static final ModConfigSpec.LongValue COURIER_DEBUG_COOLDOWN =
      BUILDER
          .comment("Cooldown (ticks) between courier debug logs.")
          .defineInRange("courierDebugCooldown", 200L, 0L, 24000L);

  public static final ModConfigSpec.LongValue COURIER_ENTITY_DEBUG_COOLDOWN =
      BUILDER
          .comment("Cooldown (ticks) between courier entity debug logs.")
          .defineInRange("courierEntityDebugCooldown", 400L, 0L, 24000L);

  public static final ModConfigSpec.LongValue ORDER_TTL_TICKS =
      BUILDER
          .comment("Cooldown (ticks) for avoiding duplicate Create Shop request ordering.")
          .defineInRange("orderTtlTicks", 20L * 60L * 5L, 20L, 24000L);

  public static final ModConfigSpec.LongValue INFLIGHT_TIMEOUT_TICKS =
      BUILDER
          .comment("Ticks before a Create Shop network request is considered overdue.")
          .defineInRange("inflightTimeoutTicks", 20L * 60L * 5L, 20L, 24000L);

  public static final ModConfigSpec.LongValue INFLIGHT_CHECK_INTERVAL_TICKS =
      BUILDER
          .comment("Ticks between Create Shop inflight tracking checks.")
          .defineInRange("inflightCheckIntervalTicks", 100L, 20L, 24000L);

  public static final ModConfigSpec.LongValue INFLIGHT_LOG_COOLDOWN =
      BUILDER
          .comment("Cooldown (ticks) between inflight overdue logs.")
          .defineInRange("inflightLogCooldown", 200L, 0L, 24000L);

  public static final ModConfigSpec.LongValue PENDING_NOTICE_COOLDOWN =
      BUILDER
          .comment("Cooldown (ticks) for pending delivery notices.")
          .defineInRange("pendingNoticeCooldown", 20L * 10L, 20L, 24000L);

  public static final ModConfigSpec.LongValue TICK_PENDING_DEBUG_COOLDOWN =
      BUILDER
          .comment("Cooldown (ticks) between pending delivery debug logs.")
          .defineInRange("tickPendingDebugCooldown", 200L, 0L, 24000L);

  public static final ModConfigSpec.BooleanValue CHAT_MESSAGES_ENABLED =
      BUILDER.comment("Enable Create Shop chat messages.").define("chatMessagesEnabled", true);

  public static final ModConfigSpec.BooleanValue FLOW_CHAT_MESSAGES_ENABLED =
      BUILDER
          .comment("Enable detailed Create Shop flow-step chat messages.")
          .define("flowChatMessagesEnabled", false);

  public static final ModConfigSpec.LongValue PERF_LOG_COOLDOWN =
      BUILDER
          .comment("Cooldown (ticks) between performance timing summaries.")
          .defineInRange("perfLogCooldown", 200L, 0L, 24000L);

  public static final ModConfigSpec.LongValue BELT_PLACEMENT_BUFFER_TTL_TICKS =
      BUILDER
          .comment(
              "Ticks before a buffered-but-incomplete Create belt placement (waiting on the rest"
                  + " of its run) is considered abandoned and discarded instead of risking a"
                  + " collision with a later, unrelated belt run that resolves to the same"
                  + " buffer key.")
          .defineInRange("beltPlacementBufferTtlTicks", 20L * 60L * 5L, 20L, 24000L);

  public static final ModConfigSpec.BooleanValue ENABLE_DEV_TEST_COMMANDS =
      BUILDER
          .comment(
              "Enable the /thesettlerxcreate dev/test-harness commands (run_live_test,"
                  + " auto_test_harness*, diag_*, test_output_packaging). These create fake"
                  + " requests and inflight data in live colonies and are meant for development"
                  + " servers only, so they default to disabled even for operators.")
          .define("enableDevTestCommands", false);

  static final ModConfigSpec SPEC = BUILDER.build();

  /** What the setting was called while it gated perma requests, which 0.6.0 removed. */
  private static final String FORMER_GAUGE_LEVEL_KEY = "permaMinBuildingLevel";

  /**
   * Carries a hand-edited {@code permaMinBuildingLevel} over to {@code gaugeMinBuildingLevel} the
   * first time the new config is loaded.
   *
   * <p>The two settings mean the same thing: the shop level from which a Colony Factory Gauge may
   * order. Renaming it dropped the old line on load and started from the default, so a player who
   * had lowered it to 1 to let a level-1 shop serve gauges was silently back at 2, with nothing but
   * a debug line saying the building was too low.
   *
   * <p>Only a value that differs from the default is carried over, and only while the new setting
   * still holds its own default, so this never overrides something set on purpose.
   */
  public static void migrateFormerKeys(ModConfig config) {
    if (config == null || config.getSpec() != SPEC) {
      return;
    }
    try {
      net.neoforged.fml.config.IConfigSpec.ILoadedConfig loaded = config.getLoadedConfig();
      if (loaded == null || loaded.config() == null) {
        return;
      }
      com.electronwill.nightconfig.core.CommentedConfig data = loaded.config();
      Object former = data.get(FORMER_GAUGE_LEVEL_KEY);
      if (!(former instanceof Number oldLevel)) {
        return;
      }
      data.remove(FORMER_GAUGE_LEVEL_KEY);
      int carried = Math.max(1, Math.min(5, oldLevel.intValue()));
      if (carried != GAUGE_MIN_BUILDING_LEVEL.getDefault()
          && GAUGE_MIN_BUILDING_LEVEL.get() == GAUGE_MIN_BUILDING_LEVEL.getDefault()) {
        GAUGE_MIN_BUILDING_LEVEL.set(carried);
        TheSettlerXCreate.LOGGER.info(
            "[Config] carried {}={} over to gaugeMinBuildingLevel",
            FORMER_GAUGE_LEVEL_KEY,
            carried);
      }
      loaded.save();
    } catch (Exception ex) {
      // A config that cannot be read or written is not worth failing startup over; the new setting
      // keeps its default, which is what would have happened without this.
      TheSettlerXCreate.LOGGER.info(
          "[Config] could not carry over {}: {}",
          FORMER_GAUGE_LEVEL_KEY,
          ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
    }
  }

  /**
   * The Create Shop level a gauge request needs, falling back to the default while the config is
   * not loaded yet. That is the same situation {@link DebugLog#enabled()} covers: early startup,
   * and tests that run without a config file.
   */
  public static int gaugeMinBuildingLevel() {
    try {
      return GAUGE_MIN_BUILDING_LEVEL.get();
    } catch (IllegalStateException notLoaded) {
      return GAUGE_MIN_BUILDING_LEVEL.getDefault();
    }
  }
}
