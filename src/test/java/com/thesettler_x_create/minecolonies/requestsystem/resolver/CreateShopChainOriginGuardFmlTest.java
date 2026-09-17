package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.requestsystem.location.ILocation;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.requestable.IDeliverable;
import com.minecolonies.api.colony.requestsystem.requestable.Stack;
import com.minecolonies.api.colony.requestsystem.requester.IRequester;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.core.colony.requestsystem.management.IStandardRequestManager;
import com.thesettler_x_create.minecolonies.building.BuildingCreateShop;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * A shop must not serve what a shop asked the colony for, or the goods a gauge pulls into the
 * Create network would be taken right back out of it. What a crafter needs to make them is another
 * item and stays available. Needs real ItemStacks, so it runs with a loaded FML.
 */
@Tag("fml")
class CreateShopChainOriginGuardFmlTest {
  private static final BlockPos SHOP_POS = new BlockPos(10, 64, 10);
  private static final BlockPos OTHER_POS = new BlockPos(30, 64, 30);

  private IStandardRequestManager manager;
  private IColony colony;
  private IRequest<?> gaugeRequest;
  private IToken<?> gaugeToken;

  @BeforeEach
  void setUp() {
    manager = mock(IStandardRequestManager.class, RETURNS_DEEP_STUBS);
    colony = mock(IColony.class, RETURNS_DEEP_STUBS);
    when(manager.getColony()).thenReturn(colony);

    IBuilding shop = mock(BuildingCreateShop.class);
    when(colony.getServerBuildingManager().getBuilding(SHOP_POS)).thenReturn(shop);
    when(colony.getServerBuildingManager().getBuilding(OTHER_POS))
        .thenReturn(mock(IBuilding.class));

    gaugeToken = mock(IToken.class);
    gaugeRequest = requestFor(new Stack(new ItemStack(Items.TORCH), 64, 64), SHOP_POS, null);
    when(gaugeRequest.getId()).thenReturn((IToken) gaugeToken);
    when(manager.getRequestHandler().getRequest(gaugeToken)).thenReturn((IRequest) gaugeRequest);
  }

  @Test
  void theShopsOwnOrderIsLeftAlone() {
    assertTrue(
        CreateShopChainOriginGuard.servesAShopsOwnOrder(
            manager, (IRequest<? extends IDeliverable>) gaugeRequest));
  }

  @Test
  void aChildForTheRestOfTheSameGoodsIsLeftAlone() {
    // What the warehouse could not cover comes back as its own request, made by the warehouse.
    IRequest<?> remainder =
        requestFor(new Stack(new ItemStack(Items.TORCH), 52, 52), OTHER_POS, gaugeToken);

    assertTrue(
        CreateShopChainOriginGuard.servesAShopsOwnOrder(
            manager, (IRequest<? extends IDeliverable>) remainder));
  }

  @Test
  void whatTheCrafterNeedsForItMayComeFromTheNetwork() {
    IRequest<?> ingredient =
        requestFor(new Stack(new ItemStack(Items.COAL), 16, 16), OTHER_POS, gaugeToken);

    assertFalse(
        CreateShopChainOriginGuard.servesAShopsOwnOrder(
            manager, (IRequest<? extends IDeliverable>) ingredient));
  }

  @Test
  void anOrderNoShopStartedIsNoneOfOurBusiness() {
    IRequest<?> citizenRequest =
        requestFor(new Stack(new ItemStack(Items.TORCH), 8, 8), OTHER_POS, null);

    assertFalse(
        CreateShopChainOriginGuard.servesAShopsOwnOrder(
            manager, (IRequest<? extends IDeliverable>) citizenRequest));
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private IRequest<?> requestFor(Stack deliverable, BlockPos requesterPos, IToken<?> parent) {
    IRequest<?> request = mock(IRequest.class);
    when(((IRequest) request).getRequest()).thenReturn(deliverable);

    ILocation location = mock(ILocation.class);
    when(location.getInDimensionLocation()).thenReturn(requesterPos);
    IRequester requester = mock(IRequester.class);
    when(requester.getLocation()).thenReturn(location);
    when(((IRequest) request).getRequester()).thenReturn(requester);

    when(request.hasParent()).thenReturn(parent != null);
    when(((IRequest) request).getParent()).thenReturn(parent);
    return request;
  }
}
