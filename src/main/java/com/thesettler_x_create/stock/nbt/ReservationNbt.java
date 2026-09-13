package com.thesettler_x_create.stock.nbt;

import com.thesettler_x_create.stock.ReservedAmount;
import com.thesettler_x_create.stock.StoredReservation;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/**
 * NBT form of the pickup reservations, one compound per request id.
 *
 * <p>Current format: {@code {<uuid>: {expires, entries: [{stack, amount}, ...]}}}. Up to 0.3.x a
 * request could only reserve one item kind and was saved as {@code {<uuid>: {stack, amount,
 * expires}}}; that format is still read. The item codec is passed in, so this class can be tested
 * without a registry.
 */
public final class ReservationNbt {
  static final String TAG_EXPIRES = "expires";
  static final String TAG_ENTRIES = "entries";
  static final String TAG_STACK = "stack";
  static final String TAG_AMOUNT = "amount";

  private ReservationNbt() {}

  public static <K> CompoundTag write(
      List<StoredReservation<K>> stored, Function<K, Tag> keyWriter) {
    CompoundTag reservationsTag = new CompoundTag();
    for (StoredReservation<K> reservation : stored) {
      ListTag entries = new ListTag();
      for (ReservedAmount<K> amount : reservation.amounts()) {
        CompoundTag entry = new CompoundTag();
        entry.put(TAG_STACK, keyWriter.apply(amount.key()));
        entry.putInt(TAG_AMOUNT, amount.amount());
        entries.add(entry);
      }
      CompoundTag data = new CompoundTag();
      data.putLong(TAG_EXPIRES, reservation.expiresAtGameTime());
      data.put(TAG_ENTRIES, entries);
      reservationsTag.put(reservation.owner().toString(), data);
    }
    return reservationsTag;
  }

  /**
   * Reads both the current and the pre-0.4 format. Malformed request ids, unreadable items and
   * non-positive amounts are skipped.
   *
   * @param keyReader turns a saved item tag (possibly {@code null}) into a key
   */
  public static <K> List<StoredReservation<K>> read(
      CompoundTag reservationsTag, Function<Tag, Optional<K>> keyReader) {
    List<StoredReservation<K>> stored = new ArrayList<>();
    for (String ownerKey : reservationsTag.getAllKeys()) {
      UUID owner;
      try {
        owner = UUID.fromString(ownerKey);
      } catch (IllegalArgumentException ignored) {
        continue;
      }
      CompoundTag data = reservationsTag.getCompound(ownerKey);
      List<ReservedAmount<K>> amounts = new ArrayList<>();
      if (data.contains(TAG_ENTRIES, Tag.TAG_LIST)) {
        ListTag entries = data.getList(TAG_ENTRIES, Tag.TAG_COMPOUND);
        for (int i = 0; i < entries.size(); i++) {
          readAmount(entries.getCompound(i), keyReader).ifPresent(amounts::add);
        }
      } else {
        readAmount(data, keyReader).ifPresent(amounts::add);
      }
      if (!amounts.isEmpty()) {
        stored.add(new StoredReservation<>(owner, data.getLong(TAG_EXPIRES), amounts));
      }
    }
    return stored;
  }

  private static <K> Optional<ReservedAmount<K>> readAmount(
      CompoundTag entry, Function<Tag, Optional<K>> keyReader) {
    int amount = entry.getInt(TAG_AMOUNT);
    if (amount <= 0) {
      return Optional.empty();
    }
    return keyReader.apply(entry.get(TAG_STACK)).map(key -> new ReservedAmount<>(key, amount));
  }
}
