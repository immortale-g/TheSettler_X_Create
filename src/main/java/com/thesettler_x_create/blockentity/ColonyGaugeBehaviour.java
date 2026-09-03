package com.thesettler_x_create.blockentity;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlock;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlock.PanelSlot;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelPosition;
import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBoard;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsFormatter;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import com.simibubi.create.foundation.utility.CreateLang;
import com.thesettler_x_create.TheSettlerXCreate;
import com.thesettler_x_create.client.gui.ColonyGaugeScreen;
import com.thesettler_x_create.menu.ColonyGaugeSetItemMenu;
import com.thesettler_x_create.minecolonies.building.BuildingCreateShop;
import java.util.List;
import net.createmod.catnip.animation.LerpedFloat;
import net.createmod.catnip.animation.LerpedFloat.Chaser;
import net.createmod.catnip.gui.ScreenOpener;
import net.createmod.catnip.platform.CatnipServices;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

public class ColonyGaugeBehaviour extends FilteringBehaviour implements MenuProvider {

  public static final BehaviourType<ColonyGaugeBehaviour> TOP_LEFT = new BehaviourType<>();
  public static final BehaviourType<ColonyGaugeBehaviour> TOP_RIGHT = new BehaviourType<>();
  public static final BehaviourType<ColonyGaugeBehaviour> BOTTOM_LEFT = new BehaviourType<>();
  public static final BehaviourType<ColonyGaugeBehaviour> BOTTOM_RIGHT = new BehaviourType<>();

  private static final int REQUEST_INTERVAL = 100;

  public final PanelSlot slot;
  public boolean active;
  public boolean satisfied;
  public boolean promisedSatisfied;
  public LerpedFloat bulb;

  private int colonyId = -1;
  @Nullable private BlockPos shopPos;
  @Nullable private String dimension;

  public @Nullable String cachedFrogportAddress;
  public @Nullable String manualAddress;
  /** -1 = never expire, 0 = 30s, N = N minutes. Same scale as FactoryPanelBehaviour, whose own
   * default is also -1 (never) — matched here. */
  public int promiseClearingInterval = -1;
  private int timer = REQUEST_INTERVAL;
  private long promisedUntil = 0L;
  /** Amount actually requested from the Colony Warehouse (may be < the target amount if the
   * warehouse only had partial stock) — used for "promised" UI display instead of the target. */
  private int promisedAmount = 0;

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

  /**
   * Cancels any colony request(s) still open under the currently-active address, so a request
   * left unresolved (e.g. no courier assigned to the warehouse) doesn't linger and cause a
   * duplicate to be created on the next {@link #tryRequest()}.
   */
  private void cancelActiveRequests() {
    String targetAddress = manualAddress != null ? manualAddress : cachedFrogportAddress;
    if (targetAddress == null || targetAddress.isBlank()) return;
    BuildingCreateShop building = findBuilding();
    if (building == null) return;
    int cancelled = building.cancelPendingGaugeRequests(getFilter(), targetAddress);
    if (cancelled > 0 && com.thesettler_x_create.Config.DEBUG_LOGGING.getAsBoolean()) {
      TheSettlerXCreate.LOGGER.info(
          "[ColonyGauge] cancelled {} stale colony request(s) slot={} address={}",
          cancelled, slot.getSerializedName(), targetAddress);
    }
  }

  public void disable() {
    cancelActiveRequests();
    active = false;
    colonyId = -1;
    shopPos = null;
    dimension = null;
    cachedFrogportAddress = null;
    manualAddress = null;
    satisfied = false;
    promisedSatisfied = false;
    timer = REQUEST_INTERVAL;
    promisedUntil = 0L;
    promisedAmount = 0;
    setFilter(ItemStack.EMPTY);
    blockEntity.notifyUpdate();
  }

  /**
   * Clears the requested item and pending promise, but keeps the panel linked to its shop
   * (unlike {@link #disable()}, which fully removes the panel).
   */
  public void resetFilter() {
    cancelActiveRequests();
    setFilter(ItemStack.EMPTY);
    satisfied = false;
    promisedSatisfied = false;
    promisedUntil = 0L;
    promisedAmount = 0;
    timer = REQUEST_INTERVAL;
    manualAddress = null;
    blockEntity.notifyUpdate();
    panelBE().updatePowered();
  }

  public void setManualAddress(@Nullable String address) {
    manualAddress = (address == null || address.isBlank()) ? null : address;
    blockEntity.notifyUpdate();
  }

  /** Placeholder shown in the address box when no manual override is set yet. */
  @Nullable
  public String getAddressHint() {
    return cachedFrogportAddress;
  }

  private int getPromiseExpiryTimeInTicks() {
    if (promiseClearingInterval == -1) return -1;
    if (promiseClearingInterval == 0) return 20 * 30;
    return promiseClearingInterval * 20 * 60;
  }

  public void setPromiseClearingInterval(int interval) {
    promiseClearingInterval = Math.max(-1, Math.min(31, interval));
    blockEntity.notifyUpdate();
  }

