package com.thesettler_x_create.minecolonies.requestsystem.resolver;

/**
 * Mutable per-request bookkeeping held in {@link CreateShopPendingDeliveryTracker}'s cache: how
 * many delivery attempts are outstanding, whether one has been created/started, and a cooldown
 * before the next retry.
 */
final class CreateShopPendingDeliveryState {
  private int pendingCount;
  private boolean deliveryStarted;
  private long cooldownUntil;
  private String reason;

  int getPendingCount() {
    return pendingCount;
  }

  void setPendingCount(int pendingCount) {
    this.pendingCount = pendingCount;
  }

  boolean isDeliveryStarted() {
    return deliveryStarted;
  }

  void setDeliveryStarted(boolean deliveryStarted) {
    this.deliveryStarted = deliveryStarted;
  }

  long getCooldownUntil() {
    return cooldownUntil;
  }

  void setCooldownUntil(long cooldownUntil) {
    this.cooldownUntil = cooldownUntil;
  }

  String getReason() {
    return reason;
  }

  void setReason(String reason) {
    this.reason = reason;
  }
}
