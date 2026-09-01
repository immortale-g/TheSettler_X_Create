package com.thesettler_x_create.blockentity;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.simibubi.create.content.logistics.packagePort.PackagePortBlockEntity;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import com.thesettler_x_create.TheSettlerXCreate;
import com.thesettler_x_create.init.ModBlockEntities;
import com.thesettler_x_create.minecolonies.building.BuildingCreateShop;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
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

  /** 30 seconds in ticks — how long a sent request counts as "promised". */
  private static final int PROMISE_EXPIRY_TICKS = 600;

  public FilteringBehaviour filter;

  private int colonyId = -1;
  @Nullable private BlockPos shopPos;
  @Nullable private String dimension;

  private int timer = REQUEST_INTERVAL;
  private long promisedUntil = 0L;

  /** Cached address from adjacent Frogport — refreshed each tick. */
  @Nullable private String cachedFrogportAddress;

  // Client-side state flags (synced via write/read)
  public boolean satisfied = false;
  public boolean promisedSatisfied = false;

  public ColonyGaugeBlockEntity(BlockPos pos, BlockState state) {
    this(ModBlockEntities.COLONY_GAUGE.get(), pos, state);
  }

  public ColonyGaugeBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
    super(type, pos, state);
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
  public void tick() {
    super.tick();
    if (level == null || level.isClientSide()) return;

    scanAdjacentFrogport();
    updateSatisfiedState();

    if (timer > 0) {
      timer--;
      return;
    }
    timer = REQUEST_INTERVAL;
    tryRequest();
  }

  private void scanAdjacentFrogport() {
    String found = null;
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
    if (!java.util.Objects.equals(found, cachedFrogportAddress)) {
      cachedFrogportAddress = found;
      sendData();
    }
  }

  private void updateSatisfiedState() {
    long now = level.getGameTime();
    boolean newPromised = promisedUntil > now;
    // "satisfied" is always false for now — we rely on promisedSatisfied to gate requests
    if (newPromised != promisedSatisfied) {
      promisedSatisfied = newPromised;
      satisfied = false;
      sendData();
    }
  }

  private void tryRequest() {
    if (!isLinked()) return;
    if (filter.getFilter().isEmpty()) return;
    if (cachedFrogportAddress == null || cachedFrogportAddress.isBlank()) return;
    if (promisedSatisfied) return;

    BuildingCreateShop building = findBuilding();
    if (building == null) return;

    int amount = filter.getAmount();
    if (amount <= 0) amount = filter.getFilter().getMaxStackSize();

    boolean requested =
        building.requestForGauge(filter.getFilter().copy(), amount, cachedFrogportAddress);
    if (requested) {
      promisedUntil = level.getGameTime() + PROMISE_EXPIRY_TICKS;
      promisedSatisfied = true;
      sendData();
      TheSettlerXCreate.LOGGER.debug(
          "[ColonyGauge] request sent item={} amount={} address={}",
          filter.getFilter().getItem(),
          amount,
          cachedFrogportAddress);
    }
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
    cachedFrogportAddress =
        tag.contains("FrogportAddress") ? tag.getString("FrogportAddress") : null;
  }

  @Nullable
  public String getCachedFrogportAddress() {
    return cachedFrogportAddress;
  }

  public int getColonyId() {
    return colonyId;
  }

  @Nullable
  public BlockPos getShopPos() {
    return shopPos;
  }
}
