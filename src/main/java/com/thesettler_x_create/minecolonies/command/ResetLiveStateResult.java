package com.thesettler_x_create.minecolonies.command;

/** Tallies produced by {@code reset_live_state} - see {@link CreateShopResetCommands}. */
final class ResetLiveStateResult {
  int colonies;
  int shops;
  int requestsCancelled;
  int staleCleaned;
  int runtimeTrackingCleared;
  int runtimeTrackingSkipped;
  int queueEntriesCleared;
  int queueRequestsCancelled;
  int blockedActiveDeliveries;
  int assignmentPruned;
  int deliveryAssignKicks;
  int deliveryRequestsCancelled;
  int drainRounds;
  int drainResiduals;
  int errors;

  /**
   * Renders the full tally as the chat line both {@code reset_live_state} variants report.
   *
   * <p>Lives here rather than at the two command call sites so that adding or removing a field
   * above updates both variants at once - the previous hand-built copies in {@link
   * CreateShopMaintenanceCommands} could silently drift apart (Clean Code Audit a4-3).
   *
   * @param forceWarehouseQueue whether this was the {@code force_warehouse_queue} child command,
   *     which only changes the headline label.
   */
  String toSummaryMessage(boolean forceWarehouseQueue) {
    return "[CreateShop] Live state reset"
        + (forceWarehouseQueue ? " (force queue)" : "")
        + ": colonies="
        + colonies
        + ", shops="
        + shops
        + ", requestsCancelled="
        + requestsCancelled
        + ", staleCleaned="
        + staleCleaned
        + ", runtimeTrackingCleared="
        + runtimeTrackingCleared
        + ", runtimeTrackingSkipped="
        + runtimeTrackingSkipped
        + ", queueEntriesCleared="
        + queueEntriesCleared
        + ", queueRequestsCancelled="
        + queueRequestsCancelled
        + ", blockedActiveDeliveries="
        + blockedActiveDeliveries
        + ", assignmentPruned="
        + assignmentPruned
        + ", deliveryAssignKicks="
        + deliveryAssignKicks
        + ", deliveryRequestsCancelled="
        + deliveryRequestsCancelled
        + ", drainRounds="
        + drainRounds
        + ", drainResiduals="
        + drainResiduals
        + ", errors="
        + errors;
  }
}
