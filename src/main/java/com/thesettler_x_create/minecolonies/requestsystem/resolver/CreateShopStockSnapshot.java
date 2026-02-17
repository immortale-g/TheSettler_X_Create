package com.thesettler_x_create.minecolonies.requestsystem.resolver;

final class CreateShopStockSnapshot {
  private final int networkAvailable;
  private final int rackAvailable;
  private final int pickupAvailable;
  private final int rackUsable;
  private final int available;

  CreateShopStockSnapshot(
      int networkAvailable, int rackAvailable, int pickupAvailable, int rackUsable, int available) {
    this.networkAvailable = networkAvailable;
    this.rackAvailable = rackAvailable;
    this.pickupAvailable = pickupAvailable;
    this.rackUsable = rackUsable;
    this.available = available;
  }

  int getNetworkAvailable() {
    return networkAvailable;
  }

  int getRackAvailable() {
    return rackAvailable;
  }

  int getPickupAvailable() {
    return pickupAvailable;
  }

  int getRackUsable() {
    return rackUsable;
  }

  int getAvailable() {
    return available;
  }
}