  /** Amount currently "promised" (requested but not yet delivered), for UI display. */
  public int getPromised() {
    return promisedSatisfied ? promisedAmount : 0;
  }

  public void forceClearPromises() {
    cancelActiveRequests();
    promisedSatisfied = false;
    promisedUntil = 0L;
    promisedAmount = 0;
    resetTimerSlightly();
    blockEntity.sendData();
    panelBE().updatePowered();
  }

  // --- Press-and-hold target amount (parity with FactoryPanelBehaviour) ---

  @Override
  public boolean isCountVisible() {
    return !getFilter().isEmpty();
  }

  @Override
  public void setValueSettings(Player player, ValueSettings settings, boolean ctrlDown) {
    if (getValueSettings().equals(settings)) return;
    count = Math.max(0, settings.value());
    upTo = settings.row() == 0;
    blockEntity.setChanged();
    blockEntity.sendData();
    playFeedbackSound(this);
    resetTimerSlightly();
  }

  @Override
  public ValueSettings getValueSettings() {
    return new ValueSettings(upTo ? 0 : 1, count);
  }

  @Override
  public ValueSettingsBoard createBoard(Player player, BlockHitResult hitResult) {
    int maxAmount = 100;
    return new ValueSettingsBoard(
        CreateLang.translate("factory_panel.target_amount").component(),
        maxAmount,
        10,
        List.of(
            CreateLang.translate("schedule.condition.threshold.items").component(),
            CreateLang.translate("schedule.condition.threshold.stacks").component()),
        new ValueSettingsFormatter(this::formatValue));
  }

  @Override
  public MutableComponent formatValue(ValueSettings value) {
    if (value.value() == 0) return CreateLang.translateDirect("gui.factory_panel.inactive");
    return Component.literal(Math.max(0, value.value()) + ((value.row() == 0) ? "" : "▤"));
  }

  private void resetTimerSlightly() {
    timer = REQUEST_INTERVAL / 2;
  }

  @Override
  public void tick() {
    super.tick();
    if (getWorld().isClientSide()) {
      bulb.updateChaseTarget(satisfied || promisedSatisfied ? 1 : 0);
      bulb.tickChaser();
      return;
    }
    if (active) {
      tickStorageMonitor();
      long now = getWorld().getGameTime();
      boolean newPromised = promisedUntil > now;
      if (newPromised != promisedSatisfied) {
        promisedSatisfied = newPromised;
        if (!promisedSatisfied && !satisfied) panelBE().updatePowered();
        blockEntity.sendData();
      }
    }

    if (timer > 0) {
      timer--;
      return;
    }
    timer = REQUEST_INTERVAL;
    tryRequest();
  }

  /**
   * Continuously compares the actual current stock in the connected Packager's target inventory
   * against the configured amount — mirroring Create's real {@code FactoryPanelBehaviour}
   * ({@code tickStorageMonitor}/{@code getLevelInStorage}), which reads
   * {@code packager.getAvailableItems()} every tick rather than tracking deliveries by count.
   * This is what lets the gauge keep asking (or stop asking) as stock actually changes, including
   * if items are later removed from the target inventory by other means.
   */
  private void tickStorageMonitor() {
    if (getFilter().isEmpty()) return;
    int amount = getAmount();
    boolean shouldSatisfy = amount <= 0 || getLevelInStorage() >= amount;
    if (shouldSatisfy == satisfied) return;
    satisfied = shouldSatisfy;
    if (shouldSatisfy) {
      promisedSatisfied = false;
      promisedAmount = 0;
      promisedUntil = 0L;
    }
    blockEntity.sendData();
    panelBE().updatePowered();
  }

  /** Current stock of the filtered item in the connected Packager's target inventory (0 if not
   * attached to a Packager, or if no inventory is connected to it yet). */
  private int getLevelInStorage() {
    var packager = panelBE().getConnectedPackager();
    if (packager == null) return 0;
    return packager.getAvailableItems().getCountOf(getFilter());
  }

