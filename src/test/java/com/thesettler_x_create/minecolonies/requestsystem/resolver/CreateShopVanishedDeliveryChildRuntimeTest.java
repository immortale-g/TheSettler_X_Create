package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.requestsystem.location.ILocation;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.request.RequestState;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.core.colony.buildings.modules.CourierAssignmentModule;
import com.minecolonies.core.colony.jobs.JobDeliveryman;
import com.minecolonies.core.colony.requestsystem.management.IStandardRequestManager;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

class CreateShopVanishedDeliveryChildRuntimeTest {
  private final CreateShopRequestStateMutatorService mutator =
      new CreateShopRequestStateMutatorService();

  @Test
  @SuppressWarnings({"rawtypes", "unchecked"})
  void childStillHeldByMineColoniesCountsAsUnfinished() {
    IStandardRequestManager manager = mock(IStandardRequestManager.class, RETURNS_DEEP_STUBS);
    IToken<?> child = mock(IToken.class);
    IRequest request = mock(IRequest.class);
    when(manager.getRequestHandler().getRequestOrNull(child)).thenReturn(request);

    when(request.getState()).thenReturn(RequestState.IN_PROGRESS);
    assertTrue(mutator.isUnfinishedDeliveryChild(manager, child));

    when(request.getState()).thenReturn(RequestState.COMPLETED);
    assertFalse(mutator.isUnfinishedDeliveryChild(manager, child));
  }

  @Test
  void childMineColoniesNoLongerKnowsIsNotUnfinished() {
    IStandardRequestManager manager = mock(IStandardRequestManager.class, RETURNS_DEEP_STUBS);
    IToken<?> child = mock(IToken.class);
    when(manager.getRequestHandler().getRequestOrNull(child)).thenReturn(null);

    assertFalse(mutator.isUnfinishedDeliveryChild(manager, child));
  }

  @Test
  @SuppressWarnings({"rawtypes", "unchecked"})
  void forgettingAVanishedChildLeavesCourierAndRequestStateAlone() {
    ILocation location = mock(ILocation.class);
    when(location.getDimension()).thenReturn(Level.OVERWORLD);
    when(location.getInDimensionLocation()).thenReturn(BlockPos.ZERO);
    CreateShopRequestResolver resolver =
        new CreateShopRequestResolver(location, mock(IToken.class));

    IStandardRequestManager manager = mock(IStandardRequestManager.class, RETURNS_DEEP_STUBS);
    IToken child = mock(IToken.class);
    when(manager.getRequestHandler().getRequestOrNull(child)).thenReturn(null);

    JobDeliveryman job = mock(JobDeliveryman.class);
    when(job.getTaskQueue()).thenReturn(List.of(child));
    ICitizenData courier = mock(ICitizenData.class);
    when(courier.getJob()).thenReturn((com.minecolonies.api.colony.jobs.IJob) job);
    CourierAssignmentModule couriers = mock(CourierAssignmentModule.class);
    when(couriers.getAssignedCitizen()).thenReturn(List.of(courier));
    IBuilding warehouse = mock(IBuilding.class);
    when(warehouse.getModule(
            any(com.minecolonies.api.colony.buildings.registry.BuildingEntry.ModuleProducer.class)))
        .thenReturn(couriers);
    when(manager.getColony().getServerBuildingManager().getBuildings())
        .thenReturn(Map.of(BlockPos.ZERO, warehouse));

    mutator.forgetVanishedDeliveryChild(resolver, manager, child, "test");

    verify(job, never()).onTaskDeletion(any());
    verify(job, never()).finishRequest(org.mockito.ArgumentMatchers.anyBoolean());
    verify(job, never()).getCurrentTask();
    verify(manager, never()).updateRequestState(any(), any());
    verify(manager.getRequestHandler(), never()).cleanRequestData(any());
  }
}
