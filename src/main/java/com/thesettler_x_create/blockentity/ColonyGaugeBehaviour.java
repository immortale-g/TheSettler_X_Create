package com.thesettler_x_create.blockentity;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlock;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlock.PanelSlot;
import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import com.thesettler_x_create.TheSettlerXCreate;
import com.thesettler_x_create.minecolonies.building.BuildingCreateShop;
import net.createmod.catnip.animation.LerpedFloat;
import net.createmod.catnip.animation.LerpedFloat.Chaser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public class ColonyGaugeBehaviour extends FilteringBehaviour {

  public static final BehaviourType<ColonyGaugeBehaviour> TOP_LEFT = new BehaviourType<>();
  public static final BehaviourType<ColonyGaugeBehaviour> TOP_RIGHT = new BehaviourType<>();
  public static final BehaviourType<ColonyGaugeBehaviour> BOTTOM_LEFT = new BehaviourType<>();
  public static final BehaviourType<ColonyGaugeBehaviour> BOTTOM_RIGHT = new BehaviourType<>();

  private static final int REQUEST_INTERVAL = 100;
  private static final int PROMISE_EXPIRY_TICKS = 600;

  public final PanelSlot slot;
  public boolean active;
  public boolean satisfied;
  public boolean promisedSatisfied;
  public LerpedFloat bulb;

  private int colonyId = -1;
  @Nullable private BlockPos shopPos;
  @Nullable private String dimension;

  public @Nullable String cachedFrogportAddress;
  private int timer = REQUEST_INTERVAL;
  private long promisedUntil = 0L;

  public ColonyGaugeBehaviour(ColonyGaugeBlockEntity be, PanelSlot slot) {
    super(be, new ColonyGaugeSlotPositioning(slot));
    this.slot = slot;
    this.active = false;
    this.satisfied = false;
    this.promisedSatisfied = false;
    this.bulb = LerpedFloat.linear().startWithValue(0).chase(0, 0.175, Chaser.EXP);
    setLazyTickRate(40);
  }

  @Override
  public boolean isActive() {
    return active;
  }

  public boolean isLinked() {
    return active && colonyId >= 0 && shopPos != null;
  }

  public void enable(int colonyId, BlockPos shopPos, String dimension) {
    this.active = true;
    this.colonyId = colonyId;
    this.shopPos = shopPos;
    this.dimension = dimension;
    blockEntity.notifyUpdate();
  }

  public void disable() {
    active = false;
    colonyId = -1;
    shopPos = null;
    dimension = null;
    cachedFrogportAddress = null;
    satisfied = false;
    promisedSatisfied = false;
    timer = REQUEST_INTERVAL;
    promisedUntil = 0L;
    setFilter(ItemStack.EMPTY);
    blockEntity.notifyUpdate();
  }

  @Override
  public void tick() {
    super.tick();
    if (getWorld().isClientSide()) {
      bulb.updateChaseTarget(satisfied || promisedSatisfied ? 1 : 0);
      bulb.tickChaser();
      return;
    }
    if (!active) return;

    long now = getWorld().getGameTime();
    boolean newPromised = promisedUntil > now;
    if (newPromised != promisedSatisfied) {
      promisedSatisfied = newPromised;
      if (!promisedSatisfied && !satisfied) panelBE().updatePowered();
      blockEntity.sendData();
    }

    if (timer > 0) {
      timer--;
      return;
    }
    timer = REQUEST_INTERVAL;
    tryRequest();
  }

  void tryRequest() {
    if (!isLinked()) return;
    if (getFilter().isEmpty()) return;
    if (cachedFrogportAddress == null || cachedFrogportAddress.isBlank()) return;
    if (promisedSatisfied || satisfied) return;

    BuildingCreateShop building = findBuilding();
    if (building == null) return;

    int amount = getAmount();
    if (amount <= 0) amount = getFilter().getMaxStackSize();

    boolean requested =
        building.requestForGauge(getFilter().copy(), amount, cachedFrogportAddress);
    if (requested) {
      promisedUntil = getWorld().getGameTime() + PROMISE_EXPIRY_TICKS;
      promisedSatisfied = true;
      blockEntity.sendData();
      panelBE().updatePowered();
      TheSettlerXCreate.LOGGER.debug(
          "[ColonyGauge] slot={} requested item={} amount={} address={}",
          slot.getSerializedName(), getFilter().getItem(), amount, cachedFrogportAddress);
    }
  }

  public void onDeliveryReceived() {
    satisfied = true;
    promisedSatisfied = false;
    promisedUntil = 0L;
    blockEntity.sendData();
    panelBE().updatePowered();
  }

  @Nullable
  private BuildingCreateShop findBuilding() {
    if (getWorld() == null || shopPos == null || colonyId < 0) return null;
    IColony colony =
        IColonyManager.getInstance().getColonyByDimension(colonyId, getWorld().dimension());
    if (colony == null) return null;
    var building = colony.getServerBuildingManager().getBuilding(shopPos);
    if (building instanceof BuildingCreateShop shop) return shop;
    return null;
  }

  private ColonyGaugeBlockEntity panelBE() {
    return (ColonyGaugeBlockEntity) blockEntity;
  }

  @Override
  public BehaviourType<?> getType() {
    return getTypeForSlot(slot);
  }

  public static BehaviourType<?> getTypeForSlot(PanelSlot slot) {
    return switch (slot) {
      case TOP_LEFT -> TOP_LEFT;
      case TOP_RIGHT -> TOP_RIGHT;
      case BOTTOM_LEFT -> BOTTOM_LEFT;
      case BOTTOM_RIGHT -> BOTTOM_RIGHT;
    };
  }

  @Override
  public int netId() {
    return 10 + slot.ordinal();
  }

  @Override
  public void write(CompoundTag nbt, HolderLookup.Provider registries, boolean clientPacket) {
    if (!active) return;
    CompoundTag tag = new CompoundTag();
    super.write(tag, registries, clientPacket);
    tag.putInt("ColonyId", colonyId);
    if (shopPos != null) tag.putLong("ShopPos", shopPos.asLong());
    if (dimension != null) tag.putString("Dimension", dimension);
    tag.putInt("Timer", timer);
    tag.putLong("PromisedUntil", promisedUntil);
    tag.putBoolean("Satisfied", satisfied);
    tag.putBoolean("PromisedSatisfied", promisedSatisfied);
    if (cachedFrogportAddress != null) tag.putString("FrogportAddress", cachedFrogportAddress);
    nbt.put(slot.getSerializedName(), tag);
  }

  @Override
  public void read(CompoundTag nbt, HolderLookup.Provider registries, boolean clientPacket) {
    CompoundTag tag = nbt.getCompound(slot.getSerializedName());
    if (tag.isEmpty()) {
      active = false;
      return;
    }
    active = true;
    super.read(tag, registries, clientPacket);
    colonyId = tag.getInt("ColonyId");
    shopPos = tag.contains("ShopPos") ? BlockPos.of(tag.getLong("ShopPos")) : null;
    dimension = tag.contains("Dimension") ? tag.getString("Dimension") : null;
    timer = tag.getInt("Timer");
    promisedUntil = tag.getLong("PromisedUntil");
    satisfied = tag.getBoolean("Satisfied");
    promisedSatisfied = tag.getBoolean("PromisedSatisfied");
    cachedFrogportAddress =
        tag.contains("FrogportAddress") ? tag.getString("FrogportAddress") : null;
  }
}
