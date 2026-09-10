package com.thesettler_x_create.blockentity;

import com.thesettler_x_create.Config;
import com.thesettler_x_create.TheSettlerXCreate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Tracks "on its way but not here yet" quantities for a {@link CreateShopBlockEntity} - orders that
 * were placed on the Create network or via the Warehouse, matched back to arrivals in the shop's
 * racks, and surfaced as overdue notices when they take too long. Extracted from {@code
 * CreateShopBlockEntity} (which held this state directly until the pre-1.0 hardening pass) purely
 * to keep that class's size manageable; behavior is unchanged.
 */
class ShopInflightLedger {
  private static final String TAG_INFLIGHT = "Inflight";
  private static final String TAG_INFLIGHT_BASELINES = "InflightBaselines";
  private static final int MAX_OPEN_INFLIGHT_SEGMENTS_PER_TUPLE = 2;

  private final CreateShopBlockEntity owner;
  private final List<InflightEntry> inflightEntries = new ArrayList<>();
  private final List<BaselineEntry> inflightBaselines = new ArrayList<>();
  private long lastInflightLogTime;

  ShopInflightLedger(CreateShopBlockEntity owner) {
    this.owner = owner;
  }

  /** Returns unique stack keys currently tracked as inflight. */
  List<ItemStack> getInflightKeys() {
    List<ItemStack> keys = new ArrayList<>();
    for (InflightEntry entry : inflightEntries) {
      if (entry.stackKey == null || entry.stackKey.isEmpty()) {
        continue;
      }
      if (!containsKey(keys, entry.stackKey)) {
        keys.add(entry.stackKey.copy());
      }
    }
    return keys;
  }

  void recordInflight(
      List<ItemStack> stacks,
      Map<ItemStack, Integer> baselines,
      String requesterName,
      String address) {
    recordInflight(stacks, baselines, requesterName, address, null);
  }

  void recordInflight(
      List<ItemStack> stacks,
      Map<ItemStack, Integer> baselines,
      String requesterName,
      String address,
      @Nullable UUID requestUuid) {
    if (!owner.ensureServerThread("recordInflight")) {
      return;
    }
    if (stacks == null || stacks.isEmpty()) {
      return;
    }
    long now = owner.getGameTimeSafe();
    boolean changed = false;
    for (ItemStack stack : stacks) {
      if (stack == null || stack.isEmpty() || stack.getCount() <= 0) {
        continue;
      }
      ItemStack key = makeKey(stack);
      int baseline = findCount(baselines, key);
      upsertBaseline(key, baseline);
      inflightEntries.add(
          new InflightEntry(
              key, stack.getCount(), now, sanitize(requesterName), sanitize(address), requestUuid));
      changed = true;
    }
    if (compactInflightEntriesForPromptStability()) {
      changed = true;
    }
    if (changed) {
      owner.setChanged();
    }
  }

  /** Reconciles inflight entries against current rack counts to detect arrivals. */
  void reconcileInflight(Map<ItemStack, Integer> currentCounts) {
    if (!owner.ensureServerThread("reconcileInflight")) {
      return;
    }
    if (inflightEntries.isEmpty()) {
      return;
    }
    ensureBaselines(currentCounts);
    long now = owner.getGameTimeSafe();
    boolean changed = false;
    for (BaselineEntry baseline : inflightBaselines) {
      int current = findCount(currentCounts, baseline.stackKey);
      int delta = Math.max(0, current - baseline.count);
      if (baseline.count != current) {
        baseline.count = current;
        changed = true;
      }
      if (delta <= 0) {
        continue;
      }
      int remaining = delta;
      Iterator<InflightEntry> iterator = inflightEntries.iterator();
      while (iterator.hasNext() && remaining > 0) {
        InflightEntry entry = iterator.next();
        if (!matches(entry.stackKey, baseline.stackKey)) {
          continue;
        }
        int applied = Math.min(remaining, entry.remaining);
        entry.remaining -= applied;
        remaining -= applied;
        if (entry.remaining <= 0) {
          iterator.remove();
          changed = true;
        } else if (applied > 0) {
          changed = true;
        }
      }
    }
    if (pruneBaselines()) {
      changed = true;
    }
    if (shouldLogOverdue(now)) {
      logOverdue(now);
    }
    if (changed) {
      owner.setChanged();
    }
  }