  void tryRequest() {
    boolean debug = com.thesettler_x_create.Config.DEBUG_LOGGING.getAsBoolean();

    if (!isLinked()) {
      if (debug)
        TheSettlerXCreate.LOGGER.info(
            "[ColonyGauge] tryRequest skip slot={} reason=not-linked active={} colonyId={} shopPos={}",
            slot.getSerializedName(), active, colonyId, shopPos);
      return;
    }
    if (getFilter().isEmpty()) {
      if (debug)
        TheSettlerXCreate.LOGGER.info(
            "[ColonyGauge] tryRequest skip slot={} reason=empty-filter", slot.getSerializedName());
      return;
    }
    String targetAddress = manualAddress != null ? manualAddress : cachedFrogportAddress;
    if (targetAddress == null || targetAddress.isBlank()) {
      if (debug)
        TheSettlerXCreate.LOGGER.info(
            "[ColonyGauge] tryRequest skip slot={} reason=no-address manualAddress={} cachedFrogportAddress={}",
            slot.getSerializedName(), manualAddress, cachedFrogportAddress);
      return;
    }
    if (promisedSatisfied || satisfied) {
      if (debug)
        TheSettlerXCreate.LOGGER.info(
            "[ColonyGauge] tryRequest skip slot={} reason=already-satisfied satisfied={} promisedSatisfied={}",
            slot.getSerializedName(), satisfied, promisedSatisfied);
      return;
    }
    int amount = getAmount();
    int inStorage = getLevelInStorage();
    int remaining = amount - inStorage;
    if (remaining <= 0) {
      if (debug)
        TheSettlerXCreate.LOGGER.info(
            "[ColonyGauge] tryRequest skip slot={} reason=amount-zero target={} inStorage={} (paused via scroll or already covered by storage)",
            slot.getSerializedName(), amount, inStorage);
      return;
    }

    BuildingCreateShop building = findBuilding();
    if (building == null) {
      if (debug)
        TheSettlerXCreate.LOGGER.info(
            "[ColonyGauge] tryRequest skip slot={} reason=no-building colonyId={} shopPos={}",
            slot.getSerializedName(), colonyId, shopPos);
      return;
    }

    int requestedAmount = building.requestForGauge(getFilter().copy(), remaining, targetAddress);
    if (requestedAmount > 0) {
      int expiryTicks = getPromiseExpiryTimeInTicks();
      promisedUntil = expiryTicks < 0 ? Long.MAX_VALUE : getWorld().getGameTime() + expiryTicks;
      promisedSatisfied = true;
      promisedAmount = requestedAmount;
      blockEntity.sendData();
      panelBE().updatePowered();
      if (debug) {
        TheSettlerXCreate.LOGGER.info(
            "[ColonyGauge] behaviour requested slot={} item={} requestedAmount={} remaining={} target={} inStorage={} address={}",
            slot.getSerializedName(), getFilter().getItem(), requestedAmount, remaining, amount, inStorage, targetAddress);
      }
    } else if (debug) {
      TheSettlerXCreate.LOGGER.info(
          "[ColonyGauge] tryRequest skip slot={} reason=requestForGauge-returned-false",
          slot.getSerializedName());
    }
  }

  public void onDeliveryReceived() {
    promisedSatisfied = false;
    promisedAmount = 0;
    promisedUntil = 0L;
    resetTimerSlightly();
    blockEntity.sendData();
    // satisfied is recomputed from the connected Packager's actual current stock, not from this
    // event directly — tickStorageMonitor() already runs every tick, but call it here too so the
    // gauge reflects the delivery immediately instead of waiting up to a tick.
    tickStorageMonitor();
    if (com.thesettler_x_create.Config.DEBUG_LOGGING.getAsBoolean()) {
      TheSettlerXCreate.LOGGER.info(
          "[ColonyGauge] onDeliveryReceived slot={} inStorage={} target={} satisfied={}",
          slot.getSerializedName(), getLevelInStorage(), getAmount(), satisfied);
    }
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
  public void onShortInteract(Player player, InteractionHand hand, Direction side, BlockHitResult hitResult) {
    boolean isClientSide = player.level().isClientSide();
    ItemStack heldItem = player.getItemInHand(hand);

    if (getFilter().isEmpty()) {
      if (heldItem.isEmpty()) {
        if (!isClientSide && player instanceof ServerPlayer sp)
          sp.openMenu(
              this,
              buf -> FactoryPanelPosition.STREAM_CODEC.encode(buf, new FactoryPanelPosition(getPos(), slot)));
        return;
      }
      super.onShortInteract(player, hand, side, hitResult);
      return;
    }

    if (isClientSide)
      CatnipServices.PLATFORM.executeOnClientOnly(() -> () -> displayScreen(player));
  }

  @OnlyIn(Dist.CLIENT)
  public void displayScreen(Player player) {
    if (player instanceof LocalPlayer)
      ScreenOpener.open(new ColonyGaugeScreen(this));
  }

  public FactoryPanelPosition getPanelPosition() {
    return new FactoryPanelPosition(getPos(), slot);
  }

  @Override
  public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
    return ColonyGaugeSetItemMenu.create(containerId, playerInventory, this);
  }

  @Override
  public Component getDisplayName() {
    return blockEntity.getBlockState().getBlock().getName();
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
    tag.putInt("PromisedAmount", promisedAmount);
    if (cachedFrogportAddress != null) tag.putString("FrogportAddress", cachedFrogportAddress);
    if (manualAddress != null) tag.putString("ManualAddress", manualAddress);
    tag.putInt("PromiseClearingInterval", promiseClearingInterval);
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
    promisedAmount = tag.getInt("PromisedAmount");
    cachedFrogportAddress =
        tag.contains("FrogportAddress") ? tag.getString("FrogportAddress") : null;
    manualAddress = tag.contains("ManualAddress") ? tag.getString("ManualAddress") : null;
    promiseClearingInterval =
        tag.contains("PromiseClearingInterval") ? tag.getInt("PromiseClearingInterval") : 0;
  }
}
