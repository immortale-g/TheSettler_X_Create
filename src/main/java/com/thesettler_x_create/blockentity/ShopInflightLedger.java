package com.thesettler_x_create.blockentity;

import com.thesettler_x_create.Config;
import com.thesettler_x_create.DebugLog;
import com.thesettler_x_create.TextUtil;
import com.thesettler_x_create.TheSettlerXCreate;
import com.thesettler_x_create.stock.InflightBook;
import com.thesettler_x_create.stock.nbt.InflightNbt;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Minecraft side of the orders a {@link CreateShopBlockEntity} has on their way from the Create
 * network: "ordered but not here yet", matched back to arrivals in the racks and surfaced as
 * overdue notices when they take too long.
 *
 * <p>The bookkeeping itself is an {@link InflightBook} over item stacks. This class adds the server
 * thread guard, marks the block entity dirty, logs, and saves through {@link InflightNbt}.
 */
class ShopInflightLedger {
  private static final String TAG_INFLIGHT = "Inflight";
  private static final String TAG_INFLIGHT_BASELINES = "InflightBaselines";

  private final LedgerHost host;
  private final InflightBook<ItemStack> book =
      new InflightBook<>(
          ItemStack::isSameItemSameComponents,
          ItemStack::isSameItem,
          ShopInflightLedger::makeKey,
          ShopInflightLedger::itemId);
  private long lastInflightLogTime;

  ShopInflightLedger(LedgerHost host) {
    this.host = host;
  }

  /** Returns unique stack keys currently tracked as inflight. */
  List<ItemStack> getInflightKeys() {
    List<ItemStack> keys = new ArrayList<>();
    for (ItemStack key : book.keys()) {
      keys.add(key.copy());
    }
    return keys;
  }

  void recordInflight(
      List<ItemStack> stacks,
      Map<ItemStack, Integer> baselines,
      String requesterName,
      String address,
      @Nullable UUID requestUuid) {
    if (!host.ensureServerThread("recordInflight")) {
      return;
    }
    if (stacks == null || stacks.isEmpty()) {
      return;
    }
    long now = host.gameTime();
    boolean changed = false;
    for (ItemStack stack : stacks) {
      if (stack == null || stack.isEmpty()) {
        continue;
      }
      changed |=
          book.record(
              stack,
              stack.getCount(),
              now,
              sanitize(requesterName),
              sanitize(address),
              requestUuid,
              findCount(baselines, stack));
    }
    if (changed) {
      host.markChanged();
    }
  }

  /** Moves the arrival baseline along with a rack change the shop caused or observed. */
  void noteStockChange(ItemStack key, int delta) {
    if (!host.ensureServerThread("noteStockChange") || key == null || key.isEmpty()) {
      return;
    }
    book.noteStockChange(key, delta);
  }

  /**
   * Reconciles inflight entries against current rack counts to detect arrivals.
   *
   * @return what arrived, per owner and item
   */
  List<InflightBook.Arrival<ItemStack>> reconcileInflight(Map<ItemStack, Integer> currentCounts) {
    if (!host.ensureServerThread("reconcileInflight") || book.entryCount() == 0) {
      return Collections.emptyList();
    }
    // Stored keys are the same instances before and after, so the records compare by count.
    List<InflightBook.StoredBaseline<ItemStack>> baselinesBefore = book.storedBaselines();
    int entriesBefore = book.entryCount();
    List<InflightBook.Arrival<ItemStack>> arrivals =
        book.reconcile(key -> findCount(currentCounts, key));
    long now = host.gameTime();
    if (shouldLogOverdue(now)) {
      logOverdue(now);
    }
    if (!arrivals.isEmpty()
        || book.entryCount() != entriesBefore
        || !book.storedBaselines().equals(baselinesBefore)) {
      host.markChanged();
    }
    return arrivals;
  }

  /** Marks overdue inflight entries as notified and returns notices to surface. */
  List<CreateShopBlockEntity.InflightNotice> consumeOverdueNotices(long now, long timeout) {
    if (!host.ensureServerThread("consumeOverdueNotices")) {
      return Collections.emptyList();
    }
    List<InflightBook.Notice<ItemStack>> notices = book.consumeOverdueNotices(now, timeout);
    if (notices.isEmpty()) {
      return Collections.emptyList();
    }
    host.markChanged();
    List<CreateShopBlockEntity.InflightNotice> converted = new ArrayList<>(notices.size());
    for (InflightBook.Notice<ItemStack> notice : notices) {
      converted.add(toNotice(notice));
    }
    return converted;
  }

