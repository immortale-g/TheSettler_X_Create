package com.thesettler_x_create.stock.nbt;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.thesettler_x_create.stock.InflightBook;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

class InflightNbtTest {
  private static final UUID REQUEST_A = UUID.fromString("00000000-0000-0000-0000-00000000000a");

  @Test
  void roundTripKeepsOwnersTuplesAndBaselines() {
    List<InflightBook.StoredEntry<String>> entries =
        List.of(
            new InflightBook.StoredEntry<>("iron", 64, 100L, "Bob", "shop", REQUEST_A, true),
            new InflightBook.StoredEntry<>("gold", 8, 200L, "", "", null, false));
    List<InflightBook.StoredBaseline<String>> baselines =
        List.of(new InflightBook.StoredBaseline<>("iron", 12));

    ListTag entryTag = InflightNbt.writeEntries(entries, StringTag::valueOf);
    ListTag baselineTag = InflightNbt.writeBaselines(baselines, StringTag::valueOf);

    assertEquals(entries, InflightNbt.readEntries(entryTag, InflightNbtTest::readKey));
    assertEquals(baselines, InflightNbt.readBaselines(baselineTag, InflightNbtTest::readKey));
  }

  @Test
  void readsTheFormatWrittenUpTo04WithItsHandedOffFlag() {
    CompoundTag legacy = new CompoundTag();
    legacy.put("stack", StringTag.valueOf("iron"));
    legacy.putInt("remaining", 32);
    legacy.putLong("requestedAt", 7L);
    legacy.putString("requester", "Bob");
    legacy.putBoolean("handedOff", true);
    legacy.putUUID("requestUuid", REQUEST_A);
    CompoundTag empty = new CompoundTag();
    empty.put("stack", StringTag.valueOf("gold"));
    ListTag list = new ListTag();
    list.add(legacy);
    list.add(empty);

    assertEquals(
        List.of(new InflightBook.StoredEntry<>("iron", 32, 7L, "Bob", "", REQUEST_A, false)),
        InflightNbt.readEntries(list, InflightNbtTest::readKey));
  }

  private static Optional<String> readKey(Tag tag) {
    return tag instanceof StringTag stringTag
        ? Optional.of(stringTag.getAsString())
        : Optional.empty();
  }
}
