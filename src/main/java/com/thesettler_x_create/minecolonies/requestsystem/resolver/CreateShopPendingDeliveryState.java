package com.thesettler_x_create.minecolonies.requestsystem.resolver;

final class CreateShopPendingDeliveryState {
  private int pendingCount;
  private boolean deliveryCreated;
  private boolean deliveryStarted;
  private long cooldownUntil;
  private String reason;

  int getPendingCount() {
    return pendingCount;
  }

  void setPendingCount(int pendingCount) {
    this.pendingCount = pendingCount;
  }

  boolean isDeliveryCreated() {
    return deliveryCreated;
  }

  void setDeliveryCreated(boolean deliveryCreated) {
    this.deliveryCreated = deliveryCreated;
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