  int consumeInflight(
      ItemStack stackKey,
      int amount,
      @Nullable String requesterName,
      @Nullable String address,
      long requestedAt) {
    if (!host.ensureServerThread("consumeInflight") || stackKey == null || stackKey.isEmpty()) {
      return 0;
    }
    int consumed =
        book.consume(stackKey, amount, sanitize(requesterName), sanitize(address), requestedAt);
    if (consumed > 0) {
      host.markChanged();
    }
    return consumed;
  }

  int getInflightRemaining(ItemStack stackKey, @Nullable UUID requestUuid) {
    if (!host.ensureServerThread("getInflightRemaining")
        || stackKey == null
        || stackKey.isEmpty()) {
      return 0;
    }
    return book.remainingFor(requestUuid, stackKey);
  }

  int getInflightRemaining(
      ItemStack stackKey,
      @Nullable String requesterName,
      @Nullable String address,
      long requestedAt) {
    if (!host.ensureServerThread("getInflightRemaining")
        || stackKey == null
        || stackKey.isEmpty()) {
      return 0;
    }
    return book.remaining(stackKey, sanitize(requesterName), sanitize(address), requestedAt);
  }

  int cancelInflight(
      ItemStack stackKey,
      @Nullable String requesterName,
      @Nullable String address,
      long requestedAt) {
    if (!host.ensureServerThread("cancelInflight") || stackKey == null || stackKey.isEmpty()) {
      return 0;
    }
    int removed = book.cancel(stackKey, sanitize(requesterName), sanitize(address), requestedAt);
    if (removed > 0) {
      host.markChanged();
    }
    return removed;
  }

  int cancelInflightByUuid(@Nullable UUID requestUuid) {
    if (!host.ensureServerThread("cancelInflightByUuid")) {
      return 0;
    }
    int removed = book.cancel(requestUuid);
    if (removed > 0) {
      host.markChanged();
    }
    return removed;
  }

  int getInflightRemainingFor(@Nullable UUID requestUuid, Predicate<ItemStack> accepts) {
    if (!host.ensureServerThread("getInflightRemainingFor")) {
      return 0;
    }
    return book.remainingForMatching(requestUuid, accepts);
  }

  /** Removes the owner from a request's orders; they keep coming for nobody. */
  int detach(@Nullable UUID requestUuid) {
    if (!host.ensureServerThread("detachInflight")) {
      return 0;
    }
    int detached = book.detach(requestUuid);
    if (detached > 0) {
      host.markChanged();
    }
    return detached;
  }

  /** Hands unowned incoming stock the request accepts to it. @return the amount claimed */
  int claimFree(@Nullable UUID requestUuid, Predicate<ItemStack> accepts, int amount) {
    if (!host.ensureServerThread("claimFreeInflight")) {
      return 0;
    }
    int claimed = book.claimFreeMatching(requestUuid, accepts, amount);
    if (claimed > 0) {
      host.markChanged();
    }
    return claimed;
  }

  /** Drops an amount of one item from a request's orders. @return the amount removed */
  int cancel(@Nullable UUID requestUuid, ItemStack key, int amount) {
    if (!host.ensureServerThread("cancelInflightAmount") || key == null || key.isEmpty()) {
      return 0;
    }
    int removed = book.cancel(requestUuid, key, amount);
    if (removed > 0) {
      host.markChanged();
    }
    return removed;
  }

  /** Drops unowned orders older than {@code timeout}. @return the dropped entries */
  List<InflightBook.StoredEntry<ItemStack>> expireFree(long now, long timeout) {
    if (!host.ensureServerThread("expireFreeInflight")) {
      return Collections.emptyList();
    }
    List<InflightBook.StoredEntry<ItemStack>> expired = book.expireFree(now, timeout);
    if (!expired.isEmpty()) {
      host.markChanged();
    }
    return expired;
  }

  /**
   * Debug helper: injects an artificial inflight tuple for automated lost-package harness tests.
   */
  int debugInjectInflight(
      ItemStack stackKey,
      int amount,
      @Nullable String requesterName,
      @Nullable String address,
      long ageTicks) {
    if (!host.ensureServerThread("debugInjectInflight") || stackKey == null || stackKey.isEmpty()) {
      return 0;
    }
    long requestedAt = Math.max(0L, host.gameTime() - Math.max(0L, ageTicks));
    int injected = Math.max(1, amount);
    book.record(
        stackKey, injected, requestedAt, sanitize(requesterName), sanitize(address), null, 0);
    host.markChanged();
    return amount;
  }

