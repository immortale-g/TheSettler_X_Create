package com.thesettler_x_create.minecolonies.ai;

import com.minecolonies.api.entity.ai.JobStatus;
import com.minecolonies.api.entity.ai.statemachine.AITarget;
import com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState;
import com.minecolonies.api.entity.ai.statemachine.states.IAIState;
import com.minecolonies.api.entity.ai.statemachine.tickratestatemachine.IStateSupplier;
import com.minecolonies.api.entity.citizen.VisibleCitizenStatus;
import com.minecolonies.api.util.InventoryUtils;
import com.minecolonies.api.util.Tuple;
import com.minecolonies.core.entity.ai.workers.AbstractEntityAIInteract;
import com.thesettler_x_create.blockentity.CreateShopBlockEntity;
import com.thesettler_x_create.blockentity.CreateShopOutputBlockEntity;
import com.thesettler_x_create.minecolonies.building.BuildingCreateShop;
import com.thesettler_x_create.minecolonies.building.ShopMissingOutputAddressInteraction;
import com.thesettler_x_create.minecolonies.job.JobCreateShop;
import com.thesettler_x_create.minecolonies.tileentity.TileEntityCreateShop;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

public class EntityAIWorkCreateShop
    extends AbstractEntityAIInteract<JobCreateShop, BuildingCreateShop> {

  @Nullable private BlockPos pendingRackPos;
  @Nullable private ItemStack pendingTargetItem;
  @Nullable private ItemStack pendingCarriedItem;

  /**
   * The rack the carried stack came out of, set only while clearing an arrival rack. Non-null means
   * the stack goes into another rack rather than into the hut buffer, and it names the one rack
   * that must not take it back.
   */
  @Nullable private BlockPos pendingArrivalRackPos;

  @SuppressWarnings("unchecked")
  public EntityAIWorkCreateShop(JobCreateShop job) {
    super(job);
    registerTargets(
        new AITarget<>(AIWorkerState.PREPARING, (IStateSupplier<IAIState>) this::prepare, 20),
        new AITarget<>(AIWorkerState.START_WORKING, (IStateSupplier<IAIState>) this::work, 20),
        new AITarget<>(AIWorkerState.IDLE, (IStateSupplier<IAIState>) this::idleState, 20),
        new AITarget<>(
            CreateShopAIState.HOUSEKEEPING_FETCH,
            (IStateSupplier<IAIState>) this::housekeepingFetch,
            10),
        new AITarget<>(
            CreateShopAIState.HOUSEKEEPING_DEPOSIT,
            (IStateSupplier<IAIState>) this::housekeepingDeposit,
            10));
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
    if (state == AIWorkerState.PREPARING
        || state == AIWorkerState.START_WORKING
        || state == CreateShopAIState.HOUSEKEEPING_FETCH
        || state == CreateShopAIState.HOUSEKEEPING_DEPOSIT) {
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
    if (hasUrgentWork()) {
      return false;
    }
    return !isWorkingTime();
  }

  protected IAIState decide() {
    return shouldWorkNow() ? AIWorkerState.PREPARING : AIWorkerState.IDLE;
  }

  private IAIState prepare() {
    if (!shouldWorkNow()) {
      markIdle();
      return AIWorkerState.IDLE;
    }
    markWorking();
    if (building != null && building.hasOutputBlock()) {
      CreateShopOutputBlockEntity obe = building.getOutputBlockEntity();
      if (obe != null && obe.getPackageAddress().isEmpty() && worker.getCitizenData() != null) {
        worker.getCitizenData().triggerInteraction(new ShopMissingOutputAddressInteraction());
      }
    }
    if (walkToBuilding()) {
      return AIWorkerState.START_WORKING;
    }
    return AIWorkerState.PREPARING;
  }

  private IAIState work() {
    if (!shouldWorkNow()) {
      markIdle();
      return AIWorkerState.IDLE;
    }
    markWorking();
    if (building != null && building.hasRackWorkToDo()) {
      return CreateShopAIState.HOUSEKEEPING_FETCH;
    }
    walkToBuilding();
    return AIWorkerState.START_WORKING;
  }

  private IAIState housekeepingFetch() {
    if (!shouldWorkNow()) {
      clearHousekeepingState();
      markIdle();
      return AIWorkerState.IDLE;
    }
    if (building == null || !building.hasRackWorkToDo()) {
      clearHousekeepingState();
      return AIWorkerState.START_WORKING;
    }
    TileEntityCreateShop tile = building.getCreateShopTileEntity();
    CreateShopBlockEntity pickup = building.getPickupBlockEntity();
    if (tile == null || pickup == null) {
      clearHousekeepingState();
      return AIWorkerState.START_WORKING;
    }
    if (pendingRackPos == null && !pickNextHousekeepingJob(tile, pickup)) {
      clearHousekeepingState();
      return AIWorkerState.START_WORKING;
    }
    if (!walkToWorkPos(pendingRackPos)) {
      return CreateShopAIState.HOUSEKEEPING_FETCH;
    }
    ItemStack target = pendingTargetItem != null ? pendingTargetItem : ItemStack.EMPTY;
    // Clearing an arrival rack only moves goods between racks, and every count the shop keeps
    // is over all racks together, so reserved stock may travel too: no budget is passed.
    ItemStack extracted =
        tile.extractFromRack(pendingRackPos, target, pendingArrivalRackPos == null ? pickup : null);
    pendingRackPos = null;
    pendingTargetItem = null;
    if (extracted.isEmpty()) {
      return CreateShopAIState.HOUSEKEEPING_FETCH;
    }
    pendingCarriedItem = extracted;
    worker.setItemInHand(InteractionHand.MAIN_HAND, extracted);
    return CreateShopAIState.HOUSEKEEPING_DEPOSIT;
  }

  private IAIState housekeepingDeposit() {
    if (!shouldWorkNow()) {
      returnCarriedItemToRack();
      markIdle();
      return AIWorkerState.IDLE;
    }
    if (building == null) {
      returnCarriedItemToRack();
      return AIWorkerState.START_WORKING;
    }
    if (!walkToBuilding()) {
      return CreateShopAIState.HOUSEKEEPING_DEPOSIT;
    }
    ItemStack carried = pendingCarriedItem;
    if (carried == null || carried.isEmpty()) {
      clearHousekeepingState();
      return CreateShopAIState.HOUSEKEEPING_FETCH;
    }
    TileEntityCreateShop tile = building.getCreateShopTileEntity();
    if (tile == null) {
      returnCarriedItemToRack();
      return AIWorkerState.START_WORKING;
    }
    if (pendingArrivalRackPos != null) {
      depositIntoAnotherRack(tile, carried);
      return CreateShopAIState.HOUSEKEEPING_FETCH;
    }
    IItemHandler hut = tile.getInventory();
    if (hut == null) {
      returnCarriedItemToRack();
      return AIWorkerState.START_WORKING;
    }
    ItemStack leftover =
        InventoryUtils.transferItemStackIntoNextBestSlotInItemHandlerWithResult(carried, hut);
    worker.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
    pendingCarriedItem = null;
    if (!leftover.isEmpty()) {
      tile.insertIntoRacks(java.util.List.of(leftover));
    }
    return CreateShopAIState.HOUSEKEEPING_FETCH;
  }

  /**
   * Chooses what the shopkeeper carries next, and remembers where it goes.
   *
   * <p>Clearing an arrival rack comes first: that rack is the only place Create can unpack into, so
   * while it is tight nothing new arrives at all. It also runs while a delivery is being gathered,
   * unlike the move into the hut buffer, because it does not take anything out of the rack pool the
   * courier reads -- it only spreads it.
   *
   * @return false when there is nothing to carry
   */
  private boolean pickNextHousekeepingJob(TileEntityCreateShop tile, CreateShopBlockEntity pickup) {
    Tuple<BlockPos, ItemStack> arrival = tile.findNextArrivalRackItem();
    if (arrival != null) {
      pendingArrivalRackPos = arrival.getA();
      pendingRackPos = arrival.getA();
      pendingTargetItem = arrival.getB();
      return true;
    }
    pendingArrivalRackPos = null;
    if (building == null || !building.isHousekeepingAllowed()) {
      return false;
    }
    Tuple<BlockPos, ItemStack> next = tile.findNextUnreservedRackItem(pickup);
    if (next == null) {
      return false;
    }
    pendingRackPos = next.getA();
    pendingTargetItem = next.getB();
    return true;
  }

  /** Puts a stack carried out of an arrival rack into any other rack, or back if none takes it. */
  private void depositIntoAnotherRack(TileEntityCreateShop tile, ItemStack carried) {
    java.util.List<ItemStack> leftovers =
        tile.insertIntoOtherRacks(pendingArrivalRackPos, java.util.List.of(carried));
    worker.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
    pendingCarriedItem = null;
    if (!leftovers.isEmpty()) {
      // Every other rack filled up while the shopkeeper walked. Back where it came from, so the
      // goods are never held in a hand across a save.
      tile.insertIntoRacks(leftovers);
    }
    pendingArrivalRackPos = null;
  }

  private void clearHousekeepingState() {
    pendingRackPos = null;
    pendingTargetItem = null;
    pendingCarriedItem = null;
    pendingArrivalRackPos = null;
    if (worker != null) {
      worker.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
    }
  }

  private void returnCarriedItemToRack() {
    if (pendingCarriedItem != null && !pendingCarriedItem.isEmpty() && building != null) {
      TileEntityCreateShop tile = building.getCreateShopTileEntity();
      if (tile != null) {
        tile.insertIntoRacks(java.util.List.of(pendingCarriedItem));
      }
    }
    clearHousekeepingState();
  }

  private IAIState idleState() {
    if (shouldWorkNow()) {
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

  private boolean hasUrgentWork() {
    BuildingCreateShop currentBuilding = this.building;
    return currentBuilding != null && currentBuilding.hasUrgentWork();
  }

  private boolean shouldWorkNow() {
    return isWorkingTime() || hasUrgentWork();
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
