package com.thesettler_x_create.minecolonies.building;

import com.google.common.collect.ImmutableCollection;
import com.minecolonies.api.blocks.AbstractBlockMinecoloniesRack;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.request.RequestState;
import com.minecolonies.api.colony.requestsystem.requestable.Stack;
import com.minecolonies.api.colony.requestsystem.requestable.deliveryman.AbstractDeliverymanRequestable;
import com.minecolonies.api.colony.requestsystem.requestable.deliveryman.Pickup;
import com.minecolonies.api.colony.requestsystem.requester.IRequester;
import com.minecolonies.api.colony.requestsystem.resolver.IRequestResolver;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.api.tileentities.AbstractTileEntityWareHouse;
import com.minecolonies.api.util.InventoryUtils;
import com.minecolonies.api.util.constant.TypeConstants;
import com.minecolonies.core.colony.buildings.AbstractBuilding;
import com.minecolonies.core.colony.buildings.modules.BuildingModules;
import com.minecolonies.core.colony.buildings.modules.CourierAssignmentModule;
import com.minecolonies.core.colony.requestsystem.management.IStandardRequestManager;
import com.minecolonies.core.colony.requestsystem.resolvers.PickupRequestResolver;
import com.minecolonies.core.tileentities.TileEntityRack;
import com.simibubi.create.content.logistics.BigItemStack;
import com.thesettler_x_create.Config;
import com.thesettler_x_create.TheSettlerXCreate;
import com.thesettler_x_create.block.CreateShopBlock;
import com.thesettler_x_create.block.CreateShopOutputBlock;
import com.thesettler_x_create.blockentity.CreateShopBlockEntity;
import com.thesettler_x_create.blockentity.CreateShopOutputBlockEntity;
import com.thesettler_x_create.create.CreateNetworkFacade;
import com.thesettler_x_create.minecolonies.requestsystem.resolver.CreateShopRequestResolver;
import com.thesettler_x_create.minecolonies.tileentity.TileEntityCreateShop;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Tuple;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

/** Create Shop building integration with MineColonies request system and Create network. */
public class BuildingCreateShop extends AbstractBuilding {
  public static final String SCHEMATIC_NAME = "createshop";

  static boolean isDebugRequests() {
    return Config.DEBUG_LOGGING.getAsBoolean();
  }

  private static final String TAG_PICKUP_POS = "PickupPos";
  private static final String TAG_OUTPUT_POS = "OutputPos";
  static final String TAG_PERMA_ORES = "PermaOres";
  static final String TAG_PERMA_WAIT_FULL = "PermaWaitFullStack";
  private static final String TAG_BUILDER_HUT_POS = "BuilderHutPos";
  private static final String TAG_FLOW_STATES = "FlowStates";

  /**
   * Gauge packaging task: items to extract from racks and send to the gauge address. {@code
   * requestId} keys the {@link CreateShopBlockEntity} reservation that protects the requested
   * amount from being swept away by rack housekeeping before it's packaged.
   */
  public record GaugePackagingTask(
      net.minecraft.world.item.ItemStack item,
      int amount,
      String gaugeAddress,
      java.util.UUID requestId) {}

  private final java.util.Map<String, String> lastRequesterError = new java.util.HashMap<>();

  /** Transient: maps pending colony-request token → gauge task (for cancellation cleanup). */
  private final java.util.Map<IToken<?>, GaugePackagingTask> pendingGaugeRequests =
      new java.util.LinkedHashMap<>();

  /** Persisted: gauge packaging tasks waiting for items to arrive in racks. */
  private final java.util.List<GaugePackagingTask> gaugePackagingQueue =
      new java.util.ArrayList<>();

  boolean warehouseRegistered;
  private CreateShopRequestResolver shopResolver;

  /** FlowStates loaded from NBT; applied to the StateMachine when the resolver first connects. */
  @Nullable private net.minecraft.nbt.CompoundTag pendingFlowStatesTag;

  private IToken<?> deliveryResolverToken;
  private IToken<?> pickupResolverToken;
  private BlockPos pickupPos;
  private BlockPos outputPos;
  private BlockPos builderHutPos;
  private final ShopInflightTracker inflightTracker;
  private final ShopRackIndex rackIndex;
  private final ShopBeltManager beltManager;
  private final ShopBeltBlueprints beltBlueprints;
  private final ShopWarehouseRegistrar warehouseRegistrar;
  private final ShopResolverAssignments resolverAssignments;
  private final ShopCourierDiagnostics courierDiagnostics;
  private final ShopPermaRequestManager permaManager;
  private final ShopWorkerStatus workerStatus;
  private final ShopNetworkNotifier networkNotifier;
  private final ShopResolverFactory resolverFactory;
  private final ShopResolverHealthCheck resolverHealthCheck;
  private final ShopHousekeepingOrchestrator housekeepingOrchestrator;
  private long lostPackageInteractionEpoch;
  private boolean legacyCourierMigrationAttempted;

  public BuildingCreateShop(IColony colony, BlockPos location) {
    super(colony, location);
    this.warehouseRegistered = false;
    this.shopResolver = null;
    this.builderHutPos = null;
    this.inflightTracker = new ShopInflightTracker(this);
    this.rackIndex = new ShopRackIndex(this);
    this.beltManager = new ShopBeltManager(this);
    this.beltBlueprints = new ShopBeltBlueprints(this);
    this.warehouseRegistrar = new ShopWarehouseRegistrar(this);
    this.resolverAssignments = new ShopResolverAssignments(this);
    this.courierDiagnostics = new ShopCourierDiagnostics(this);
    this.permaManager = new ShopPermaRequestManager(this);
    this.workerStatus = new ShopWorkerStatus(this);
    this.networkNotifier = new ShopNetworkNotifier(this);
    this.resolverFactory = new ShopResolverFactory(this);
    this.resolverHealthCheck = new ShopResolverHealthCheck(this);
    this.housekeepingOrchestrator = new ShopHousekeepingOrchestrator(this);
    this.lostPackageInteractionEpoch = 0L;
    this.legacyCourierMigrationAttempted = false;
  }

  @Override
  public String getSchematicName() {
    return SCHEMATIC_NAME;
  }

  @Override
  public int getMaxBuildingLevel() {
    return 2;
  }

  public boolean canAccessWareHouse(ICitizenData citizen) {
    boolean result =
        citizen != null
            && citizen.getJob() instanceof com.minecolonies.core.colony.jobs.JobDeliveryman;
    if (isDebugRequests()) {
      courierDiagnostics.logAccessCheck(citizen, result);
    }
    return result;
  }

  @Override
  public AbstractTileEntityWareHouse getTileEntity() {
    if (super.getTileEntity() instanceof AbstractTileEntityWareHouse wareHouse) {
      return wareHouse;
    }
    return null;
  }

  public TileEntityCreateShop getCreateShopTileEntity() {
    if (super.getTileEntity() instanceof TileEntityCreateShop shop) {
      return shop;
    }
    return null;
  }

