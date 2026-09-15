package com.thesettler_x_create.create;

import com.thesettler_x_create.TheSettlerXCreate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Collects package requests placed during a tick and broadcasts them grouped, so several colonist
 * requests for the same item do not turn into separate network orders.
 *
 * <p>{@link #flush()} runs every server tick. A refused broadcast is retried a few times with a
 * cooldown and then dropped: there is no package to wait for, and the resolver reorders on its own
 * once nothing is tracked as inflight. Retrying every tick instead would rescan the whole logistics
 * network 20 times a second for as long as it stays unreachable.
 */
final class CreateNetworkRequestQueue {
  private static final int MAX_ATTEMPTS = 3;
  private static final long RETRY_COOLDOWN_FLUSHES = 100;

  private static final Map<QueuedRequestKey, QueuedRequestBucket> QUEUED_REQUESTS = new HashMap<>();
  private static long flushCounter;

  private CreateNetworkRequestQueue() {}

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
        requeue(key, bucket);
        continue;
      }
      CreateLogisticsBridge.Outcome outcome =
          bucket.facade.broadcastQueuedRequest(key, bucket.stacks);
      if (outcome.dispatched()) {
        continue;
      }
      bucket.attempts++;
      if (!shouldRetry(outcome, bucket.attempts)) {
        TheSettlerXCreate.LOGGER.warn(
            "[CreateShop] dropping package request after {} attempt(s) ({}) network={} address='{}' requester='{}'",
            bucket.attempts,
            outcome,
            key.networkId,
            key.address,
            key.requesterName);
        continue;
      }
      bucket.retryAfterFlush = flushCounter + RETRY_COOLDOWN_FLUSHES;
      requeue(key, bucket);
    }
  }

  /**
   * Whether a refused broadcast is worth sending again.
   *
   * <p>A busy packager clears on its own and an unreachable one may just be mid-reload, so both are
   * retried a limited number of times. An empty order or a failed call will not fix itself by being
   * sent again. Package-private so the retry policy can be tested without a Minecraft bootstrap.
   */
  static boolean shouldRetry(CreateLogisticsBridge.Outcome outcome, int attempts) {
    if (outcome == null || outcome.dispatched() || attempts >= MAX_ATTEMPTS) {
      return false;
    }
    return outcome == CreateLogisticsBridge.Outcome.PACKAGER_BUSY
        || outcome == CreateLogisticsBridge.Outcome.NO_PACKAGER;
  }

  /** Package-private for testing: flushes to wait before retrying a refused bucket. */
  static long retryCooldownFlushes() {
    return RETRY_COOLDOWN_FLUSHES;
  }

  private static void requeue(QueuedRequestKey key, QueuedRequestBucket failed) {
    if (key == null || failed == null || failed.facade == null || failed.stacks.isEmpty()) {
      return;
    }
    QueuedRequestBucket target =
        QUEUED_REQUESTS.computeIfAbsent(key, ignored -> new QueuedRequestBucket(failed.facade));
    if (target.facade == null) {
      target.facade = failed.facade;
    }
    target.attempts = Math.max(target.attempts, failed.attempts);
    target.retryAfterFlush = Math.max(target.retryAfterFlush, failed.retryAfterFlush);
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
