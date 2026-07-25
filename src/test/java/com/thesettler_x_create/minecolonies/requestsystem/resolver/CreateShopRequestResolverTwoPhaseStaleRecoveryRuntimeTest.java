package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.minecolonies.api.colony.requestsystem.location.ILocation;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * After Phase 3.5, the two-phase stale recovery (arm → recheck delay → mutate) has been removed.
 * MineColonies owns the delivery lifecycle after DELIVERY_CREATED; the resolver no longer forces
 * recovery based on elapsed time. This test confirms the removal and the absence of stale state in
 * a freshly-created resolver.
 */
class CreateShopRequestResolverTwoPhaseStaleRecoveryRuntimeTest {
  private CreateShopRequestResolver resolver;

  @BeforeEach
  void setUp() {
    ILocation resolverLocation = mock(ILocation.class);
    when(resolverLocation.getDimension()).thenReturn(Level.OVERWORLD);
    when(resolverLocation.getInDimensionLocation()).thenReturn(BlockPos.ZERO);
    IToken<?> resolverToken = mock(IToken.class);
    resolver = new CreateShopRequestResolver(resolverLocation, resolverToken);
  }

  @Test
  void staleRecoveryArmAndClockAreAbsentFromSourceAndResolver() throws Exception {
    String storeSource =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/requestsystem/resolver/CreateShopLifecycleStateStore.java"));
    String mutatorSource =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/requestsystem/resolver/CreateShopRequestStateMutatorService.java"));

    assertFalse(storeSource.contains("parentStaleRecoveryArmedAt"));
    assertFalse(storeSource.contains("parentDeliveryActiveSince"));
    assertFalse(mutatorSource.contains("armStaleRecoveryIfMissing"));
    assertFalse(mutatorSource.contains("clearStaleRecoveryArm"));
  }

  @Test
  void resolverHasNoActiveChildTrackingAfterCreation() {
    // The active-child set (deliveryChildActiveSince) has been removed.
    // hasProtectedInventoryWindow now relies only on hasActiveWork + pendingTracker.
    assertFalse(resolver.hasProtectedInventoryWindow());
  }
}