  @Nullable
  public CreateShopBlockEntity getPickupBlockEntity() {
    if (pickupPos == null) {
      return null;
    }
    Level level = getColony() == null ? null : getColony().getWorld();
    if (level == null) {
      return null;
    }
    BlockEntity entity = level.getBlockEntity(pickupPos);
    if (entity instanceof CreateShopBlockEntity shopBlock) {
      return shopBlock;
    }
    return null;
  }

  @Nullable
  public BlockPos getPickupPos() {
    return pickupPos;
  }

  @Nullable
  public CreateShopOutputBlockEntity getOutputBlockEntity() {
    if (outputPos == null) {
      return null;
    }
    Level level = getColony() == null ? null : getColony().getWorld();
    if (level == null) {
      return null;
    }
    BlockEntity entity = level.getBlockEntity(outputPos);
    if (entity instanceof CreateShopOutputBlockEntity output) {
      return output;
    }
    return null;
  }

  public boolean hasOutputBlock() {
    return outputPos != null;
  }

  @Nullable
  public BlockPos getOutputPos() {
    return outputPos;
  }

  Set<BlockPos> getContainerList() {
    return containerList;
  }

  int getContainerCount() {
    return containerList.size();
  }

  BlockPos getBuilderHutPos() {
    return builderHutPos;
  }

  void setBuilderHutPos(BlockPos builderHutPos) {
    this.builderHutPos = builderHutPos;
  }

  void setPickupPos(BlockPos pickupPos) {
    this.pickupPos = pickupPos;
  }

  public boolean isPermaWaitFullStack() {
    return permaManager.isPermaWaitFullStack();
  }

  public Set<ResourceLocation> getPermaOres() {
    return permaManager.getPermaOres();
  }

  public boolean canUsePermaRequests() {
    return false; // disabled — gauge-based restocking replaces perma requests
  }

  public java.util.List<String> getPermaPendingDebugLines() {
    return permaManager.getPendingPermaDebugLines(getColony());
  }

  public boolean hasContainerPosition(BlockPos pos) {
    return containerList.contains(pos) || getLocation().getInDimensionLocation().equals(pos);
  }

  @Override
  public Map<Predicate<ItemStack>, Tuple<Integer, Boolean>> getRequiredItemsAndAmount() {
    Map<Predicate<ItemStack>, Tuple<Integer, Boolean>> base = super.getRequiredItemsAndAmount();
    Item beltItem = BuiltInRegistries.ITEM.get(ShopBeltBlueprints.beltItemId());
    if (beltItem == null || beltItem == net.minecraft.world.item.Items.AIR) {
      return base;
    }
    Map<Predicate<ItemStack>, Tuple<Integer, Boolean>> result = new java.util.HashMap<>(base);
    result.put(stack -> stack != null && stack.getItem() == beltItem, new Tuple<>(1, Boolean.TRUE));
    return result;
  }

  @Override
  public void requestRepair(BlockPos pos) {
    for (BlockPos containerPos : containerList) {
      Level world = getColony().getWorld();
      if (world == null) {
        continue;
      }
      BlockEntity entity = world.getBlockEntity(containerPos);
      if (entity instanceof TileEntityRack rack) {
        rack.setInWarehouse(Boolean.TRUE);
      }
    }
    super.requestRepair(pos);
    beltManager.onRepair();
  }

  @Override
  public void onPlacement() {
    super.onPlacement();
    ensureWarehouseRegistration();
    ensurePickupLink();
    beltManager.onPlacement();
  }

  @Override
  public void onUpgradeComplete(int newLevel) {
    super.onUpgradeComplete(newLevel);
    ensureWarehouseRegistration();
    ensurePickupLink();
    beltManager.onUpgrade();
  }

