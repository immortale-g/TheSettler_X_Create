package com.thesettler_x_create.stock.nbt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.thesettler_x_create.stock.ReservedAmount;
import com.thesettler_x_create.stock.StoredReservation;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

class ReservationNbtTest {
  private static final UUID REQUEST_A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
  private static final UUID REQUEST_B = UUID.fromString("00000000-0000-0000-0000-00000000000b");

  @Test
  void roundTripKeepsSeveralItemKindsPerRequest() {
    List<StoredReservation<String>> stored =
        List.of(
            new StoredReservation<>(
                REQUEST_A,
                12_345L,
                List.of(
                    new ReservedAmount<>("oak_log", 32), new ReservedAmount<>("birch_log", 16))),
            new StoredReservation<>(REQUEST_B, 99L, List.of(new ReservedAmount<>("brass", 64))));

    CompoundTag tag = ReservationNbt.write(stored, StringTag::valueOf);
    List<StoredReservation<String>> read = ReservationNbt.read(tag, ReservationNbtTest::readKey);

    assertEquals(
        stored.stream().sorted((a, b) -> a.owner().compareTo(b.owner())).toList(),
        read.stream().sorted((a, b) -> a.owner().compareTo(b.owner())).toList());
  }

  @Test
  void readsTheSingleItemFormatUpTo03x() {
    CompoundTag legacyEntry = new CompoundTag();
    legacyEntry.put("stack", StringTag.valueOf("brass"));
    legacyEntry.putInt("amount", 256);
    legacyEntry.putLong("expires", 6_000L);
    CompoundTag tag = new CompoundTag();
    tag.put(REQUEST_A.toString(), legacyEntry);

    List<StoredReservation<String>> read = ReservationNbt.read(tag, ReservationNbtTest::readKey);

    assertEquals(
        List.of(
            new StoredReservation<>(
                REQUEST_A, 6_000L, List.of(new ReservedAmount<>("brass", 256)))),
        read);
  }

  @Test
  void skipsMalformedIdsUnreadableItemsAndEmptyAmounts() {
    CompoundTag badId = new CompoundTag();
    badId.put("stack", StringTag.valueOf("brass"));
    badId.putInt("amount", 1);

    CompoundTag unreadable = new CompoundTag();
    unreadable.putInt("amount", 5);

    CompoundTag zeroAmount = new CompoundTag();
    zeroAmount.put("stack", StringTag.valueOf("brass"));
    zeroAmount.putInt("amount", 0);

    CompoundTag tag = new CompoundTag();
    tag.put("not-a-uuid", badId);
    tag.put(REQUEST_A.toString(), unreadable);
    tag.put(REQUEST_B.toString(), zeroAmount);

    assertTrue(ReservationNbt.read(tag, ReservationNbtTest::readKey).isEmpty());
  }

  private static Optional<String> readKey(Tag tag) {
    return tag instanceof StringTag stringTag
        ? Optional.of(stringTag.getAsString())
        : Optional.empty();
  }
}
