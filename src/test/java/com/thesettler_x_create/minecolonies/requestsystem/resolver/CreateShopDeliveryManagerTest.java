package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.minecolonies.api.colony.requestsystem.location.ILocation;
import java.nio.file.Files;
import java.nio.file.Path;
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

  /**
   * All deliveries of a request start at the shop hut and are created in one go. Rack positions as
   * start split one request into one courier trip per rack, and a single child per request made
   * large orders run one stack after the other.
   */
  @Test
  void deliveryCreationStartsAtTheHutAndCreatesEveryPlannedStack() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/requestsystem/resolver/CreateShopDeliveryManager.java"));

    assertTrue(source.contains("BlockPos startPos = shop.getLocation().getInDimensionLocation();"));
    assertTrue(source.contains("CreateShopDeliveryPlanner.toDeliveryStacks(stacks)"));
    assertTrue(source.contains("for (ItemStack deliveryStack : deliveryStacks) {"));
    assertFalse(source.contains("startPos = entry.getB();"));
    assertFalse(source.contains("if (request.hasChildren()) {"));
  }

  /**
   * The callers decide whether a request may get more deliveries, which is why the manager does not
   * check. Without open children the regular path plans them; with open children only the
   * open-delivery path does, and it subtracts what the open deliveries still hold.
   */
  @Test
  void callersOnlyPlanDeliveriesForRequestsWithoutOpenChildren() throws Exception {
    String attemptResolve =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/requestsystem/resolver/CreateShopAttemptResolveService.java"));
    String pendingProcessor =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/requestsystem/resolver/CreateShopPendingRequestProcessorService.java"));

    int attemptGuard = attemptResolve.indexOf("if (request.hasChildren()) {");
    int attemptCreate = attemptResolve.indexOf("deliveryManager.createDeliveriesFromStacks(");
    assertTrue(attemptGuard >= 0 && attemptGuard < attemptCreate);

    int pendingGuard =
        pendingProcessor.indexOf("if (childResult.hasActiveChildren() || request.hasChildren()) {");
    int pendingCreate = pendingProcessor.indexOf("pendingDeliveryCreationService.process(");
    assertTrue(pendingGuard >= 0 && pendingGuard < pendingCreate);
    int openDeliveryPath = pendingProcessor.indexOf("openDeliveryTopupService.process(");
    assertTrue(pendingGuard < openDeliveryPath && openDeliveryPath < pendingCreate);

    String openDelivery =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/requestsystem/resolver/CreateShopOpenDeliveryTopupService.java"));
    assertTrue(openDelivery.contains("OpenDeliveryPlan.of("));
    assertTrue(openDelivery.contains("if (plan.deliverNow() > 0) {"));
    assertTrue(openDelivery.contains("ledger.pickupConfirmedAtTick < 0L"));
  }
}
