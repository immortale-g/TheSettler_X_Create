package com.thesettler_x_create.minecolonies.requestsystem.resolver;

record CreateShopStockSnapshot(
    int networkAvailable, int rackAvailable, int pickupAvailable, int rackUsable, int available) {}
