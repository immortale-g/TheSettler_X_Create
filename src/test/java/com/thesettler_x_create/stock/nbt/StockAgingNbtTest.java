package com.thesettler_x_create.stock.nbt;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.thesettler_x_create.stock.StockAging;
import java.util.List;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

class StockAgingNbtTest {

  @Test
  void roundTripKeepsAmountsAndAges() {
    List<StockAging.Batch<String>> batches =
        List.of(
            new StockAging.Batch<>("iron", 64, 1_000L),
            new StockAging.Batch<>("iron", 36, 4_000L),
            new StockAging.Batch<>("gold", 8, 12L));

    ListTag tag = StockAgingNbt.write(batches, StringTag::valueOf);

    assertEquals(batches, StockAgingNbt.read(tag, StockAgingNbtTest::readKey));
  }

  @Test
  void skipsUnreadableItemsAndEmptyAmounts() {
    CompoundTag unreadable = new CompoundTag();
    unreadable.putInt("amount", 5);
    CompoundTag empty = new CompoundTag();
    empty.put("stack", StringTag.valueOf("iron"));
    empty.putInt("amount", 0);
    ListTag tag = new ListTag();
    tag.add(unreadable);
    tag.add(empty);

    assertEquals(List.of(), StockAgingNbt.read(tag, StockAgingNbtTest::readKey));
  }

  private static Optional<String> readKey(Tag tag) {
    return tag instanceof StringTag stringTag
        ? Optional.of(stringTag.getAsString())
        : Optional.empty();
  }
}