  /** Marks overdue inflight entries as notified and returns notices to surface. */
  List<CreateShopBlockEntity.InflightNotice> consumeOverdueNotices(long now, long timeout) {
    if (!owner.ensureServerThread("consumeOverdueNotices")) {
      return java.util.Collections.emptyList();
    }
    if (timeout <= 0L || inflightEntries.isEmpty()) {
      return java.util.Collections.emptyList();
    }
    Map<String, InflightEntry> bestPerPromptKey = new java.util.LinkedHashMap<>();
    for (InflightEntry entry : inflightEntries) {
      if (entry.remaining <= 0 || entry.notified) {
        continue;
      }
      long age = now - entry.requestedAt;
      if (age < timeout) {
        continue;
      }
      String promptKey = buildNoticePromptKey(entry.stackKey, entry.address);
      InflightEntry existing = bestPerPromptKey.get(promptKey);
      if (existing == null || entry.requestedAt < existing.requestedAt) {
        bestPerPromptKey.put(promptKey, entry);
      }
    }
    if (bestPerPromptKey.isEmpty()) {
      return java.util.Collections.emptyList();
    }
    InflightEntry selected = null;
    for (InflightEntry candidate : bestPerPromptKey.values()) {
      if (candidate == null) {
        continue;
      }
      if (selected == null || candidate.requestedAt < selected.requestedAt) {
        selected = candidate;
      }
    }
    if (selected == null) {
      return java.util.Collections.emptyList();
    }
    selected.notified = true;
    owner.setChanged();
    long age = now - selected.requestedAt;
    return java.util.List.of(
        new CreateShopBlockEntity.InflightNotice(
            selected.stackKey.copy(),
            selected.remaining,
            age,
            selected.requesterName,
            selected.address,
            selected.requestedAt,
            selected.requestUuid));
  }

  /** Consumes tracked inflight quantity for a specific overdue notice tuple. */
  int consumeInflight(
      ItemStack stackKey, int amount, @Nullable String requesterName, @Nullable String address) {
    return consumeInflight(stackKey, amount, requesterName, address, -1L);
  }

  int consumeInflight(
      ItemStack stackKey,
      int amount,
      @Nullable String requesterName,
      @Nullable String address,
      long requestedAt) {
    if (!owner.ensureServerThread("consumeInflight")) {
      return 0;
    }
    if (stackKey == null || stackKey.isEmpty() || amount <= 0 || inflightEntries.isEmpty()) {
      return 0;
    }
    String requester = sanitize(requesterName);
    String destination = sanitize(address);
    int remaining = amount;
    int consumed;
    boolean changed;
    remaining = consumeInflightMatches(stackKey, remaining, requester, destination, requestedAt);
    consumed = amount - remaining;
    changed = consumed > 0;

    // Fallback: old inflight entries can drift in requester/address text after reloads/renames.
    // If strict tuple matching consumed nothing, clear by stack key to avoid stuck overdue loops.
    if (consumed <= 0 && (!requester.isEmpty() || !destination.isEmpty()) && remaining > 0) {
      int before = remaining;
      remaining = consumeInflightMatches(stackKey, remaining, "", "", requestedAt);
      int fallbackConsumed = before - remaining;
      if (fallbackConsumed > 0) {
        consumed += fallbackConsumed;
        changed = true;
      }
    }
    if (changed) {
      pruneBaselines();
      owner.setChanged();
    }
    return consumed;
  }

  int getInflightRemaining(ItemStack stackKey, @Nullable UUID requestUuid) {
    if (!owner.ensureServerThread("getInflightRemaining")) {
      return 0;
    }
    if (stackKey == null
        || stackKey.isEmpty()
        || requestUuid == null
        || inflightEntries.isEmpty()) {
      return 0;
    }
    int remaining = 0;
    for (InflightEntry entry : inflightEntries) {
      if (!requestUuid.equals(entry.requestUuid)) {
        continue;
      }
      if (!matchesForInflightRecovery(entry.stackKey, stackKey)) {
        continue;
      }
      remaining += Math.max(0, entry.remaining);
    }
    return remaining;
  }

