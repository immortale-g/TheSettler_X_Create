package com.thesettler_x_create.create;

import com.google.common.collect.Multimap;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.simibubi.create.content.logistics.packager.PackagingRequest;
import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBehaviour;
import com.simibubi.create.content.logistics.packagerLink.LogisticsManager;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;
import com.thesettler_x_create.TheSettlerXCreate;
import java.util.List;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/**
 * Single chokepoint for placing a package request against a Create stock network.
 *
 * <p>Every call site funnels through here instead of calling {@code
 * LogisticsManager.broadcastPackageRequest} itself, so addon compatibility only has to be handled
 * once. Two things this fixes over calling Create directly:
 *
 * <ol>
 *   <li>Create's {@code broadcastPackageRequest} returns {@code true} even when {@code
 *       findPackagersForRequest} found nothing, so "packaged the order" and "silently did nothing"
 *       were indistinguishable. We run the same three steps ourselves and report which one ended
 *       it.
 *   <li>With Create Factory Logistics installed, the stock path stops producing packages
 *       altogether. {@link CreateFactoryLogisticsCompat} takes over when it is present.
 * </ol>
 *
 * <p>Callers must treat anything other than {@link Outcome#DISPATCHED} as "no package exists", in
 * particular by not recording inflight tracking for it.
 */
public final class CreateLogisticsBridge {

  /** What happened to a broadcast attempt. */
  public enum Outcome {
    /** Packages were created for the order. */
    DISPATCHED,
    /** The order was empty by the time it reached Create. */
    EMPTY_ORDER,
    /** No packager on the network could serve the order. */
    NO_PACKAGER,
    /** A packager was found but has too many packages queued already. */
    PACKAGER_BUSY,
    /** The logistics call itself failed. */
    ERROR;

    public boolean dispatched() {
      return this == DISPATCHED;
    }
  }

  private CreateLogisticsBridge() {}

  public static Outcome broadcastPackageRequest(
      UUID networkId, List<BigItemStack> order, @Nullable String address) {
    if (networkId == null || order == null || order.isEmpty()) {
      return Outcome.EMPTY_ORDER;
    }
    PackageOrderWithCrafts request = PackageOrderWithCrafts.simple(order);
    if (request.isEmpty()) {
      return Outcome.EMPTY_ORDER;
    }

    if (CreateFactoryLogisticsCompat.isAvailable()) {
      return broadcastThroughFactoryLogistics(networkId, request, address);
    }
    return broadcastThroughCreate(networkId, request, address);
  }

  private static Outcome broadcastThroughFactoryLogistics(
      UUID networkId, PackageOrderWithCrafts request, @Nullable String address) {
    try {
      boolean dispatched =
          CreateFactoryLogisticsCompat.broadcastPackageRequest(
              networkId, LogisticallyLinkedBehaviour.RequestType.PLAYER, request, address);
      // CFL returns false both for an empty packager set and for a busy packager without saying
      // which, so the coarser NO_PACKAGER is the honest answer here.
      return dispatched ? Outcome.DISPATCHED : Outcome.NO_PACKAGER;
    } catch (Exception | LinkageError ex) {
      TheSettlerXCreate.LOGGER.warn(
          "[CreateShop] Create Factory Logistics broadcast failed for {}: {}", networkId, ex);
      return Outcome.ERROR;
    }
  }

  private static Outcome broadcastThroughCreate(
      UUID networkId, PackageOrderWithCrafts request, @Nullable String address) {
    try {
      // Mirrors LogisticsManager.broadcastPackageRequest, but keeps the two failure cases apart
      // instead of folding them into a boolean that is true in one of them.
      Multimap<PackagerBlockEntity, PackagingRequest> requests =
          LogisticsManager.findPackagersForRequest(
              networkId, request, null, address == null ? "" : address);
      if (requests.isEmpty()) {
        return Outcome.NO_PACKAGER;
      }
      for (PackagerBlockEntity packager : requests.keySet()) {
        if (packager.isTooBusyFor(LogisticallyLinkedBehaviour.RequestType.PLAYER)) {
          return Outcome.PACKAGER_BUSY;
        }
      }
      LogisticsManager.performPackageRequests(requests);
      return Outcome.DISPATCHED;
    } catch (Exception | LinkageError ex) {
      TheSettlerXCreate.LOGGER.warn(
          "[CreateShop] Create logistics broadcast failed for {}: {}", networkId, ex);
      return Outcome.ERROR;
    }
  }
}