  /** Debug helper: returns the oldest active inflight tuple, regardless of overdue state. */
  @Nullable
  CreateShopBlockEntity.InflightNotice debugPeekOldestInflightNotice(long now) {
    if (!host.ensureServerThread("debugPeekOldestInflightNotice")) {
      return null;
    }
    InflightBook.Notice<ItemStack> oldest = book.peekOldest(now);
    return oldest == null ? null : toNotice(oldest);
  }

  int size() {
    return book.size();
  }

  /** Number of orders still tracked as on their way, baselines not counted. */
  int entryCount() {
    return book.entryCount();
  }

  void clear() {
    book.clear();
  }

  void load(CompoundTag tag, HolderLookup.Provider registries) {
    book.restore(
        InflightNbt.readEntries(
            tag.getList(TAG_INFLIGHT, Tag.TAG_COMPOUND),
            stackTag -> readStack(stackTag, registries)),
        InflightNbt.readBaselines(
            tag.getList(TAG_INFLIGHT_BASELINES, Tag.TAG_COMPOUND),
            stackTag -> readStack(stackTag, registries)));
  }

  void save(CompoundTag tag, HolderLookup.Provider registries) {
    if (book.entryCount() > 0) {
      tag.put(
          TAG_INFLIGHT,
          InflightNbt.writeEntries(book.storedEntries(), stack -> stack.save(registries)));
    }
    if (!book.storedBaselines().isEmpty()) {
      tag.put(
          TAG_INFLIGHT_BASELINES,
          InflightNbt.writeBaselines(book.storedBaselines(), stack -> stack.save(registries)));
    }
  }

  private boolean shouldLogOverdue(long now) {
    if (!DebugLog.enabled() || now == 0L) {
      return false;
    }
    return now - lastInflightLogTime >= Config.INFLIGHT_LOG_COOLDOWN.getAsLong();
  }

  private void logOverdue(long now) {
    long timeout = Config.INFLIGHT_TIMEOUT_TICKS.getAsLong();
    if (timeout <= 0L) {
      return;
    }
    List<String> overdue = new ArrayList<>();
    for (InflightBook.StoredEntry<ItemStack> entry : book.storedEntries()) {
      long age = now - entry.requestedAt();
      if (age >= timeout) {
        overdue.add(
            entry.key().getHoverName().getString() + " x" + entry.remaining() + " age=" + age);
      }
    }
    if (overdue.isEmpty()) {
      return;
    }
    lastInflightLogTime = now;
    TheSettlerXCreate.LOGGER.info("[CreateShop] inflight overdue: {}", String.join(" | ", overdue));
  }

  private static CreateShopBlockEntity.InflightNotice toNotice(
      InflightBook.Notice<ItemStack> notice) {
    return new CreateShopBlockEntity.InflightNotice(
        notice.key().copy(),
        notice.remaining(),
        notice.age(),
        notice.requester(),
        notice.address(),
        notice.requestedAt(),
        notice.owner());
  }

  private static int findCount(Map<ItemStack, Integer> counts, ItemStack key) {
    if (counts == null || counts.isEmpty() || key == null || key.isEmpty()) {
      return 0;
    }
    for (Map.Entry<ItemStack, Integer> entry : counts.entrySet()) {
      if (ItemStack.isSameItemSameComponents(entry.getKey(), key)) {
        return entry.getValue();
      }
    }
    return 0;
  }

  private static Optional<ItemStack> readStack(Tag stackTag, HolderLookup.Provider registries) {
    if (!(stackTag instanceof CompoundTag compound)) {
      return Optional.empty();
    }
    return ItemStack.parse(registries, compound).filter(stack -> !stack.isEmpty());
  }

  private static ItemStack makeKey(ItemStack stack) {
    ItemStack copy = stack.copy();
    copy.setCount(1);
    return copy;
  }

  private static String itemId(ItemStack stack) {
    return String.valueOf(BuiltInRegistries.ITEM.getKey(stack.getItem()));
  }

  private static String sanitize(@Nullable String value) {
    return TextUtil.sanitize(value);
  }
}