  int getInflightRemaining(
      ItemStack stackKey, @Nullable String requesterName, @Nullable String address) {
    return getInflightRemaining(stackKey, requesterName, address, -1L);
  }

  int getInflightRemaining(
      ItemStack stackKey,
      @Nullable String requesterName,
      @Nullable String address,
      long requestedAt) {
    if (!owner.ensureServerThread("getInflightRemaining")) {
      return 0;
    }
    if (stackKey == null || stackKey.isEmpty() || inflightEntries.isEmpty()) {
      return 0;
    }
    String requester = sanitize(requesterName);
    String destination = sanitize(address);
    boolean requireExactItemMatch = requester.isEmpty() && destination.isEmpty();
    int remaining = 0;
    for (InflightEntry entry : inflightEntries) {
      if (!matchesForInflightLookup(entry.stackKey, stackKey, requireExactItemMatch)) {
        continue;
      }
      if (!matchesInflightTuple(entry, requester, destination, requestedAt)) {
        continue;
      }
      remaining += Math.max(0, entry.remaining);
    }
    return remaining;
  }

  int cancelInflight(ItemStack stackKey, @Nullable String requesterName, @Nullable String address) {
    return cancelInflight(stackKey, requesterName, address, -1L);
  }

  int cancelInflight(
      ItemStack stackKey,
      @Nullable String requesterName,
      @Nullable String address,
      long requestedAt) {
    if (!owner.ensureServerThread("cancelInflight")) {
      return 0;
    }
    if (stackKey == null || stackKey.isEmpty() || inflightEntries.isEmpty()) {
      return 0;
    }
    String requester = sanitize(requesterName);
    String destination = sanitize(address);
    int removed = cancelInflightMatches(stackKey, requester, destination, requestedAt);
    // Fallback: requester labels can drift (hut name vs citizen name); clear by stack+address.
    if (removed <= 0 && !requester.isEmpty()) {
      removed = cancelInflightMatches(stackKey, "", destination, requestedAt);
    }
    if (removed > 0) {
      pruneBaselines();
      owner.setChanged();
    }
    return removed;
  }

  int cancelInflightByUuid(@Nullable UUID requestUuid) {
    if (!owner.ensureServerThread("cancelInflightByUuid")) {
      return 0;
    }
    if (requestUuid == null || inflightEntries.isEmpty()) {
      return 0;
    }
    int removed = 0;
    Iterator<InflightEntry> iterator = inflightEntries.iterator();
    while (iterator.hasNext()) {
      InflightEntry entry = iterator.next();
      if (requestUuid.equals(entry.requestUuid)) {
        removed += Math.max(0, entry.remaining);
        iterator.remove();
      }
    }
    if (removed > 0) {
      pruneBaselines();
      owner.setChanged();
    }
    return removed;
  }

  void markInflightHandedOff(@Nullable UUID requestUuid) {
    if (requestUuid == null) return;
    for (InflightEntry e : inflightEntries) {
      if (requestUuid.equals(e.requestUuid)) {
        e.handedOff = true;
        owner.setChanged();
        return;
      }
    }
  }

  int clearInflightByUuid(@Nullable UUID requestUuid) {
    if (requestUuid == null || inflightEntries.isEmpty()) return 0;
    boolean removed = inflightEntries.removeIf(e -> requestUuid.equals(e.requestUuid));
    if (removed) {
      pruneBaselines();
      owner.setChanged();
      return 1;
    }
    return 0;
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
    if (!owner.ensureServerThread("debugInjectInflight")) {
      return 0;
    }
    if (stackKey == null || stackKey.isEmpty() || amount <= 0) {
      return 0;
    }
    long now = owner.getGameTimeSafe();
    long requestedAt = Math.max(0L, now - Math.max(0L, ageTicks));
    ItemStack key = makeKey(stackKey);
    upsertBaseline(key, 0);
    inflightEntries.add(
        new InflightEntry(
            key, Math.max(1, amount), requestedAt, sanitize(requesterName), sanitize(address)));
    compactInflightEntriesForPromptStability();
    owner.setChanged();
    return amount;
  }

