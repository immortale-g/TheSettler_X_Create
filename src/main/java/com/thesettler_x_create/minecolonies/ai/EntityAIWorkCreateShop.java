package com.thesettler_x_create.minecolonies.ai;

import com.minecolonies.api.entity.ai.JobStatus;
import com.minecolonies.api.entity.ai.statemachine.AITarget;
import com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState;
import com.minecolonies.api.entity.ai.statemachine.states.IAIState;
import com.minecolonies.api.entity.ai.statemachine.tickratestatemachine.IStateSupplier;
import com.minecolonies.api.entity.citizen.VisibleCitizenStatus;
import com.minecolonies.core.entity.ai.workers.AbstractEntityAIInteract;
import com.thesettler_x_create.minecolonies.building.BuildingCreateShop;
import com.thesettler_x_create.minecolonies.job.JobCreateShop;

public class EntityAIWorkCreateShop
    extends AbstractEntityAIInteract<JobCreateShop, BuildingCreateShop> {
  public EntityAIWorkCreateShop(JobCreateShop job) {
    super(job);
    registerTargets(
        new AITarget<>(AIWorkerState.PREPARING, (IStateSupplier<IAIState>) this::prepare, 20),
        new AITarget<>(AIWorkerState.START_WORKING, (IStateSupplier<IAIState>) this::work, 20),
        new AITarget<>(AIWorkerState.IDLE, (IStateSupplier<IAIState>) this::idleState, 20));
  }

  @Override
  public Class<BuildingCreateShop> getExpectedBuildingClass() {
    return BuildingCreateShop.class;
  }

  @Override
  protected void updateRenderMetaData() {
    if (worker == null) {
      return;
    }
    IAIState state = getState();
    if (state == AIWorkerState.PREPARING || state == AIWorkerState.START_WORKING) {
      worker.setRenderMetadata("working");
    } else {
      worker.setRenderMetadata("");
    }
  }

  public boolean hasWorkToDo() {
    return isWorkingTime();
  }

  @Override
  public boolean canGoIdle() {
    BuildingCreateShop building = this.building;
    if (building != null && building.hasResolverWork()) {
      return false;
    }
    return !isWorkingTime();
  }

  protected IAIState decide() {
    return isWorkingTime() ? AIWorkerState.PREPARING : AIWorkerState.IDLE;
  }

  private IAIState prepare() {
    if (!isWorkingTime()) {
      markIdle();
      return AIWorkerState.IDLE;
    }
    markWorking();
    if (walkToBuilding()) {
      return AIWorkerState.START_WORKING;
    }
    return AIWorkerState.PREPARING;
  }

  private IAIState work() {
    if (!isWorkingTime()) {
      markIdle();
      return AIWorkerState.IDLE;
    }
    markWorking();
    walkToBuilding();
    return AIWorkerState.START_WORKING;
  }

  private IAIState idleState() {
    if (isWorkingTime()) {
      markWorking();
      return AIWorkerState.PREPARING;
    }
    markIdle();
    return AIWorkerState.IDLE;
  }

  private boolean isWorkingTime() {
    if (world == null) {
      return true;
    }
    return world.isDay();
  }

  private void markWorking() {
    if (worker == null || worker.getCitizenData() == null) {
      return;
    }
    if (building != null && building.hasCapacityStall()) {
      worker.getCitizenData().setJobStatus(JobStatus.STUCK);
      worker.getCitizenData().setVisibleStatus(VisibleCitizenStatus.WORKING);
      return;
    }
    worker.getCitizenData().setJobStatus(JobStatus.WORKING);
    worker.getCitizenData().setVisibleStatus(VisibleCitizenStatus.WORKING);
  }

  private void markIdle() {
    if (worker == null || worker.getCitizenData() == null) {
      return;
    }
    worker.getCitizenData().setJobStatus(JobStatus.IDLE);
  }
}
