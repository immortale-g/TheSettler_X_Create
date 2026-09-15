package com.thesettler_x_create.create;

import com.thesettler_x_create.ItemStackDataUtil;
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

  /**
   * flush() runs every server tick. Retrying a refused broadcast on the very next tick would rescan
   * the whole logistics network 20 times a second for as long as it stays unreachable, so a refusal
   * has to cool down first.
   */
  private static final long RETRY_COOLDOWN_FLUSHES = 100;

  private static long flushCounter;

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
      ItemStackDataUtil.mergeIntoList(bucket.stacks, stack);
    }
  }

  /**
   * Drops the orders queued for one shop (network and address) that were not broadcast yet.
   *
   * @return number of dropped buckets
   */
  static int discard(UUID networkId, String address) {
    if (networkId == null) {
      return 0;
    }
    String shopAddress = address == null ? "" : address;
    int before = QUEUED_REQUESTS.size();
    QUEUED_REQUESTS
        .keySet()
        .removeIf(
            key ->
                networkId.equals(key.networkId())
                    && shopAddress.equals(key.address() == null ? "" : key.address()));
    return before - QUEUED_REQUESTS.size();
  }

  static void flush() {
    if (QUEUED_REQUESTS.isEmpty()) {
      return;
    }
    flushCounter++;
    var snapshot = new ArrayList<>(QUEUED_REQUESTS.entrySet());
    QUEUED_REQUESTS.clear();
    for (var entry : snapshot) {
      QueuedRequestKey key = entry.getKey();
      QueuedRequestBucket bucket = entry.getValue();
      if (bucket == null || bucket.facade == null || bucket.stacks.isEmpty()) {
        continue;
      }
      if (bucket.retryAfterFlush > flushCounter) {
        requeueWithoutCountingAttempt(key, bucket);
        continue;
      }
      CreateLogisticsBridge.Outcome outcome =
          bucket.facade.broadcastQueuedRequest(key, bucket.stacks);
      if (!outcome.dispatched()) {
        requeueFailedBucket(key, bucket, outcome);
      }
    }
  }

  /**
   * Whether a refused broadcast is worth sending again.
   *
   * <p>A busy packager clears on its own and an unreachable one may just be mid-reload, so both are
   * retried. An empty order or a failed call will not fix itself by being sent again.
   * Package-private so the retry policy can be tested without a Minecraft bootstrap.
   */
  static boolean shouldRetry(CreateLogisticsBridge.Outcome outcome, int attempts) {
    if (outcome == null || outcome.dispatched() || attempts > MAX_RETRY_ATTEMPTS) {
      return false;
    }
    return outcome == CreateLogisticsBridge.Outcome.PACKAGER_BUSY
        || outcome == CreateLogisticsBridge.Outcome.NO_PACKAGER;
  }

  /** Package-private for testing: flushes to wait before retrying a refused bucket. */
  static long retryCooldownFlushes() {
    return RETRY_COOLDOWN_FLUSHES;
  }

  private static void requeueFailedBucket(
      QueuedRequestKey key, QueuedRequestBucket failed, CreateLogisticsBridge.Outcome outcome) {
    if (key == null || failed == null || failed.facade == null || failed.stacks.isEmpty()) {
      return;
    }
    int attempts = failed.failedAttempts + 1;
    if (!shouldRetry(outcome, attempts)) {
      // Seam-audit finding s2-1 (partial-fix follow-up): the ordered amount was reserved
      // (CreateShopAttemptResolveService.attemptResolve -> pickup.reserve(...)) as soon as the
      // order was attempted, before broadcast success was known. Release exactly that amount now
      // instead of leaving it "spoken for" until its own TTL expires - the requester re-derives
      // its need next tick and can immediately re-order instead of waiting out the reservation.
      failed.facade.releaseAbandonedReservation(key.requestUuid(), failed.stacks);
      TheSettlerXCreate.LOGGER.warn(
          "[CreateShop] giving up on Create network request after {} failed broadcast attempts ({}),"
              + " network={} address='{}' requester='{}' stacks={} - dropping and releasing its"
              + " reservation so the requester can re-derive the need immediately",
          attempts - 1,
          outcome,
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
    target.retryAfterFlush = flushCounter + RETRY_COOLDOWN_FLUSHES;
    for (ItemStack stack : failed.stacks) {
      ItemStackDataUtil.mergeIntoList(target.stacks, stack);
    }
  }

  /** Puts a bucket back while it is still cooling down, without spending one of its attempts. */
  private static void requeueWithoutCountingAttempt(
      QueuedRequestKey key, QueuedRequestBucket waiting) {
    QueuedRequestBucket target =
        QUEUED_REQUESTS.computeIfAbsent(key, ignored -> new QueuedRequestBucket(waiting.facade));
    if (target.facade == null) {
      target.facade = waiting.facade;
    }
    target.failedAttempts = Math.max(target.failedAttempts, waiting.failedAttempts);
    target.retryAfterFlush = Math.max(target.retryAfterFlush, waiting.retryAfterFlush);
    for (ItemStack stack : waiting.stacks) {
      ItemStackDataUtil.mergeIntoList(target.stacks, stack);
    }
  }
}