  /** Debug helper: returns the oldest active inflight tuple, regardless of overdue state. */
  @Nullable
  CreateShopBlockEntity.InflightNotice debugPeekOldestInflightNotice(long now) {
    if (!owner.ensureServerThread("debugPeekOldestInflightNotice")) {
      return null;
    }
    InflightEntry selected = null;
    for (InflightEntry entry : inflightEntries) {
      if (entry == null || entry.remaining <= 0) {
        continue;
      }
      if (selected == null || entry.requestedAt < selected.requestedAt) {
        selected = entry;
      }
    }
    if (selected == null) {
      return null;
    }
    long age = Math.max(0L, now - selected.requestedAt);
    return new CreateShopBlockEntity.InflightNotice(
        selected.stackKey.copy(),
        selected.remaining,
        age,
        selected.requesterName,
        selected.address,
        selected.requestedAt,
        selected.requestUuid);
  }

  int size() {
    return inflightEntries.size() + inflightBaselines.size();
  }

  void clear() {
    inflightEntries.clear();
    inflightBaselines.clear();
  }

  void load(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
    inflightEntries.clear();
    if (tag.contains(TAG_INFLIGHT)) {
      var list = tag.getList(TAG_INFLIGHT, net.minecraft.nbt.Tag.TAG_COMPOUND);
      for (int i = 0; i < list.size(); i++) {
        CompoundTag entry = list.getCompound(i);
        ItemStack stack =
            ItemStack.parse(registries, entry.getCompound("stack")).orElse(ItemStack.EMPTY);
        int remaining = entry.getInt("remaining");
        long requestedAt = entry.getLong("requestedAt");
        String requester = entry.getString("requester");
        String address = entry.getString("address");
        if (!stack.isEmpty() && remaining > 0) {
          UUID requestUuid = entry.hasUUID("requestUuid") ? entry.getUUID("requestUuid") : null;
          InflightEntry inflight =
              new InflightEntry(
                  makeKey(stack), remaining, requestedAt, requester, address, requestUuid);
          // Interactions are not reliably restored across reload; re-arm overdue prompting for
          // still-open inflight entries after world load.
          inflight.notified = false;
          inflight.handedOff = entry.getBoolean("handedOff");
          inflightEntries.add(inflight);
        }
      }
    }
    compactInflightEntriesForPromptStability();
    inflightBaselines.clear();
    if (tag.contains(TAG_INFLIGHT_BASELINES)) {
      var list = tag.getList(TAG_INFLIGHT_BASELINES, net.minecraft.nbt.Tag.TAG_COMPOUND);
      for (int i = 0; i < list.size(); i++) {
        CompoundTag entry = list.getCompound(i);
        ItemStack stack =
            ItemStack.parse(registries, entry.getCompound("stack")).orElse(ItemStack.EMPTY);
        int count = entry.getInt("count");
        if (!stack.isEmpty()) {
          inflightBaselines.add(new BaselineEntry(makeKey(stack), Math.max(0, count)));
        }
      }
    }
  }

  void save(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
    if (!inflightEntries.isEmpty()) {
      net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
      for (InflightEntry entry : inflightEntries) {
        CompoundTag data = new CompoundTag();
        data.put("stack", entry.stackKey.save(registries));
        data.putInt("remaining", entry.remaining);
        data.putLong("requestedAt", entry.requestedAt);
        if (entry.notified) {
          data.putBoolean("notified", true);
        }
        if (entry.handedOff) {
          data.putBoolean("handedOff", true);
        }
        if (entry.requesterName != null && !entry.requesterName.isEmpty()) {
          data.putString("requester", entry.requesterName);
        }
        if (entry.address != null && !entry.address.isEmpty()) {
          data.putString("address", entry.address);
        }
        if (entry.requestUuid != null) {
          data.putUUID("requestUuid", entry.requestUuid);
        }
        list.add(data);
      }
      tag.put(TAG_INFLIGHT, list);
    }
    if (!inflightBaselines.isEmpty()) {
      net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
      for (BaselineEntry entry : inflightBaselines) {
        CompoundTag data = new CompoundTag();
        data.put("stack", entry.stackKey.save(registries));
        data.putInt("count", entry.count);
        list.add(data);
      }
      tag.put(TAG_INFLIGHT_BASELINES, list);
    }
  }

