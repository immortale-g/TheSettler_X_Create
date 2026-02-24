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

  public static final ModConfigSpec.DoubleValue SHOPKEEPER_WORK_RADIUS =
      BUILDER
          .comment("Max distance (blocks) a working shopkeeper may roam from the hut block.")
          .defineInRange("shopkeeperWorkRadius", 2.5D, 0.5D, 16.0D);

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

  public static final ModConfigSpec.LongValue GLOBAL_INJECTOR_LOG_COOLDOWN =
      BUILDER
          .comment("Cooldown (ticks) between global resolver injector logs.")
          .defineInRange("globalInjectorLogCooldown", 200L, 0L, 24000L);

  public static final ModConfigSpec.LongValue GLOBAL_REQUEST_LOG_COOLDOWN =
      BUILDER
          .comment("Cooldown (ticks) between global request diagnostics.")
          .defineInRange("globalRequestLogCooldown", 200L, 0L, 24000L);

  public static final ModConfigSpec.LongValue RESOLVER_INJECTOR_DEBUG_COOLDOWN =
      BUILDER
          .comment("Cooldown (ticks) between Create Shop resolver injector debug logs.")
          .defineInRange("resolverInjectorDebugCooldown", 200L, 0L, 24000L);

  public static final ModConfigSpec.LongValue RESOLVER_DELIVERY_DEBUG_COOLDOWN =
      BUILDER
          .comment("Cooldown (ticks) between Create Shop delivery debug logs.")
          .defineInRange("resolverDeliveryDebugCooldown", 200L, 0L, 24000L);

  public static final ModConfigSpec.LongValue RESOLVER_CHAIN_SANITIZE_COOLDOWN =
      BUILDER
          .comment("Cooldown (ticks) between request chain sanitize passes.")
          .defineInRange("resolverChainSanitizeCooldown", 200L, 0L, 24000L);

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

  public static final ModConfigSpec.LongValue DELIVERY_ASSIGNMENT_DEBUG_COOLDOWN =
      BUILDER
          .comment("Cooldown (ticks) between delivery assignment debug logs.")
          .defineInRange("deliveryAssignmentDebugCooldown", 200L, 0L, 24000L);

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

  static final ModConfigSpec SPEC = BUILDER.build();
}
