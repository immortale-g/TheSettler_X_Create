package com.thesettler_x_create.stock.nbt;

import com.thesettler_x_create.stock.StockAging;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/**
 * NBT form of the unreserved stock ages: {@code [{stack, amount, since}, ...]}, oldest first per
 * item kind. The item codec is passed in, so this class can be tested without a registry.
 */
public final class StockAgingNbt {
  static final String TAG_STACK = "stack";
  static final String TAG_AMOUNT = "amount";
  static final String TAG_SINCE = "since";

  private StockAgingNbt() {}

  public static <K> ListTag write(List<StockAging.Batch<K>> batches, Function<K, Tag> keyWriter) {
    ListTag list = new ListTag();
    for (StockAging.Batch<K> batch : batches) {
      CompoundTag entry = new CompoundTag();
      entry.put(TAG_STACK, keyWriter.apply(batch.key()));
      entry.putInt(TAG_AMOUNT, batch.amount());
      entry.putLong(TAG_SINCE, batch.sinceGameTime());
      list.add(entry);
    }
    return list;
  }

  /** Unreadable items and non-positive amounts are skipped. */
  public static <K> List<StockAging.Batch<K>> read(
      ListTag list, Function<Tag, Optional<K>> keyReader) {
    List<StockAging.Batch<K>> batches = new ArrayList<>();
    for (int i = 0; i < list.size(); i++) {
      CompoundTag entry = list.getCompound(i);
      int amount = entry.getInt(TAG_AMOUNT);
      if (amount <= 0) {
        continue;
      }
      long since = entry.getLong(TAG_SINCE);
      keyReader
          .apply(entry.get(TAG_STACK))
          .ifPresent(key -> batches.add(new StockAging.Batch<>(key, amount, since)));
    }
    return batches;
  }
}