  private int cancelInflightMatches(
      ItemStack stackKey, String requester, String destination, long requestedAt) {
    boolean requireExactItemMatch = requester.isEmpty() && destination.isEmpty();
    int removed = 0;
    Iterator<InflightEntry> iterator = inflightEntries.iterator();
    while (iterator.hasNext()) {
      InflightEntry entry = iterator.next();
      if (!matchesForInflightLookup(entry.stackKey, stackKey, requireExactItemMatch)) {
        continue;
      }
      if (!matchesInflightTuple(entry, requester, destination, requestedAt)) {
        continue;
      }
      removed += Math.max(0, entry.remaining);
      iterator.remove();
    }
    return removed;
  }

  private int consumeInflightMatches(
      ItemStack stackKey, int remaining, String requester, String destination, long requestedAt) {
    boolean requireExactItemMatch = requester.isEmpty() && destination.isEmpty();
    Iterator<InflightEntry> iterator = inflightEntries.iterator();
    while (iterator.hasNext() && remaining > 0) {
      InflightEntry entry = iterator.next();
      if (!matchesForInflightLookup(entry.stackKey, stackKey, requireExactItemMatch)) {
        continue;
      }
      if (!matchesInflightTuple(entry, requester, destination, requestedAt)) {
        continue;
      }
      int used = Math.min(remaining, entry.remaining);
      entry.remaining -= used;
      remaining -= used;
      if (entry.remaining <= 0) {
        iterator.remove();
      } else if (used > 0) {
        // Partial inflight consumption must be promptable again for the unresolved remainder.
        entry.notified = false;
      }
    }
    return remaining;
  }

  private void ensureBaselines(Map<ItemStack, Integer> currentCounts) {
    for (InflightEntry entry : inflightEntries) {
      if (entry.stackKey == null || entry.stackKey.isEmpty()) {
        continue;
      }
      if (findBaseline(entry.stackKey) == null) {
        int current = findCount(currentCounts, entry.stackKey);
        inflightBaselines.add(new BaselineEntry(entry.stackKey.copy(), current));
      }
    }
  }

  private boolean pruneBaselines() {
    boolean changed = false;
    Iterator<BaselineEntry> iterator = inflightBaselines.iterator();
    while (iterator.hasNext()) {
      BaselineEntry baseline = iterator.next();
      if (!hasInflightFor(baseline.stackKey)) {
        iterator.remove();
        changed = true;
      }
    }
    return changed;
  }

  private boolean hasInflightFor(ItemStack key) {
    for (InflightEntry entry : inflightEntries) {
      if (matches(entry.stackKey, key)) {
        return true;
      }
    }
    return false;
  }

  private BaselineEntry findBaseline(ItemStack key) {
    for (BaselineEntry baseline : inflightBaselines) {
      if (matches(baseline.stackKey, key)) {
        return baseline;
      }
    }
    return null;
  }

  private void upsertBaseline(ItemStack key, int count) {
    BaselineEntry existing = findBaseline(key);
    if (existing != null) {
      existing.count = count;
      return;
    }
    inflightBaselines.add(new BaselineEntry(key.copy(), count));
  }

  private boolean shouldLogOverdue(long now) {
    if (!Config.DEBUG_LOGGING.getAsBoolean()) {
      return false;
    }
    if (now == 0L) {
      return false;
    }
    return now - lastInflightLogTime >= Config.INFLIGHT_LOG_COOLDOWN.getAsLong();
  }

