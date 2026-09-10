package com.thesettler_x_create.minecolonies.requestsystem.resolver;

/**
 * Result of {@link CreateShopStockResolver#getAvailability}: how much of a deliverable is
 * available from each source (Create network, racks, pickup reservation) and the combined total.
 */
record CreateShopStockSnapshot(
    int networkAvailable, int rackAvailable, int pickupAvailable, int rackUsable, int available) {}
