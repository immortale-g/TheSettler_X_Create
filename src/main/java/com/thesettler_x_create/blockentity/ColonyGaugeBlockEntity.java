package com.thesettler_x_create.blockentity;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.simibubi.create.content.logistics.packagePort.PackagePortBlockEntity;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import com.thesettler_x_create.TheSettlerXCreate;
import com.thesettler_x_create.block.ColonyGaugeBlock;
import com.thesettler_x_create.init.ModBlockEntities;
import com.thesettler_x_create.minecolonies.building.BuildingCreateShop;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class ColonyGaugeBlockEntity extends SmartBlockEntity {

  private static final String TAG_COLONY_ID = "GaugeColonyId";
  private static final String TAG_SHOP_POS = "GaugeShopPos";
  private static final String TAG_DIMENSION = "GaugeDimension";
  private static final String TAG_TIMER = "GaugeTimer";
  private static final String TAG_PROMISED_UNTIL = "GaugePromisedUntil";

  /** Ticks between request attempts. */
  private static final int REQUEST_INTERVAL = 100;

  /** 30 seconds — how long a sent request counts as promised. */
  private static final int PROMISE_EXPIRY_TICKS = 600;

  public FilteringBehaviour filter;

  private int colonyId = -1;
  @Nullable private BlockPos shopPos;
  @Nullable private String dimension;

  private int timer = REQUEST_INTERVAL;
  private long promisedUntil = 0L;

  /**
   * True when an adjacent block (in connectedDirection) is a Packager. In restocker mode the
   * Frogport address is read from above the Packager instead of adjacent to the gauge.
   */
  private boolean restocker = false;

  /** Cached address from adjacent/restocker Frogport — refreshed each lazy tick. */
  @Nullable private String cachedFrogportAddress;

  // Synced client-side state
  public boolean satisfied = false;
  public boolean promisedSatisfied = false;

  public ColonyGaugeBlockEntity(BlockPos pos, BlockState state) {
    this(ModBlockEntities.COLONY_GAUGE.get(), pos, state);
  }

  public ColonyGaugeBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
    super(type, pos, state);
    setLazyTickRate(20);
  }

  @Override
  public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    behaviours.add(filter = new FilteringBehaviour(this, new ColonyGaugeFilterSlot()));
  }

  public void setShopLink(int colonyId, BlockPos shopPos, String dimension) {
    this.colonyId = colonyId;
    this.shopPos = shopPos;
    this.dimension = dimension;
    setChanged();
    sendData();
  }

  public boolean isLinked() {
    return colonyId >= 0 && shopPos != null;
  }

  @Override
  public void lazyTick() {
    super.lazyTick();
    if (level == null || level.isClientSide()) return;
    detectPackager();
    scanFrogport();
  }

  @Override
  public void tick() {
    super.tick();
    if (level == null || level.isClientSide()) return;

    updateSatisfiedState();

    if (timer > 0) {
      timer--;
      return;
    }
    timer = REQUEST_INTERVAL;
    tryRequest();
  }

  /** Checks if the block this gauge is attached to is a Packager. */
  private void detectPackager() {
    BlockState state = getBlockState();
    Direction connectedDir = ColonyGaugeBlock.connectedDirection(state);
    BlockPos attachedPos = getBlockPos().relative(connectedDir);
    boolean isRestocker = level.getBlockEntity(attachedPos) instanceof PackagerBlockEntity;
    if (isRestocker != restocker) {
      restocker = isRestocker;
      sendData();
    }
  }

  /**
   * Scans for a Frogport. In restocker mode reads from above the attached Packager (matching
   * Create's FactoryPanel.getFrogAddress()). Otherwise scans all adjacent blocks.
   */
  private void scanFrogport() {
    String found = null;
    if (restocker) {
      BlockState state = getBlockState();
      Direction connectedDir = ColonyGaugeBlock.connectedDirection(state);
      BlockPos packagerPos = getBlockPos().relative(connectedDir);
      BlockPos abovePackager = packagerPos.above();
      if (level.getBlockEntity(abovePackager) instanceof PackagePortBlockEntity port) {
        String addr = port.addressFilter;
        if (addr != null && !addr.isBlank()) found = addr;
      }
    } else {
      for (Direction dir : Direction.values()) {
        BlockPos neighbor = getBlockPos().relative(dir);
        if (level.getBlockEntity(neighbor) instanceof PackagePortBlockEntity port) {
          String addr = port.addressFilter;
          if (addr != null && !addr.isBlank()) {
            found = addr;
            break;
          }
        }
      }
    }
    if (!Objects.equals(found, cachedFrogportAddress)) {
      cachedFrogportAddress = found;
      sendData();
    }
  }

  private void updateSatisfiedState() {
    long now = level.getGameTime();
    boolean newPromised = promisedUntil > now;
    if (newPromised != promisedSatisfied) {
      promisedSatisfied = newPromised;
      if (!promisedSatisfied) satisfied = false;
      sendData();
      updatePowered(promisedSatisfied || satisfied);
    }
  }

  private void updatePowered(boolean powered) {
    BlockState state = getBlockState();
    if (!state.hasProperty(ColonyGaugeBlock.POWERED)) return;
    if (state.getValue(ColonyGaugeBlock.POWERED) == powered) return;
    level.setBlock(
        getBlockPos(), state.setValue(ColonyGaugeBlock.POWERED, powered), Block.UPDATE_ALL);
  }

  private void tryRequest() {
    if (!isLinked()) return;
    if (filter.getFilter().isEmpty()) return;
    if (cachedFrogportAddress == null || cachedFrogportAddress.isBlank()) return;
    if (promisedSatisfied || satisfied) return;

    BuildingCreateShop building = findBuilding();
    if (building == null) return;

    int amount = filter.getAmount();
    if (amount <= 0) amount = filter.getFilter().getMaxStackSize();

    boolean requested =
        building.requestForGauge(
            filter.getFilter().copy(), amount, cachedFrogportAddress, getBlockPos());
    if (requested) {
      promisedUntil = level.getGameTime() + PROMISE_EXPIRY_TICKS;
      promisedSatisfied = true;
      sendData();
      updatePowered(true);
      TheSettlerXCreate.LOGGER.debug(
          "[ColonyGauge] request sent item={} amount={} address={} restocker={}",
          filter.getFilter().getItem(),
          amount,
          cachedFrogportAddress,
          restocker);
    }
  }

  /**
   * Called by BuildingCreateShop when the colony courier delivers items to the shop for this gauge.
   * Items are now in shop racks and packaging is queued.
   */
  public void onDeliveryReceived() {
    satisfied = true;
    sendData();
    updatePowered(true);
  }

  @Nullable
  private BuildingCreateShop findBuilding() {
    if (level == null || shopPos == null || colonyId < 0) return null;
    IColony colony = IColonyManager.getInstance().getColonyByDimension(colonyId, level.dimension());
    if (colony == null) return null;
    var building = colony.getServerBuildingManager().getBuilding(shopPos);
    if (building instanceof BuildingCreateShop shop) return shop;
    return null;
  }

  @Override
  protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
    super.write(tag, registries, clientPacket);
    tag.putInt(TAG_COLONY_ID, colonyId);
    if (shopPos != null) tag.putLong(TAG_SHOP_POS, shopPos.asLong());
    if (dimension != null) tag.putString(TAG_DIMENSION, dimension);
    tag.putInt(TAG_TIMER, timer);
    tag.putLong(TAG_PROMISED_UNTIL, promisedUntil);
    tag.putBoolean("Satisfied", satisfied);
    tag.putBoolean("PromisedSatisfied", promisedSatisfied);
    tag.putBoolean("Restocker", restocker);
    if (cachedFrogportAddress != null) tag.putString("FrogportAddress", cachedFrogportAddress);
  }

  @Override
  protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
    super.read(tag, registries, clientPacket);
    colonyId = tag.getInt(TAG_COLONY_ID);
    if (tag.contains(TAG_SHOP_POS)) shopPos = BlockPos.of(tag.getLong(TAG_SHOP_POS));
    if (tag.contains(TAG_DIMENSION)) dimension = tag.getString(TAG_DIMENSION);
    timer = tag.getInt(TAG_TIMER);
    promisedUntil = tag.getLong(TAG_PROMISED_UNTIL);
    satisfied = tag.getBoolean("Satisfied");
    promisedSatisfied = tag.getBoolean("PromisedSatisfied");
    restocker = tag.getBoolean("Restocker");
    cachedFrogportAddress =
        tag.contains("FrogportAddress") ? tag.getString("FrogportAddress") : null;
  }

  @Nullable
  public String getCachedFrogportAddress() {
    return cachedFrogportAddress;
  }

  public boolean isRestocker() {
    return restocker;
  }

  public int getColonyId() {
    return colonyId;
  }

  @Nullable
  public BlockPos getShopPos() {
    return shopPos;
  }
}