  @Override
  public void onColonyTick(IColony colony) {
    super.onColonyTick(colony);
    migrateLegacyShopCourierAssignments();
    ensureWarehouseRegistration();
    ensurePickupLink();
    resolverHealthCheck.ensureResolverRegistrationHealthy(colony);
    beltManager.tick();
    permaManager.tickPermaRequests(colony);
    if (colony != null) {
      CreateShopRequestResolver resolver = resolverHealthCheck.resolveTickResolver(colony);
      if (isDebugRequests() && resolver == null) {
        com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
            "[CreateShop] tick: resolver missing for shop {}",
            getLocation().getInDimensionLocation());
      }
      if (resolver != null) {
        resolver.tickPendingDeliveries(colony.getRequestManager());
      }
      housekeepingOrchestrator.tick(colony);
      inflightTracker.tick(colony);
      courierDiagnostics.debugCourierAssignments(colony);
    }
  }

  @Override
  public void onRequestedRequestCancelled(
      com.minecolonies.api.colony.requestsystem.manager.IRequestManager manager,
      com.minecolonies.api.colony.requestsystem.request.IRequest<?> request) {
    try {
      super.onRequestedRequestCancelled(manager, request);
      clearPermaPending(request);
      if (request != null) {
        GaugePackagingTask task = pendingGaugeRequests.remove(request.getId());
        if (task != null) {
          gaugePackagingQueue.removeIf(t -> t.requestId().equals(task.requestId()));
          CreateShopBlockEntity pickup = getPickupBlockEntity();
          if (pickup != null) {
            pickup.release(task.requestId());
          }
          markDirty();
        }
      }
    } catch (Exception ex) {
      String token = request == null ? "<null>" : String.valueOf(request.getId());
      String msg =
          ex.getClass().getSimpleName()
              + ":"
              + (ex.getMessage() == null ? "<null>" : ex.getMessage());
      String key = "cancel:" + token;
      String last = lastRequesterError.put(key, msg);
      if (!msg.equals(last)) {
        com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
            "[CreateShop] requester cancel error {} -> {}", token, msg);
      }
    }
  }

  @Override
  public void onRequestedRequestComplete(
      com.minecolonies.api.colony.requestsystem.manager.IRequestManager manager,
      com.minecolonies.api.colony.requestsystem.request.IRequest<?> request) {
    try {
      super.onRequestedRequestComplete(manager, request);
      clearPermaPending(request);
      if (request != null) {
        pendingGaugeRequests.remove(request.getId());
      }
    } catch (Exception ex) {
      String token = request == null ? "<null>" : String.valueOf(request.getId());
      String msg =
          ex.getClass().getSimpleName()
              + ":"
              + (ex.getMessage() == null ? "<null>" : ex.getMessage());
      String key = "complete:" + token;
      String last = lastRequesterError.put(key, msg);
      if (!msg.equals(last)) {
        com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
            "[CreateShop] requester complete error {} -> {}", token, msg);
      }
    }
  }

  @Override
  public void onDestroyed() {
    super.onDestroyed();
    var manager = getColony() == null ? null : getColony().getServerBuildingManager();
    if (manager != null && manager.getWareHouses() != null) {
      manager.getWareHouses().removeIf(w -> w == this);
    }
    warehouseRegistered = false;
  }

  @Override
  public void registerBlockPosition(Block block, BlockPos pos, Level world) {
    if (block instanceof CreateShopBlock) {
      pickupPos = pos;
      BlockEntity entity = world.getBlockEntity(pos);
      if (entity instanceof CreateShopBlockEntity shopBlock) {
        shopBlock.setShopPos(getLocation().getInDimensionLocation());
      }
    }
    if (block instanceof CreateShopOutputBlock) {
      outputPos = pos;
      BlockEntity entity = world.getBlockEntity(pos);
      if (entity instanceof CreateShopOutputBlockEntity output) {
        output.setShopPos(getLocation().getInDimensionLocation());
      }
    }
    if (block instanceof AbstractBlockMinecoloniesRack) {
      BlockEntity entity = world.getBlockEntity(pos);
      rackIndex.onRackRegistered(world, pos, entity);
    }
    super.registerBlockPosition(block, pos, world);
  }

  public void upgradeContainers(Level level) {
    // No storage upgrades for the Create Shop yet.
  }

  @Override
  public ImmutableCollection<IRequestResolver<?>> createResolvers() {
    return resolverFactory.createResolvers(super.createResolvers());
  }

  public CreateShopRequestResolver getShopResolver() {
    return shopResolver;
  }

  void setResolverState(
      CreateShopRequestResolver resolver, IToken<?> deliveryToken, IToken<?> pickupToken) {
    this.shopResolver = resolver;
    this.deliveryResolverToken = deliveryToken;
    this.pickupResolverToken = pickupToken;
    // Apply any FlowStates that were loaded from NBT before the resolver was available.
    if (resolver != null && pendingFlowStatesTag != null) {
      resolver.loadFlowStatesFromNbt(pendingFlowStatesTag);
      pendingFlowStatesTag = null;
    }
  }

  @Nullable
  CreateShopRequestResolver getExistingShopResolver() {
    return shopResolver;
  }

  @Nullable
  IToken<?> getDeliveryResolverToken() {
    return deliveryResolverToken;
  }

  @Nullable
  public IToken<?> getDeliveryResolverTokenPublic() {
    return deliveryResolverToken;
  }

  @Nullable
  IToken<?> getPickupResolverToken() {
    return pickupResolverToken;
  }

  @Nullable
  public CreateShopRequestResolver getOrCreateShopResolver() {
    if (shopResolver == null) {
      getResolvers();
    }
    return shopResolver;
  }

  public boolean hasActiveWorker() {
    return workerStatus.hasActiveWorker();
  }

  public boolean hasAvailableWorker() {
    return workerStatus.hasAvailableWorker();
  }

  public boolean hasHousekeepingAvailableWorker() {
    return workerStatus.hasHousekeepingAvailableWorker();
  }

  public String describeHousekeepingBlockReason() {
    return workerStatus.describeHousekeepingBlockReason();
  }

  public boolean isWorkerWorking() {
    return workerStatus.isWorkerWorking();
  }

  public boolean hasResolverWork() {
    CreateShopRequestResolver resolver = getOrCreateShopResolver();
    return (resolver != null && resolver.hasProtectedInventoryWindow()) || hasIncomingRackWork();
  }

  public boolean hasIncomingRackWork() {
    return housekeepingOrchestrator.hasIncomingRackWork();
  }

  /**
   * Creates a colony delivery request for the given item on behalf of a Colony Factory Gauge. Items
   * will be delivered to this shop's hut by a colony courier.
   *
   * @return true if the request was successfully created
   */
  /**
   * Attempts to request {@code amount} of {@code item} for a Gauge, clamped to what the Colony
   * Warehouse actually holds (partial deliveries are allowed). Returns the amount actually
   * requested, or 0 if no request was created — the caller must use this returned amount (not the
   * requested {@code amount}) for "promised" UI display, since it can be smaller.
   */
  public int requestForGauge(ItemStack item, int amount, String gaugeAddress) {
    if (item.isEmpty() || amount <= 0) {
      if (isDebugRequests()) {
        TheSettlerXCreate.LOGGER.info(
            "[ColonyGauge] requestForGauge skip reason=invalid-args item={} amount={}",
            item,
            amount);
      }
      return 0;
    }
    int minLevel = Config.PERMA_MIN_BUILDING_LEVEL.get();
    if (getBuildingLevel() < minLevel) {
      if (isDebugRequests()) {
        TheSettlerXCreate.LOGGER.info(
            "[ColonyGauge] requestForGauge skip reason=building-level-too-low item={} level={} required={}",
            item.getItem(),
            getBuildingLevel(),
            minLevel);
      }
      return 0;
    }
    IColony colony = getColony();
    if (colony == null) {
      if (isDebugRequests()) {
        TheSettlerXCreate.LOGGER.info(
            "[ColonyGauge] requestForGauge skip reason=no-colony item={}", item.getItem());
      }
      return 0;
    }
    IRequester requester = getRequester();
    if (requester == null) {
      if (isDebugRequests()) {
        TheSettlerXCreate.LOGGER.info(
            "[ColonyGauge] requestForGauge skip reason=no-requester item={}", item.getItem());
      }
      return 0;
    }
    if (!isWorkerWorking()) {
      if (isDebugRequests()) {
        TheSettlerXCreate.LOGGER.info(
            "[ColonyGauge] requestForGauge skip reason=worker-not-working item={}", item.getItem());
      }
      return 0;
    }
    // Only place a colony request once we've confirmed the Colony Warehouse actually has the item
    // — same check the perma-request system already uses
    // (ShopPermaRequestManager.countInWarehouses).
    // This is the whole point of the Gauge: pull from the Colony Warehouse, not Create's stock
    // network (vanilla Create Factory Gauges already cover that case).
    int available = ShopPermaRequestManager.countInWarehouses(this, item);
    if (available <= 0) {
      if (isDebugRequests()) {
        TheSettlerXCreate.LOGGER.info(
            "[ColonyGauge] requestForGauge skip reason=nothing-in-warehouse item={} requested={}",
            item.getItem(),
            amount);
      }
      return 0;
    }
    int actualAmount = Math.min(amount, available);

    IStandardRequestManager manager = (IStandardRequestManager) colony.getRequestManager();
    Stack deliverable = new Stack(item.copyWithCount(1), actualAmount, 1);
    IToken<?> token = manager.createAndAssignRequest(requester, deliverable);
    if (token != null) {
      java.util.UUID requestId = toRequestId(token);
      GaugePackagingTask task =
          new GaugePackagingTask(item.copy(), actualAmount, gaugeAddress, requestId);
      // Queue for packaging (deduplicated by item+address to avoid double-queuing on re-request).
      boolean alreadyQueued =
          gaugePackagingQueue.stream()
              .anyMatch(
                  t ->
                      ItemStack.isSameItem(t.item(), item)
                          && t.gaugeAddress().equals(gaugeAddress));
      if (!alreadyQueued) {
        gaugePackagingQueue.add(task);
        markDirty();
      }
      pendingGaugeRequests.put(token, task);
      // Protect the delivered item from rack housekeeping (which sweeps "unreserved" rack stock
      // back to the warehouse) until CreateShopOutputBlockEntity actually packages it.
      CreateShopBlockEntity pickup = getPickupBlockEntity();
      if (pickup != null) {
        pickup.reserve(requestId, item.copy(), actualAmount);
      }
      if (isDebugRequests()) {
        TheSettlerXCreate.LOGGER.info(
            "[ColonyGauge] request created token={} item={} amount={} available={} address={} queued={}",
            token,
            item.getItem(),
            actualAmount,
            available,
            gaugeAddress,
            !alreadyQueued);
      }
    } else if (isDebugRequests()) {
      TheSettlerXCreate.LOGGER.info(
          "[ColonyGauge] requestForGauge skip reason=createAndAssignRequest-returned-null item={} amount={}",
          item.getItem(),
          actualAmount);
    }
    return token != null ? actualAmount : 0;
  }

  /**
   * Cancels any still-open colony request(s) for the given Gauge item/address — called when a
   * Gauge's promise is cleared or its filter is reset, so a request left unresolved (e.g. no
   * courier assigned to the warehouse) doesn't keep piling up as a duplicate on the next request
   * attempt. Matches both the transient {@code pendingGaugeRequests} tracking (by address) and,
   * since that tracking doesn't survive a world/server restart, a live scan of this shop's own
   * still-open requests (by item — the request payload has no address of its own).
   */
  public int cancelPendingGaugeRequests(ItemStack item, String gaugeAddress) {
    if (gaugeAddress == null || gaugeAddress.isBlank() || getColony() == null) {
      return 0;
    }
    if (!(getColony().getRequestManager() instanceof IStandardRequestManager standard)) {
      return 0;
    }
    Set<IToken<?>> toCancel = new java.util.LinkedHashSet<>();
    for (var entry : pendingGaugeRequests.entrySet()) {
      if (entry.getValue().gaugeAddress().equals(gaugeAddress)) {
        toCancel.add(entry.getKey());
      }
    }
    IRequester requester = getRequester();
    if (item != null && !item.isEmpty() && requester != null) {
      for (IRequest<?> request :
          standard.getRequestHandler().getRequestsMadeByRequester(requester)) {
        if (request == null || request.hasParent() || isTerminalRequestState(request.getState())) {
          continue;
        }
        if (request.getRequest() instanceof Stack stack
            && ItemStack.isSameItem(stack.getStack(), item)) {
          toCancel.add(request.getId());
        }
      }
    }
    CreateShopBlockEntity pickup = getPickupBlockEntity();
    int cancelled = 0;
    for (IToken<?> token : toCancel) {
      try {
        standard.updateRequestState(token, RequestState.CANCELLED);
        cancelled++;
      } catch (Exception ex) {
        if (isDebugRequests()) {
          TheSettlerXCreate.LOGGER.info(
              "[ColonyGauge] cancelPendingGaugeRequests failed token={} error={}",
              token,
              ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
        }
      }
      pendingGaugeRequests.remove(token);
      if (pickup != null) {
        pickup.release(toRequestId(token));
      }
    }
    gaugePackagingQueue.removeIf(t -> t.gaugeAddress().equals(gaugeAddress));
    if (cancelled > 0) {
      markDirty();
      if (isDebugRequests()) {
        TheSettlerXCreate.LOGGER.info(
            "[ColonyGauge] cancelPendingGaugeRequests address={} cancelled={}",
            gaugeAddress,
            cancelled);
      }
    }
    return cancelled;
  }

  /** Returns the next gauge packaging task without removing it, or null if queue is empty. */
  @Nullable
  /**
   * Tokens of colony requests this shop currently has open as a requester on behalf of a Colony
   * Factory Gauge (delivery-to-shop, not the shop resolving a customer request) — surfaced in the
   * shop's task UI, which otherwise only shows requests where the shop is the resolver.
   */
  public List<IToken<?>> getPendingGaugeRequestTokens() {
    return List.copyOf(pendingGaugeRequests.keySet());
  }

  public GaugePackagingTask peekNextGaugeTask() {
    return gaugePackagingQueue.isEmpty() ? null : gaugePackagingQueue.get(0);
  }

  /** Removes and returns the next gauge packaging task (call after successfully packaging). */
  public void completeNextGaugeTask() {
    if (!gaugePackagingQueue.isEmpty()) {
      GaugePackagingTask completed = gaugePackagingQueue.remove(0);
      CreateShopBlockEntity pickup = getPickupBlockEntity();
      if (pickup != null) {
        pickup.release(completed.requestId());
      }
      markDirty();
    }
  }

  public boolean hasGaugeTask() {
    return !gaugePackagingQueue.isEmpty();
  }

  public boolean isHousekeepingAllowed() {
    IColony colony = getColony();
    if (colony == null) {
      return false;
    }
    return housekeepingOrchestrator.isHousekeepingAllowed(colony, getPickupBlockEntity());
  }

  public boolean hasActiveLocalDeliveryChildrenForInflight(IColony colony) {
    return housekeepingOrchestrator.hasActiveLocalDeliveryChildren(colony, getPickupBlockEntity());
  }

  public boolean hasUrgentWork() {
    return hasResolverWork();
  }

  public boolean hasCapacityStall() {
    TileEntityCreateShop tile = getCreateShopTileEntity();
    return tile != null && tile.hasCapacityStall();
  }

  @Nullable
  TileEntityCreateShop.CapacityStallNotice consumeCapacityStallNotice() {
    TileEntityCreateShop tile = getCreateShopTileEntity();
    if (tile == null) {
      return null;
    }
    return tile.consumeCapacityStallNotice();
  }

  public void notifyMissingNetwork() {
    networkNotifier.notifyMissingNetwork();
  }

  LostPackageReorderResult restartLostPackageDetailed(
      ItemStack stackKey, int remaining, String requesterName, String address, long requestedAt) {
    if (isDebugRequests()) {
      com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
          "[CreateShop] lost-package restart requested item={} remaining={} requester='{}' address='{}'",
          stackKey == null || stackKey.isEmpty() ? "<empty>" : stackKey.getHoverName().getString(),
          remaining,
          requesterName,
          address);
    }
    if (stackKey == null || stackKey.isEmpty() || remaining <= 0) {
      if (isDebugRequests()) {
        com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
            "[CreateShop] lost-package restart rejected: invalid input");
      }
      return new LostPackageReorderResult(0, LostPackageReorderStatus.INVALID_INPUT);
    }
    TileEntityCreateShop tile = getCreateShopTileEntity();
    CreateShopBlockEntity pickup = getPickupBlockEntity();
    if (tile == null || pickup == null || tile.getStockNetworkId() == null) {
      if (isDebugRequests()) {
        com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
            "[CreateShop] lost-package restart rejected: tilePresent={} pickupPresent={} networkPresent={}",
            tile != null,
            pickup != null,
            tile != null && tile.getStockNetworkId() != null);
      }
      return new LostPackageReorderResult(0, LostPackageReorderStatus.MISSING_CONTEXT);
    }
    int trackedRemaining =
        pickup.getInflightRemaining(stackKey, requesterName, address, requestedAt);
    int reorderTarget = Math.min(Math.max(1, remaining), Math.max(0, trackedRemaining));
    if (reorderTarget <= 0) {
      if (isDebugRequests()) {
        com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
            "[CreateShop] lost-package restart skipped: no tracked inflight remaining for tuple");
      }
      return new LostPackageReorderResult(0, LostPackageReorderStatus.NO_TRACKED_INFLIGHT);
    }
    ItemStack requested = stackKey.copy();
    requested.setCount(reorderTarget);
    var reordered =
        new CreateNetworkFacade(tile).requestStacksImmediate(List.of(requested), requesterName);
    if (reordered.isEmpty()) {
      if (isDebugRequests()) {
        com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
            "[CreateShop] lost-package restart failed: network returned empty reorder list");
      }
      return new LostPackageReorderResult(0, LostPackageReorderStatus.NO_NETWORK_STOCK);
    }
    int requestedCount = 0;
    for (ItemStack stack : reordered) {
      if (stack != null && !stack.isEmpty()) {
        requestedCount += stack.getCount();
      }
    }
    int consumed =
        pickup.consumeInflight(stackKey, requestedCount, requesterName, address, requestedAt);
    if (isDebugRequests()) {
      com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
          "[CreateShop] lost-package restart requester={} item={} requested={} consumedOld={}",
          requesterName,
          stackKey.getHoverName().getString(),
          requestedCount,
          consumed);
    }
    return new LostPackageReorderResult(consumed, LostPackageReorderStatus.SUCCESS);
  }

  /** Debug/helper wrapper for command harness flows. */
  public int restartLostPackage(
      ItemStack stackKey, int remaining, String requesterName, String address, long requestedAt) {
    return restartLostPackageDetailed(stackKey, remaining, requesterName, address, requestedAt)
        .consumed();
  }

  public int acceptLostPackageFromPlayer(
      Player player,
      ItemStack stackKey,
      int remaining,
      String requesterName,
      String address,
      long requestedAt) {
    if (isDebugRequests()) {
      com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
          "[CreateShop] lost-package handover requested player={} item={} remaining={} requester='{}' address='{}'",
          player == null ? "<null>" : player.getName().getString(),
          stackKey == null || stackKey.isEmpty() ? "<empty>" : stackKey.getHoverName().getString(),
          remaining,
          requesterName,
          address);
    }
    if (player == null || stackKey == null || stackKey.isEmpty()) {
      if (isDebugRequests()) {
        com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
            "[CreateShop] lost-package handover rejected: invalid input");
      }
      return 0;
    }
    TileEntityCreateShop tile = getCreateShopTileEntity();
    CreateShopBlockEntity pickup = getPickupBlockEntity();
    if (tile == null || pickup == null) {
      if (isDebugRequests()) {
        com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
            "[CreateShop] lost-package handover rejected: tilePresent={} pickupPresent={}",
            tile != null,
            pickup != null);
      }
      return 0;
    }
    var inventory = player.getInventory();
    rackIndex.ensureRackContainers();
    int targetAmount = Math.max(1, remaining);
    int inflightBefore = pickup.getInflightRemaining(stackKey, requesterName, address, requestedAt);
    if (isDebugRequests()) {
      com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
          "[CreateShop] lost-package handover precheck inventorySlots={} target={} inflightBefore={} requester='{}' address='{}'",
          inventory.getContainerSize(),
          targetAmount,
          inflightBefore,
          requesterName,
          address);
    }
    int totalConsumed = 0;
    int totalInsertedMatching = 0;
    int scannedPackages = 0;
    int matchedPackages = 0;
    int removedPackages = 0;
    for (int slot = 0;
        slot < inventory.getContainerSize() && totalConsumed < targetAmount;
        slot++) {
      ItemStack candidate = inventory.getItem(slot);
      boolean isPackage =
          candidate != null
              && !candidate.isEmpty()
              && com.simibubi.create.content.logistics.box.PackageItem.isPackage(candidate);
      if (isPackage) {
        scannedPackages++;
      }
      int matching = ShopLostPackageInteraction.countMatchingInPackage(candidate, stackKey);
      if (isDebugRequests() && candidate != null && !candidate.isEmpty()) {
        if (isPackage || matching > 0) {
          com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
              "[CreateShop] lost-package handover scan slot={} stack={} isPackage={} matchingCount={}",
              slot,
              candidate.getHoverName().getString(),
              isPackage,
              matching);
        }
      }
      if (matching <= 0) {
        continue;
      }
      matchedPackages++;
      List<ItemStack> previewUnpacked = ShopLostPackageInteraction.unpackPackage(candidate);
      if (isDebugRequests()) {
        com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
            "[CreateShop] lost-package handover slot={} previewUnpackedStacks={} matching={}",
            slot,
            previewUnpacked.size(),
            matching);
      }
      if (previewUnpacked.isEmpty()) {
        if (isDebugRequests()) {
          com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
              "[CreateShop] lost-package handover slot={} skip: preview unpack empty", slot);
        }
        continue;
      }
      List<ItemStack> previewAccepted = tile.planInboundAcceptedStacks(previewUnpacked);
      int previewInsertedMatching = countMatching(previewAccepted, stackKey);
      int consumeTarget =
          Math.min(targetAmount - totalConsumed, Math.max(0, previewInsertedMatching));
      if (consumeTarget <= 0) {
        if (isDebugRequests()) {
          com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
              "[CreateShop] lost-package handover slot={} skip: preview accepted no matching items",
              slot);
        }
        continue;
      }
      int strictRemaining =
          pickup.getInflightRemaining(stackKey, requesterName, address, requestedAt);
      int looseRemaining = pickup.getInflightRemaining(stackKey, "", "");
      if (strictRemaining < consumeTarget && looseRemaining < consumeTarget) {
        if (isDebugRequests()) {
          com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
              "[CreateShop] lost-package handover slot={} skip: no inflight remainder for consumeTarget={} strictRemaining={} looseRemaining={}",
              slot,
              consumeTarget,
              strictRemaining,
              looseRemaining);
        }
        continue;
      }
      ItemStack removedPackage = inventory.removeItem(slot, 1);
      if (removedPackage.isEmpty()) {
        if (isDebugRequests()) {
          com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
              "[CreateShop] lost-package handover slot={} failed: package remove returned empty",
              slot);
        }
        continue;
      }
      removedPackages++;
      List<ItemStack> unpacked = ShopLostPackageInteraction.unpackPackage(removedPackage);
      if (unpacked.isEmpty() && !previewUnpacked.isEmpty()) {
        unpacked = new ArrayList<>(previewUnpacked.size());
        for (ItemStack stack : previewUnpacked) {
          if (stack != null && !stack.isEmpty()) {
            unpacked.add(stack.copy());
          }
        }
      }
      if (isDebugRequests()) {
        com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
            "[CreateShop] lost-package handover slot={} unpackedStacks={}", slot, unpacked.size());
      }
      if (unpacked.isEmpty()) {
        if (isDebugRequests()) {
          com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
              "[CreateShop] lost-package handover slot={} skipped: package unpacked empty", slot);
        }
        continue;
      }
      List<ItemStack> leftovers = tile.insertIntoRacksOnly(unpacked);
      if (isDebugRequests()) {
        com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
            "[CreateShop] lost-package handover slot={} insertedStacks={} leftoverStacks={}",
            slot,
            unpacked.size() - leftovers.size(),
            leftovers.size());
      }
      for (ItemStack leftover : leftovers) {
        if (!leftover.isEmpty()) {
          Level level = getColony() == null ? null : getColony().getWorld();
          BlockPos dropPos = getLocation().getInDimensionLocation();
          if (level != null) {
            InventoryUtils.spawnItemStack(
                level,
                dropPos.getX() + 0.5D,
                dropPos.getY() + 1.0D,
                dropPos.getZ() + 0.5D,
                leftover);
          }
        }
      }
      int insertedMatching = countMatching(unpacked, stackKey) - countMatching(leftovers, stackKey);
      totalInsertedMatching += Math.max(0, insertedMatching);
      consumeTarget = Math.min(targetAmount - totalConsumed, Math.max(0, insertedMatching));
      int consumed =
          pickup.consumeInflight(stackKey, consumeTarget, requesterName, address, requestedAt);
      totalConsumed += Math.max(0, consumed);
      if (isDebugRequests()) {
        com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
            "[CreateShop] lost-package handover requester={} item={} inserted={} consumedOld={} totalConsumed={} target={}",
            requesterName,
            stackKey.getHoverName().getString(),
            insertedMatching,
            consumed,
            totalConsumed,
            targetAmount);
        if (consumed <= 0 && consumeTarget > 0) {
          strictRemaining =
              pickup.getInflightRemaining(stackKey, requesterName, address, requestedAt);
          looseRemaining = pickup.getInflightRemaining(stackKey, "", "");
          com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
              "[CreateShop] lost-package handover consume-miss slot={} consumeTarget={} strictRemaining={} looseRemaining={}",
              slot,
              consumeTarget,
              strictRemaining,
              looseRemaining);
        }
      }
      if (consumeTarget > 0 && consumed <= 0) {
        // Avoid draining additional player packages when inflight tuple cannot be consumed.
        break;
      }
    }
    int inflightAfter = pickup.getInflightRemaining(stackKey, requesterName, address, requestedAt);
    if (isDebugRequests()) {
      com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
          "[CreateShop] lost-package handover summary scannedPackages={} matchedPackages={} removedPackages={} insertedMatchingTotal={} consumedTotal={} target={} inflightBefore={} inflightAfter={}",
          scannedPackages,
          matchedPackages,
          removedPackages,
          totalInsertedMatching,
          totalConsumed,
          targetAmount,
          inflightBefore,
          inflightAfter);
    }
    if (totalConsumed > 0) {
      return totalConsumed;
    }
    if (isDebugRequests()) {
      com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
          "[CreateShop] lost-package handover failed: no matching package found in player inventory or no inflight consumed (insertedMatchingTotal={})",
          totalInsertedMatching);
    }
    return 0;
  }

  public int cancelLostPackage(
      ItemStack stackKey, String requesterName, String address, long requestedAt) {
    return cancelLostPackage(null, stackKey, requesterName, address, requestedAt);
  }

  public int cancelLostPackage(
      @Nullable java.util.UUID requestUuid,
      ItemStack stackKey,
      String requesterName,
      String address,
      long requestedAt) {
    if (stackKey == null || stackKey.isEmpty()) {
      return 0;
    }
    CreateShopBlockEntity pickup = getPickupBlockEntity();
    if (pickup == null) {
      return 0;
    }
    // UUID-first: precise and drift-free for entries recorded since Phase 3.1.
    int cleared = requestUuid != null ? pickup.cancelInflightByUuid(requestUuid) : 0;
    // String-matching fallback for legacy entries (requestUuid == null in NBT).
    if (cleared <= 0) {
      cleared = pickup.cancelInflight(stackKey, requesterName, address, requestedAt);
    }
    if (isDebugRequests()) {
      com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
          "[CreateShop] lost-package cancel uuid={} item={} requester='{}' address='{}' cleared={}",
          requestUuid,
          stackKey.getHoverName().getString(),
          requesterName,
          address,
          cleared);
    }
    return cleared;
  }

  /** Clears request runtime tracking caches used by Create Shop for debug/test clean-state runs. */
  public int clearRuntimeTrackingForDebug() {
    advanceLostPackageInteractionEpoch("debug-reset");
    CreateShopBlockEntity pickup = getPickupBlockEntity();
    if (pickup == null) {
      return 0;
    }
    int cleared = pickup.clearRuntimeTrackingForDebug();
    if (isDebugRequests() && cleared > 0) {
      com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
          "[CreateShop] debug reset runtime tracking shop={} cleared={}",
          getLocation() == null ? "<unknown>" : getLocation().getInDimensionLocation(),
          cleared);
    }
    return cleared;
  }

  long getLostPackageInteractionEpoch() {
    return lostPackageInteractionEpoch;
  }

  void advanceLostPackageInteractionEpoch(String reason) {
    lostPackageInteractionEpoch++;
    if (isDebugRequests()) {
      com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
          "[CreateShop] lost-package interaction epoch advanced to {} reason={}",
          lostPackageInteractionEpoch,
          reason == null ? "<none>" : reason);
    }
  }

  public int cancelLostPackageRequestAndInflight(
      ItemStack stackKey, int remaining, String requesterName, String address, long requestedAt) {
    return cancelLostPackageRequestAndInflight(
        null, stackKey, remaining, requesterName, address, requestedAt);
  }

  public int cancelLostPackageRequestAndInflight(
      @Nullable java.util.UUID requestUuid,
      ItemStack stackKey,
      int remaining,
      String requesterName,
      String address,
      long requestedAt) {
    int clearedInflight =
        cancelLostPackage(requestUuid, stackKey, requesterName, address, requestedAt);
    int cancelledRequests =
        new ShopLostPackageRequestCanceller(this)
            .cancelMatchingRequests(stackKey, requesterName, address, requestedAt);
    if (isDebugRequests()) {
      com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
          "[CreateShop] lost-package cancel+requests uuid={} item={} requester='{}' address='{}' clearedInflight={} cancelledRequests={}",
          requestUuid,
          stackKey == null || stackKey.isEmpty() ? "<empty>" : stackKey.getHoverName().getString(),
          requesterName,
          address,
          clearedInflight,
          cancelledRequests);
    }
    if (clearedInflight > 0 || cancelledRequests > 0) {
      return Math.max(Math.max(1, remaining), clearedInflight);
    }
    return 0;
  }

  /**
   * Debug helper: simulate package handover without requiring a real package item in player
   * inventory.
   */
  public int debugSimulateLostPackageHandover(
      ItemStack stackKey, int remaining, String requesterName, String address, long requestedAt) {
    if (stackKey == null || stackKey.isEmpty() || remaining <= 0) {
      return 0;
    }
    TileEntityCreateShop tile = getCreateShopTileEntity();
    CreateShopBlockEntity pickup = getPickupBlockEntity();
    if (tile == null || pickup == null) {
      return 0;
    }
    ItemStack insertStack = stackKey.copy();
    insertStack.setCount(Math.max(1, remaining));
    java.util.List<ItemStack> leftovers = tile.insertIntoRacksOnly(java.util.List.of(insertStack));
    int leftover = 0;
    for (ItemStack stack : leftovers) {
      if (stack != null && !stack.isEmpty()) {
        leftover += stack.getCount();
      }
    }
    int inserted = Math.max(0, insertStack.getCount() - leftover);
    if (inserted <= 0) {
      return 0;
    }
    int consumeTarget = Math.min(Math.max(1, remaining), inserted);
    return pickup.consumeInflight(stackKey, consumeTarget, requesterName, address, requestedAt);
  }

  record LostPackageReorderResult(int consumed, LostPackageReorderStatus status) {}

  enum LostPackageReorderStatus {
    SUCCESS,
    INVALID_INPUT,
    MISSING_CONTEXT,
    NO_TRACKED_INFLIGHT,
    NO_NETWORK_STOCK
  }

  private void ensureWarehouseRegistration() {
    warehouseRegistrar.ensureWarehouseRegistration();
  }

  /**
   * Requests MineColonies' native building pickup flow for items staged in the hut.
   *
   * <p>The pickup requestable stores priority only; the source is the building requester's hut
   * location.
   */
  boolean createNativeHutPickupRequest(int pickupPriority) {
    int effectivePriority =
        Math.max(pickupPriority, AbstractDeliverymanRequestable.getPlayerActionPriority(false));
    return createPickupRequest(effectivePriority);
  }

  @Override
  public boolean createPickupRequest(int pickupPriority) {
    if (!(getColony() != null
        && getColony().getRequestManager() instanceof IStandardRequestManager standard)) {
      return super.createPickupRequest(pickupPriority);
    }

    boolean activePickupRequest = false;
    java.util.Collection<IToken<?>> openPickupRequests =
        getOpenRequestsByRequestableType().get(TypeConstants.PICKUP);
    if (openPickupRequests != null && !openPickupRequests.isEmpty()) {
      for (IToken<?> token : List.copyOf(openPickupRequests)) {
        if (token == null) {
          continue;
        }

        IRequest<?> request;
        try {
          request = standard.getRequestForToken(token);
        } catch (Exception ignored) {
          pruneStalePickupRequestToken(token, "lookup-failed");
          continue;
        }
        if (request == null) {
          pruneStalePickupRequestToken(token, "missing-request");
          continue;
        }
        if (!(request.getRequest() instanceof Pickup)) {
          pruneStalePickupRequestToken(token, "unexpected-request-type");
          continue;
        }

        RequestState state = request.getState();
        if (isTerminalRequestState(state)) {
          if (state == RequestState.CANCELLED) {
            super.onRequestedRequestCancelled(standard, request);
          } else {
            super.onRequestedRequestComplete(standard, request);
          }
          if (isDebugRequests()) {
            com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
                "[CreateShop] pickup request cleanup token={} state={} reason=terminal-open-token",
                token,
                state);
          }
          continue;
        }

        activePickupRequest = true;
        repairOpenPickupRequest(standard, token, request, state);
      }
    }

    if (activePickupRequest) {
      return false;
    }
    return super.createPickupRequest(pickupPriority);
  }

  private void repairOpenPickupRequest(
      IStandardRequestManager manager, IToken<?> token, IRequest<?> request, RequestState state) {
    IRequestResolver<?> resolver = null;
    try {
      resolver = manager.getResolverForRequest(token);
    } catch (Exception ignored) {
      // Reassignment path below will repair missing resolver ownership.
    }

    boolean needsAssignmentKick = state == RequestState.CREATED || state == RequestState.ASSIGNED;
    boolean invalidResolver = resolver != null && !(resolver instanceof PickupRequestResolver);
    boolean missingResolver = resolver == null;

    if (!needsAssignmentKick && !invalidResolver && !missingResolver) {
      return;
    }

    try {
      if (state == RequestState.IN_PROGRESS || invalidResolver || missingResolver) {
        manager.reassignRequest(token, Collections.emptyList());
      } else {
        manager.assignRequest(token);
      }
      if (isDebugRequests()) {
        com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
            "[CreateShop] pickup request repair token={} state={} resolver={} action={}",
            token,
            state,
            resolver == null ? "<null>" : resolver.getClass().getSimpleName(),
            state == RequestState.IN_PROGRESS || invalidResolver || missingResolver
                ? "reassign"
                : "assign");
      }
    } catch (Exception ex) {
      if (isDebugRequests()) {
        com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
            "[CreateShop] pickup request repair failed token={} state={} resolver={} error={}",
            token,
            state,
            resolver == null ? "<null>" : resolver.getClass().getSimpleName(),
            ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
      }
    }
  }

  private void pruneStalePickupRequestToken(IToken<?> token, String reason) {
    if (token == null) {
      return;
    }
    java.util.Collection<IToken<?>> openPickupRequests =
        getOpenRequestsByRequestableType().get(TypeConstants.PICKUP);
    if (openPickupRequests != null) {
      openPickupRequests.remove(token);
      if (openPickupRequests.isEmpty()) {
        getOpenRequestsByRequestableType().remove(TypeConstants.PICKUP);
      }
    }
    java.util.Collection<IToken<?>> buildingRequests = getOpenRequestsByCitizen().get(-1);
    if (buildingRequests != null) {
      buildingRequests.remove(token);
      if (buildingRequests.isEmpty()) {
        getOpenRequestsByCitizen().remove(-1);
      }
    }
    markDirty();
    if (isDebugRequests()) {
      com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
          "[CreateShop] pickup request cleanup token={} reason={}", token, reason);
    }
  }

  private static java.util.UUID toRequestId(IToken<?> token) {
    Object id = token == null ? null : token.getIdentifier();
    if (id instanceof java.util.UUID uuid) {
      return uuid;
    }
    return java.util.UUID.nameUUIDFromBytes(
        String.valueOf(id).getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }

  private static boolean isTerminalRequestState(RequestState state) {
    if (state == null) {
      return false;
    }
    return state == RequestState.CANCELLED
        || state == RequestState.COMPLETED
        || state == RequestState.FAILED
        || state == RequestState.RECEIVED
        || state == RequestState.RESOLVED;
  }

  public void ensureRackContainers() {
    rackIndex.ensureRackContainers();
  }

  /** Returns rack inventory counts for the given stack keys. */
  public java.util.Map<ItemStack, Integer> getStockCountsForKeys(List<ItemStack> keys) {
    return rackIndex.getStockCountsForKeys(keys);
  }

  public List<BigItemStack> getRegisteredStorageStock() {
    return rackIndex.getRegisteredStorageStock();
  }

  public void ensurePickupLink() {
    resolverAssignments.ensurePickupLink();
  }

  public void setPermaWaitFullStack(boolean enabled) {
    permaManager.setPermaWaitFullStack(enabled);
  }

  public void setPermaOre(ResourceLocation itemId, boolean enabled) {
    permaManager.setPermaOre(itemId, enabled);
  }

  boolean trySpawnBeltBlueprint(IColony colony) {
    return beltBlueprints.trySpawnBeltBlueprint(colony);
  }

  boolean hasActiveWorkOrder(IColony colony) {
    if (colony == null || colony.getWorkManager() == null) {
      return false;
    }
    var workOrders =
        colony
            .getWorkManager()
            .getWorkOrdersOfType(com.minecolonies.core.colony.workorders.WorkOrderBuilding.class);
    if (workOrders == null || workOrders.isEmpty()) {
      return false;
    }
    BlockPos location = getLocation().getInDimensionLocation();
    for (var order : workOrders) {
      if (order == null || order.getLocation() == null) {
        continue;
      }
      if (order.getLocation().equals(location)) {
        return true;
      }
    }
    return false;
  }

  private void clearPermaPending(
      com.minecolonies.api.colony.requestsystem.request.IRequest<?> request) {
    permaManager.clearPermaPending(request);
  }

  private void migrateLegacyShopCourierAssignments() {
    if (legacyCourierMigrationAttempted) {
      return;
    }
    CourierAssignmentModule legacy = getModule(BuildingModules.WAREHOUSE_COURIERS);
    if (legacy == null) {
      return;
    }
    legacyCourierMigrationAttempted = true;
    boolean cleared = false;
    try {
      var citizens = legacy.getAssignedCitizen();
      if (citizens != null && !citizens.isEmpty()) {
        citizens.clear();
        cleared = true;
      }
    } catch (Exception ignored) {
      // Best-effort migration only.
    }
    try {
      var entities = legacy.getAssignedEntities();
      if (entities != null && !entities.isEmpty()) {
        entities.clear();
        cleared = true;
      }
    } catch (Exception ignored) {
      // Best-effort migration only.
    }
    if (isDebugRequests()) {
      com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
          "[CreateShop] legacy shop-courier migration modulePresent=true cleared={}", cleared);
    }
  }

  private static int countMatching(List<ItemStack> stacks, ItemStack key) {
    if (stacks == null || stacks.isEmpty() || key == null || key.isEmpty()) {
      return 0;
    }
    int count = 0;
    for (ItemStack stack : stacks) {
      if (stack == null || stack.isEmpty()) {
        continue;
      }
      if (ItemStack.isSameItemSameComponents(stack, key) || ItemStack.isSameItem(stack, key)) {
        count += stack.getCount();
      }
    }
    return count;
  }

  public static List<ItemStack> getOreCandidates() {
    List<ItemStack> stacks = new ArrayList<>();
    TagKey<Item> primary =
        TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", "ores"));
    TagKey<Item> fallback =
        TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("forge", "ores"));
    if (!collectTagItems(primary, stacks)) {
      collectTagItems(fallback, stacks);
    }
    stacks.sort(
        Comparator.comparing(stack -> BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()));
    return stacks;
  }

  private static boolean collectTagItems(TagKey<Item> tag, List<ItemStack> stacks) {
    var optional = BuiltInRegistries.ITEM.getTag(tag);
    if (optional.isEmpty()) {
      return false;
    }
    for (Holder<Item> holder : optional.get()) {
      Item item = holder.value();
      if (item == null || item == net.minecraft.world.item.Items.AIR) {
        continue;
      }
      stacks.add(new ItemStack(item, 1));
    }
    return !stacks.isEmpty();
  }

  @Override
  public void deserializeNBT(
      net.minecraft.core.HolderLookup.Provider provider, CompoundTag compound) {
    super.deserializeNBT(provider, compound);
    if (compound.contains(TAG_PICKUP_POS)) {
      pickupPos = BlockPos.of(compound.getLong(TAG_PICKUP_POS));
    }
    if (compound.contains(TAG_OUTPUT_POS)) {
      outputPos = BlockPos.of(compound.getLong(TAG_OUTPUT_POS));
    }
    if (compound.contains(TAG_BUILDER_HUT_POS)) {
      builderHutPos = BlockPos.of(compound.getLong(TAG_BUILDER_HUT_POS));
    }
    permaManager.loadPerma(compound);
    // Buffer FlowStates for lazy application when the resolver connects (setResolverState).
    if (compound.contains(TAG_FLOW_STATES)) {
      net.minecraft.nbt.CompoundTag flowTag = compound.getCompound(TAG_FLOW_STATES);
      if (shopResolver != null) {
        shopResolver.loadFlowStatesFromNbt(flowTag);
      } else {
        pendingFlowStatesTag = flowTag;
      }
    }
    gaugePackagingQueue.clear();
    if (compound.contains("GaugePackagingQueue", 9)) {
      net.minecraft.nbt.ListTag list = compound.getList("GaugePackagingQueue", 10);
      for (int i = 0; i < list.size(); i++) {
        CompoundTag t = list.getCompound(i);
        ItemStack item = ItemStack.parseOptional(provider, t.getCompound("Item"));
        int amount = t.getInt("Amount");
        String address = t.getString("Address");
        if (!item.isEmpty() && amount > 0 && !address.isEmpty()) {
          java.util.UUID requestId =
              t.contains("RequestId")
                  ? java.util.UUID.fromString(t.getString("RequestId"))
                  : java.util.UUID.randomUUID();
          gaugePackagingQueue.add(new GaugePackagingTask(item, amount, address, requestId));
        }
      }
    }
  }

  @Override
  public CompoundTag serializeNBT(net.minecraft.core.HolderLookup.Provider provider) {
    CompoundTag tag = super.serializeNBT(provider);
    if (pickupPos != null) {
      tag.putLong(TAG_PICKUP_POS, pickupPos.asLong());
    }
    if (outputPos != null) {
      tag.putLong(TAG_OUTPUT_POS, outputPos.asLong());
    }
    permaManager.savePerma(tag);
    if (builderHutPos != null) {
      tag.putLong(TAG_BUILDER_HUT_POS, builderHutPos.asLong());
    }
    if (shopResolver != null) {
      shopResolver.saveFlowStatesToNbt(tag);
    }
    if (!gaugePackagingQueue.isEmpty()) {
      net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
      for (GaugePackagingTask task : gaugePackagingQueue) {
        CompoundTag t = new CompoundTag();
        t.put("Item", task.item().save(provider));
        t.putInt("Amount", task.amount());
        t.putString("Address", task.gaugeAddress());
        t.putString("RequestId", task.requestId().toString());
        list.add(t);
      }
      tag.put("GaugePackagingQueue", list);
    }
    return tag;
  }
}
