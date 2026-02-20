package com.thesettler_x_create.minecolonies.requestsystem.resolver;

/** Worker-availability decisions for request ordering and pending resume. */
final class CreateShopWorkerAvailabilityGate {
  boolean shouldDeferNetworkOrder(boolean workerWorking, int neededCount) {
    return !workerWorking && neededCount > 0;
  }

  boolean shouldResumePending(boolean workerWorking, int pendingCount) {
    return workerWorking && pendingCount > 0;
  }
}
