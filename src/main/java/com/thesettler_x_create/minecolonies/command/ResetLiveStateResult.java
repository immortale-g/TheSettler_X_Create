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
}
