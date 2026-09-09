package com.thesettler_x_create.create;

import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBehaviour;
import com.simibubi.create.content.logistics.packagerLink.LogisticsManager;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;
import java.util.List;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/**
 * Single chokepoint for building a Create stock-network package request and broadcasting it
 * through {@link LogisticsManager}. Every call site that wants to place a package request against
 * a Create logistics network funnels through here instead of independently constructing a {@link
 * PackageOrderWithCrafts} and calling {@code broadcastPackageRequest} itself - that used to be
 * duplicated three times (the grouped broadcast in {@link CreateNetworkFacade}, and the test- and
 * batch-request handlers in {@code ModNetwork}), so a future compatibility shim for Create-addon
 * mixins that change the request shape only has to patch this one place.
 */
public final class CreateLogisticsBridge {
  private CreateLogisticsBridge() {}

  public static void broadcastPackageRequest(
      UUID networkId, List<BigItemStack> order, @Nullable String address) {
    PackageOrderWithCrafts request = PackageOrderWithCrafts.simple(order);
    LogisticsManager.broadcastPackageRequest(
        networkId,
        LogisticallyLinkedBehaviour.RequestType.PLAYER,
        request,
        null,
        address == null ? "" : address);
  }
}
