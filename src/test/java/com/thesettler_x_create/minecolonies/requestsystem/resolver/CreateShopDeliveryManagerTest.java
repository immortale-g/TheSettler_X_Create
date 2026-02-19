package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.minecolonies.api.colony.requestsystem.location.ILocation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

class CreateShopDeliveryManagerTest {
  @Test
  void detectsSelfLoopWhenPickupAndTargetMatch() {
    ILocation targetLocation = mock(ILocation.class);
    Level pickupLevel = mock(Level.class);

    BlockPos samePos = new BlockPos(10, 64, 10);
    when(pickupLevel.dimension()).thenReturn(Level.OVERWORLD);
    when(targetLocation.getDimension()).thenReturn(Level.OVERWORLD);
    when(targetLocation.getInDimensionLocation()).thenReturn(samePos);

    assertTrue(
        CreateShopDeliveryManager.isSelfLoopDeliveryTarget(pickupLevel, samePos, targetLocation));
  }

  @Test
  void returnsFalseForDifferentTargetsOrMissingInputs() {
    ILocation targetLocation = mock(ILocation.class);
    Level pickupLevel = mock(Level.class);

    BlockPos startPos = new BlockPos(10, 64, 10);
    BlockPos otherPos = new BlockPos(11, 64, 10);
    when(pickupLevel.dimension()).thenReturn(Level.OVERWORLD);
    when(targetLocation.getDimension()).thenReturn(Level.OVERWORLD);
    when(targetLocation.getInDimensionLocation()).thenReturn(otherPos);

    assertFalse(
        CreateShopDeliveryManager.isSelfLoopDeliveryTarget(pickupLevel, startPos, targetLocation));
    assertFalse(CreateShopDeliveryManager.isSelfLoopDeliveryTarget(null, startPos, targetLocation));
    assertFalse(
        CreateShopDeliveryManager.isSelfLoopDeliveryTarget(pickupLevel, null, targetLocation));
    assertFalse(CreateShopDeliveryManager.isSelfLoopDeliveryTarget(pickupLevel, startPos, null));
  }
}
