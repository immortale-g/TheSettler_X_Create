package com.thesettler_x_create;

import net.neoforged.neoforge.common.ModConfigSpec;

public class Config {
  private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

  public static final ModConfigSpec.BooleanValue DEBUG_LOGGING =
      BUILDER
          .comment("Enable extra debug logging for TheSettler_x_Create")
          .define("debugLogging", false);

  public static final ModConfigSpec.IntValue PERMA_MIN_BUILDING_LEVEL =
      BUILDER
          .comment("Minimum Create Shop level required for perma requests.")
          .defineInRange("permaMinBuildingLevel", 2, 1, 5);

  public static final ModConfigSpec.LongValue PERMA_REQUEST_INTERVAL_TICKS =
      BUILDER
          .comment("Ticks between perma request evaluations.")
          .defineInRange("permaRequestIntervalTicks", 200L, 20L, 24000L);

  public static final ModConfigSpec.LongValue MISSING_NETWORK_WARNING_COOLDOWN =
      BUILDER
          .comment("Cooldown (ticks) between missing network warning messages.")
          .defineInRange("missingNetworkWarningCooldown", 6000L, 20L, 24000L);

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
}