  private void logOverdue(long now) {
    long timeout = Config.INFLIGHT_TIMEOUT_TICKS.getAsLong();
    if (timeout <= 0L) {
      return;
    }
    List<String> entries = new ArrayList<>();
    for (InflightEntry entry : inflightEntries) {
      if (entry.remaining <= 0) {
        continue;
      }
      long age = now - entry.requestedAt;
      if (age < timeout) {
        continue;
      }
      String label =
          entry.stackKey.getHoverName().getString() + " x" + entry.remaining + " age=" + age;
      entries.add(label);
    }
    if (entries.isEmpty()) {
      return;
    }
    lastInflightLogTime = now;
    TheSettlerXCreate.LOGGER.info("[CreateShop] inflight overdue: {}", String.join(" | ", entries));
  }

  private static boolean matches(ItemStack a, ItemStack b) {
    return ItemStack.isSameItemSameComponents(a, b);
  }

  private static boolean matchesForInflightRecovery(ItemStack a, ItemStack b) {
    if (a == null || a.isEmpty() || b == null || b.isEmpty()) {
      return false;
    }
    if (matches(a, b)) {
      return true;
    }
    return ItemStack.isSameItem(a, b);
  }

  /**
   * Seam-audit finding s1-5: matching by item type alone (ignoring components) is only trustworthy
   * when the requester/address tuple also positively confirms which request an entry belongs to.
   * Once that tuple is dropped - the drift-recovery fallback both {@link #getInflightRemaining(
   * ItemStack, String, String, long)} and {@link #consumeInflightMatches} use when a citizen
   * rename/reassignment makes the recorded requester/address stop matching - item type is the only
   * signal left, so loosening it too would let two unrelated requests for component-different
   * variants of the same item (e.g. differently enchanted books) consume each other's inflight
   * entries. Callers pass {@code requireExactItemMatch = requester.isEmpty() &&
   * destination.isEmpty()} to require an exact match precisely in that degraded case.
   */
  private static boolean matchesForInflightLookup(
      ItemStack entryStack, ItemStack stackKey, boolean requireExactItemMatch) {
    if (requireExactItemMatch) {
      return matches(entryStack, stackKey);
    }
    return matchesForInflightRecovery(entryStack, stackKey);
  }

  /**
   * The requester/destination/requestedAt half of an inflight lookup - shared by {@link
   * #getInflightRemaining(ItemStack, String, String, long)}, {@link #cancelInflightMatches}, and
   * {@link #consumeInflightMatches}, each of which still does its own item-matching call (see
   * {@link #matchesForInflightLookup}) right before this, since the two matches vary independently
   * (a caller may want loose item matching with a strict tuple filter, or vice versa).
   */
  private static boolean matchesInflightTuple(
      InflightEntry entry, String requester, String destination, long requestedAt) {
    if (!requester.isEmpty() && !requester.equals(entry.requesterName)) {
      return false;
    }
    if (!destination.isEmpty() && !destination.equals(entry.address)) {
      return false;
    }
    return requestedAt <= 0L || entry.requestedAt == requestedAt;
  }

  private static boolean containsKey(List<ItemStack> keys, ItemStack key) {
    for (ItemStack existing : keys) {
      if (matches(existing, key)) {
        return true;
      }
    }
    return false;
  }

  private static int findCount(Map<ItemStack, Integer> counts, ItemStack key) {
    if (counts == null || counts.isEmpty() || key == null || key.isEmpty()) {
      return 0;
    }
    for (Map.Entry<ItemStack, Integer> entry : counts.entrySet()) {
      if (matches(entry.getKey(), key)) {
        return entry.getValue();
      }
    }
    return 0;
  }

  private static ItemStack makeKey(ItemStack stack) {
    ItemStack copy = stack.copy();
    copy.setCount(1);
    return copy;
  }

  private static String sanitize(String value) {
    return com.thesettler_x_create.TextUtil.sanitize(value);
  }

