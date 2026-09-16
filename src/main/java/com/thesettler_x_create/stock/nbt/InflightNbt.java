package com.thesettler_x_create.stock.nbt;

import com.thesettler_x_create.stock.InflightBook;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/**
 * NBT form of the inflight orders: {@code [{stack, remaining, requestedAt, requester?, address?,
 * requestUuid?, notified?}, ...]} and baselines {@code [{stack, count}, ...]}. The format is the
 * one used since 0.3; the old {@code handedOff} flag is ignored. The item codec is passed in, so
 * this class can be tested without a registry.
 */
public final class InflightNbt {
  static final String TAG_STACK = "stack";
  static final String TAG_REMAINING = "remaining";
  static final String TAG_REQUESTED_AT = "requestedAt";
  static final String TAG_REQUESTER = "requester";
  static final String TAG_ADDRESS = "address";
  static final String TAG_REQUEST_UUID = "requestUuid";
  static final String TAG_NOTIFIED = "notified";
  static final String TAG_COUNT = "count";

  private InflightNbt() {}

  public static <K> ListTag writeEntries(
      List<InflightBook.StoredEntry<K>> entries, Function<K, Tag> keyWriter) {
    ListTag list = new ListTag();
    for (InflightBook.StoredEntry<K> entry : entries) {
      CompoundTag data = new CompoundTag();
      data.put(TAG_STACK, keyWriter.apply(entry.key()));
      data.putInt(TAG_REMAINING, entry.remaining());
      data.putLong(TAG_REQUESTED_AT, entry.requestedAt());
      if (entry.notified()) {
        data.putBoolean(TAG_NOTIFIED, true);
      }
      if (entry.requester() != null && !entry.requester().isEmpty()) {
        data.putString(TAG_REQUESTER, entry.requester());
      }
      if (entry.address() != null && !entry.address().isEmpty()) {
        data.putString(TAG_ADDRESS, entry.address());
      }
      if (entry.owner() != null) {
        data.putUUID(TAG_REQUEST_UUID, entry.owner());
      }
      list.add(data);
    }
    return list;
  }

  /** Unreadable items and non-positive amounts are skipped. */
  public static <K> List<InflightBook.StoredEntry<K>> readEntries(
      ListTag list, Function<Tag, Optional<K>> keyReader) {
    List<InflightBook.StoredEntry<K>> entries = new ArrayList<>();
    for (int i = 0; i < list.size(); i++) {
      CompoundTag data = list.getCompound(i);
      int remaining = data.getInt(TAG_REMAINING);
      if (remaining <= 0) {
        continue;
      }
      UUID owner = data.hasUUID(TAG_REQUEST_UUID) ? data.getUUID(TAG_REQUEST_UUID) : null;
      keyReader
          .apply(data.get(TAG_STACK))
          .ifPresent(
              key ->
                  entries.add(
                      new InflightBook.StoredEntry<>(
                          key,
                          remaining,
                          data.getLong(TAG_REQUESTED_AT),
                          data.getString(TAG_REQUESTER),
                          data.getString(TAG_ADDRESS),
                          owner,
                          data.getBoolean(TAG_NOTIFIED))));
    }
    return entries;
  }

  public static <K> ListTag writeBaselines(
      List<InflightBook.StoredBaseline<K>> baselines, Function<K, Tag> keyWriter) {
    ListTag list = new ListTag();
    for (InflightBook.StoredBaseline<K> baseline : baselines) {
      CompoundTag data = new CompoundTag();
      data.put(TAG_STACK, keyWriter.apply(baseline.key()));
      data.putInt(TAG_COUNT, baseline.count());
      list.add(data);
    }
    return list;
  }

  public static <K> List<InflightBook.StoredBaseline<K>> readBaselines(
      ListTag list, Function<Tag, Optional<K>> keyReader) {
    List<InflightBook.StoredBaseline<K>> baselines = new ArrayList<>();
    for (int i = 0; i < list.size(); i++) {
      CompoundTag data = list.getCompound(i);
      int count = Math.max(0, data.getInt(TAG_COUNT));
      keyReader
          .apply(data.get(TAG_STACK))
          .ifPresent(key -> baselines.add(new InflightBook.StoredBaseline<>(key, count)));
    }
    return baselines;
  }
}
