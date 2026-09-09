package com.thesettler_x_create.create;

import com.thesettler_x_create.TheSettlerXCreate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Cross-shop, cross-tick coalescing of Create stock-network requests. Multiple {@link
 * CreateNetworkFacade} instances (one per call, not a singleton per shop) can queue stacks for the
 * same network+address+requester within a tick; {@link #flush()} - called once per server tick from
 * {@code TheSettlerXCreate} - broadcasts each accumulated bucket as a single grouped package
 * request instead of one broadcast per queued call.
 */
final class CreateNetworkRequestQueue {
  private CreateNetworkRequestQueue() {}

  /**
   * A broadcast that keeps failing (stale/merged network id, no linked packager, etc.) would
   * otherwise requeue forever with no visible signal — the deficit gets silently re-derived and
   * re-queued every tick. Give up after this many consecutive failures and log loudly instead.
   */
  private static final int MAX_RETRY_ATTEMPTS = 5;

  private static final Map<QueuedRequestKey, QueuedRequestBucket> QUEUED_REQUESTS =
      new ConcurrentHashMap<>();

  static void queue(
      CreateNetworkFacade facade,
      UUID networkId,
      String address,
      List<ItemStack> stacks,
      String requesterName,
      @Nullable UUID requestUuid) {
    if (stacks == null || stacks.isEmpty() || networkId == null) {
      return;
    }
    QueuedRequestKey key = new QueuedRequestKey(networkId, address, requesterName, requestUuid);
    QueuedRequestBucket bucket =
        QUEUED_REQUESTS.computeIfAbsent(key, k -> new QueuedRequestBucket(facade));
    bucket.facade = facade;
    for (ItemStack stack : stacks) {
      mergeInto(bucket.stacks, stack);
    }
  }

  static void flush() {
    if (QUEUED_REQUESTS.isEmpty()) {
      return;
    }
    var snapshot = new ArrayList<>(QUEUED_REQUESTS.entrySet());
    QUEUED_REQUESTS.clear();
    for (var entry : snapshot) {
      QueuedRequestKey key = entry.getKey();
      QueuedRequestBucket bucket = entry.getValue();
      if (bucket == null || bucket.facade == null || bucket.stacks.isEmpty()) {
        continue;
      }
      if (!bucket.facade.broadcastQueuedRequest(key, bucket.stacks)) {
        requeueFailedBucket(key, bucket);
      }
    }
  }

  private static void requeueFailedBucket(QueuedRequestKey key, QueuedRequestBucket failed) {
    if (key == null || failed == null || failed.facade == null || failed.stacks.isEmpty()) {
      return;
    }
    int attempts = failed.failedAttempts + 1;
    if (attempts > MAX_RETRY_ATTEMPTS) {
      // Seam-audit finding s2-1 (partial-fix follow-up): the ordered amount was reserved
      // (CreateShopAttemptResolveService.attemptResolve -> pickup.reserve(...)) as soon as the
      // order was attempted, before broadcast success was known. Release exactly that amount now
      // instead of leaving it "spoken for" until its own TTL expires - the requester re-derives
      // its need next tick and can immediately re-order instead of waiting out the reservation.
      failed.facade.releaseAbandonedReservation(key.requestUuid(), failed.stacks);
      TheSettlerXCreate.LOGGER.warn(
          "[CreateShop] giving up on Create network request after {} failed broadcast attempts,"
              + " network={} address='{}' requester='{}' stacks={} - dropping and releasing its"
              + " reservation so the requester can re-derive the need immediately",
          attempts - 1,
          key.networkId(),
          key.address(),
          key.requesterName(),
          failed.stacks.size());
      return;
    }
    QueuedRequestBucket target =
        QUEUED_REQUESTS.computeIfAbsent(key, ignored -> new QueuedRequestBucket(failed.facade));
    if (target.facade == null) {
      target.facade = failed.facade;
    }
    target.failedAttempts = attempts;
    for (ItemStack stack : failed.stacks) {
      mergeInto(target.stacks, stack);
    }
  }

  private static void mergeInto(List<ItemStack> target, ItemStack stack) {
    if (target == null || stack == null || stack.isEmpty()) {
      return;
    }
    for (ItemStack existing : target) {
      if (ItemStack.isSameItemSameComponents(existing, stack)) {
        existing.setCount(existing.getCount() + stack.getCount());
        return;
      }
    }
    target.add(stack.copy());
  }
}