  private static String buildNoticeSegmentKey(
      ItemStack stackKey, String requesterName, String address, long requestedAt, int remaining) {
    if (stackKey == null || stackKey.isEmpty()) {
      return "minecraft:air|||" + requestedAt + "|" + remaining;
    }
    String itemId =
        String.valueOf(
            net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stackKey.getItem()));
    String requester = sanitize(requesterName);
    String destination = sanitize(address);
    return itemId + "|" + requester + "|" + destination + "|" + requestedAt + "|" + remaining;
  }

  private static String buildNoticePromptKey(ItemStack stackKey, String address) {
    if (stackKey == null || stackKey.isEmpty()) {
      return "minecraft:air|";
    }
    String itemId =
        String.valueOf(
            net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stackKey.getItem()));
    String destination = sanitize(address);
    return itemId + "|" + destination;
  }

  private static String buildNoticeTupleKey(
      ItemStack stackKey, String requesterName, String address) {
    if (stackKey == null || stackKey.isEmpty()) {
      return "minecraft:air||";
    }
    String itemId =
        String.valueOf(
            net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stackKey.getItem()));
    String requester = sanitize(requesterName);
    String destination = sanitize(address);
    return itemId + "|" + requester + "|" + destination;
  }

  private boolean compactInflightEntriesForPromptStability() {
    if (inflightEntries.size() <= 1) {
      return false;
    }
    boolean changed = false;
    Map<String, InflightEntry> unique = new java.util.LinkedHashMap<>();
    for (InflightEntry entry : inflightEntries) {
      if (entry == null
          || entry.stackKey == null
          || entry.stackKey.isEmpty()
          || entry.remaining <= 0) {
        changed = true;
        continue;
      }
      String segmentKey =
          buildNoticeSegmentKey(
              entry.stackKey,
              entry.requesterName,
              entry.address,
              entry.requestedAt,
              entry.remaining);
      InflightEntry existing = unique.get(segmentKey);
      if (existing == null) {
        unique.put(segmentKey, entry);
      } else {
        existing.notified = existing.notified || entry.notified;
        changed = true;
      }
    }

    List<InflightEntry> sorted = new ArrayList<>(unique.values());
    sorted.sort((left, right) -> Long.compare(right.requestedAt, left.requestedAt));
    Map<String, Integer> keptPerTuple = new HashMap<>();
    List<InflightEntry> compacted = new ArrayList<>(sorted.size());
    for (InflightEntry entry : sorted) {
      String tupleKey = buildNoticeTupleKey(entry.stackKey, entry.requesterName, entry.address);
      int kept = keptPerTuple.getOrDefault(tupleKey, 0);
      if (kept >= MAX_OPEN_INFLIGHT_SEGMENTS_PER_TUPLE) {
        changed = true;
        continue;
      }
      keptPerTuple.put(tupleKey, kept + 1);
      compacted.add(entry);
    }
    compacted.sort((left, right) -> Long.compare(left.requestedAt, right.requestedAt));
    if (!changed && compacted.size() == inflightEntries.size()) {
      return false;
    }
    inflightEntries.clear();
    inflightEntries.addAll(compacted);
    return true;
  }

  static class InflightEntry {
    final ItemStack stackKey;
    int remaining;
    final long requestedAt;
    final String requesterName;
    final String address;
    boolean notified;

    /** UUID of the MineColonies request that created this entry. Null for legacy entries. */
    @Nullable UUID requestUuid;

    /** True once DELIVERY_CREATED — MC owns the courier lifecycle from this point on. */
    boolean handedOff;

    InflightEntry(
        ItemStack stackKey, int remaining, long requestedAt, String requesterName, String address) {
      this(stackKey, remaining, requestedAt, requesterName, address, null);
    }

    InflightEntry(
        ItemStack stackKey,
        int remaining,
        long requestedAt,
        String requesterName,
        String address,
        @Nullable UUID requestUuid) {
      this.stackKey = stackKey;
      this.remaining = remaining;
      this.requestedAt = requestedAt;
      this.requesterName = requesterName == null ? "" : requesterName;
      this.address = address == null ? "" : address;
      this.notified = false;
      this.requestUuid = requestUuid;
    }
  }

  static class BaselineEntry {
    final ItemStack stackKey;
    int count;

    BaselineEntry(ItemStack stackKey, int count) {
      this.stackKey = stackKey;
      this.count = count;
    }
  }
}
