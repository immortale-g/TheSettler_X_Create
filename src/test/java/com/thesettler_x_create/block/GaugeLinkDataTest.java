package com.thesettler_x_create.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class GaugeLinkDataTest {

  @Test
  void writeThenReadRoundTrips() {
    CompoundTag tag = new CompoundTag();
    BlockPos pos = new BlockPos(12, 34, -56);
    GaugeLinkData.writeTo(tag, 7, pos);

    assertTrue(GaugeLinkData.isLinked(tag));
    GaugeLinkData link = GaugeLinkData.readFrom(tag);
    assertEquals(7, link.colonyId());
    assertEquals(pos, link.shopPos());
  }

  @Test
  void unlinkedDataIsNotLinked() {
    assertFalse(GaugeLinkData.isLinked(new CompoundTag()));
    assertFalse(GaugeLinkData.isLinked(null));
  }
}
