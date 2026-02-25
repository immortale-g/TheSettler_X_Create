package com.thesettler_x_create.minecolonies.building;

import com.google.common.collect.ImmutableCollection;
import com.minecolonies.api.blocks.AbstractBlockMinecoloniesRack;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.workerbuildings.IWareHouse;
import com.minecolonies.api.colony.requestsystem.resolver.IRequestResolver;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.api.tileentities.AbstractTileEntityWareHouse;
import com.minecolonies.api.util.InventoryUtils;
import com.minecolonies.api.util.constant.TypeConstants;
import com.minecolonies.core.colony.buildings.AbstractBuilding;
import com.minecolonies.core.colony.buildings.modules.BuildingModules;
import com.minecolonies.core.colony.buildings.modules.CourierAssignmentModule;
import com.minecolonies.core.colony.requestsystem.management.IStandardRequestManager;
import com.minecolonies.core.tileentities.TileEntityRack;
import com.simibubi.create.content.logistics.BigItemStack;
import com.thesettler_x_create.Config;
import com.thesettler_x_create.block.CreateShopBlock;
import com.thesettler_x_create.block.CreateShopOutputBlock;
import com.thesettler_x_create.blockentity.CreateShopBlockEntity;
import com.thesettler_x_create.blockentity.CreateShopOutputBlockEntity;
import com.thesettler_x_create.create.CreateNetworkFacade;
import com.thesettler_x_create.minecolonies.requestsystem.resolver.CreateShopRequestResolver;
import com.thesettler_x_create.minecolonies.tileentity.TileEntityCreateShop;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
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
public class BuildingCreateShop extends AbstractBuilding implements IWareHouse {
  public static final String SCHEMATIC_NAME = "createshop";
  private static final long HOUSEKEEPING_TRANSFER_INTERVAL = 20L * 3L;
  private static final long HOUSEKEEPING_DEBUG_COOLDOWN = 20L * 5L;
  private static final int HOUSEKEEPING_MAX_CATCHUP_STACKS = 16;
  private static final int HOUSEKEEPING_TRANSFER_STACKS = 1;

  static boolean isDebugRequests() {
    return Config.DEBUG_LOGGING.getAsBoolean();
  }

  private static final String TAG_PICKUP_POS = "PickupPos";
  private static final String TAG_OUTPUT_POS = "OutputPos";
  static final String TAG_PERMA_ORES = "PermaOres";
  static final String TAG_PERMA_WAIT_FULL = "PermaWaitFullStack";
  private static final String TAG_BUILDER_HUT_POS = "BuilderHutPos";

  private final java.util.Map<String, String> lastRequesterError = new java.util.HashMap<>();
  boolean warehouseRegistered;
  private CreateShopRequestResolver shopResolver;
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
  private long lastResolverHealthcheckTick = -1L;
  private long lastHousekeepingTransferTick = -1L;
  private long lastHousekeepingWorkCheckTick = -1L;
  private long lastHousekeepingDebugTick = -1L;
  private boolean cachedHasIncomingRackWork;
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

  @Override
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

  @Nullable
  public BlockPos getOutputPos() {
    return outputPos;
  }

  Set<BlockPos> getContainerList() {
    return containerList;
  }

  boolean hasContainer(BlockPos pos) {
    return containerList.contains(pos);
  }

  void addContainer(BlockPos pos) {
    containerList.add(pos);
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
    return isBuilt() && getBuildingLevel() >= Config.PERMA_MIN_BUILDING_LEVEL.getAsInt();
  }

  @Override
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
    ensureResolverRegistrationHealthy(colony);
    beltManager.tick();
    permaManager.tickPermaRequests(colony);
    if (colony != null) {
      CreateShopRequestResolver resolver = resolveTickResolver(colony);
      if (isDebugRequests() && resolver == null) {
        com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
            "[CreateShop] tick: resolver missing for shop {}",
            getLocation().getInDimensionLocation());
      }
      if (resolver != null) {
        resolver.tickPendingDeliveries(colony.getRequestManager());
      }
      tickIncomingRackHousekeeping(colony);
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
    getColony().getServerBuildingManager().removeWareHouse(this);
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

  @Override
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
    return (resolver != null && resolver.hasActiveWork()) || hasIncomingRackWork();
  }

  public boolean hasIncomingRackWork() {
    Level level = getColony() == null ? null : getColony().getWorld();
    if (level == null || level.isClientSide) {
      return false;
    }
    long now = level.getGameTime();
    if (lastHousekeepingWorkCheckTick == now) {
      return cachedHasIncomingRackWork;
    }
    lastHousekeepingWorkCheckTick = now;
    TileEntityCreateShop tile = getCreateShopTileEntity();
    CreateShopBlockEntity pickup = getPickupBlockEntity();
    cachedHasIncomingRackWork = tile != null && tile.hasUnreservedRackItems(pickup);
    return cachedHasIncomingRackWork;
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

  public boolean restartLostPackage(
      ItemStack stackKey, int remaining, String requesterName, String address) {
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
      return false;
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
      return false;
    }
    ItemStack requested = stackKey.copy();
    requested.setCount(remaining);
    var reordered =
        new CreateNetworkFacade(tile).requestStacksImmediate(List.of(requested), requesterName);
    if (reordered.isEmpty()) {
      if (isDebugRequests()) {
        com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
            "[CreateShop] lost-package restart failed: network returned empty reorder list");
      }
      return false;
    }
    int requestedCount = 0;
    for (ItemStack stack : reordered) {
      if (stack != null && !stack.isEmpty()) {
        requestedCount += stack.getCount();
      }
    }
    int consumed = pickup.consumeInflight(stackKey, requestedCount, requesterName, address);
    if (isDebugRequests()) {
      com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
          "[CreateShop] lost-package restart requester={} item={} requested={} consumedOld={}",
          requesterName,
          stackKey.getHoverName().getString(),
          requestedCount,
          consumed);
    }
    // Close the interaction as soon as a replacement order was successfully queued.
    // Inflight cleanup is best-effort and can be partial if old entries drifted.
    return true;
  }

  public boolean acceptLostPackageFromPlayer(
      Player player, ItemStack stackKey, int remaining, String requesterName, String address) {
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
      return false;
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
      return false;
    }
    var inventory = player.getInventory();
    for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
      ItemStack candidate = inventory.getItem(slot);
      if (isDebugRequests() && candidate != null && !candidate.isEmpty()) {
        boolean isPackage =
            com.simibubi.create.content.logistics.box.PackageItem.isPackage(candidate);
        int matching = ShopLostPackageInteraction.countMatchingInPackage(candidate, stackKey);
        if (isPackage || matching > 0) {
          com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
              "[CreateShop] lost-package handover scan slot={} stack={} isPackage={} matchingCount={}",
              slot,
              candidate.getHoverName().getString(),
              isPackage,
              matching);
        }
      }
      if (!ShopLostPackageInteraction.packageContains(candidate, stackKey, 1)) {
        continue;
      }
      List<ItemStack> unpacked = ShopLostPackageInteraction.unpackPackage(candidate);
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
      ItemStack removed = inventory.removeItem(slot, 1);
      if (removed.isEmpty()) {
        if (isDebugRequests()) {
          com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
              "[CreateShop] lost-package handover slot={} failed: package remove returned empty",
              slot);
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
      int targetAmount = Math.max(1, remaining);
      int consumed =
          pickup.consumeInflight(
              stackKey, Math.min(targetAmount, insertedMatching), requesterName, address);
      if (isDebugRequests()) {
        com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
            "[CreateShop] lost-package handover requester={} item={} inserted={} consumedOld={}",
            requesterName,
            stackKey.getHoverName().getString(),
            insertedMatching,
            consumed);
      }
      // Only close the interaction once the full overdue target was actually cleared from inflight.
      // Partial handovers must keep the interaction active so unresolved remainder can be handled.
      return consumed >= targetAmount;
    }
    if (isDebugRequests()) {
      com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
          "[CreateShop] lost-package handover failed: no matching package found in player inventory");
    }
    return false;
  }

  private void ensureWarehouseRegistration() {
    warehouseRegistrar.ensureWarehouseRegistration();
  }

  private void tickIncomingRackHousekeeping(IColony colony) {
    if (colony == null || colony.getWorld() == null || colony.getWorld().isClientSide) {
      return;
    }
    long now = colony.getWorld().getGameTime();
    long elapsed =
        lastHousekeepingTransferTick < 0L
            ? HOUSEKEEPING_TRANSFER_INTERVAL
            : now - lastHousekeepingTransferTick;
    if (elapsed < HOUSEKEEPING_TRANSFER_INTERVAL) {
      if (isDebugRequests() && shouldLogHousekeepingDebug(now)) {
        com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
            "[CreateShop] housekeeping wait cooldown remaining={}t",
            HOUSEKEEPING_TRANSFER_INTERVAL - elapsed);
      }
      return;
    }
    TileEntityCreateShop tile = getCreateShopTileEntity();
    CreateShopBlockEntity pickup = getPickupBlockEntity();
    if (tile == null || pickup == null) {
      cachedHasIncomingRackWork = tile != null && tile.hasUnreservedRackItems(pickup);
      if (isDebugRequests() && shouldLogHousekeepingDebug(now)) {
        com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
            "[CreateShop] housekeeping skip tilePresent={} pickupPresent={} pendingUnreserved={}",
            tile != null,
            pickup != null,
            cachedHasIncomingRackWork);
      }
      return;
    }
    if (!hasHousekeepingAvailableWorker()) {
      cachedHasIncomingRackWork = tile.hasUnreservedRackItems(pickup);
      if (isDebugRequests() && shouldLogHousekeepingDebug(now)) {
        com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
            "[CreateShop] housekeeping blocked reason={} pendingUnreserved={}",
            describeHousekeepingBlockReason(),
            cachedHasIncomingRackWork);
      }
      return;
    }
    int dueStacks =
        Math.max(
            HOUSEKEEPING_TRANSFER_STACKS,
            (int) Math.max(1L, elapsed / HOUSEKEEPING_TRANSFER_INTERVAL));
    int transferBudget = Math.min(HOUSEKEEPING_MAX_CATCHUP_STACKS, dueStacks);
    int moved = tile.moveUnreservedRackStacksToHut(pickup, transferBudget);
    cachedHasIncomingRackWork = tile.hasUnreservedRackItems(pickup);
    if (moved > 0 || cachedHasIncomingRackWork) {
      lastHousekeepingTransferTick = now;
    }
    if (moved > 0 && isDebugRequests()) {
      com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
          "[CreateShop] housekeeping moved unreserved rack stacks to hut count={} budget={} elapsed={}t",
          moved,
          transferBudget,
          elapsed);
    } else if (isDebugRequests() && shouldLogHousekeepingDebug(now)) {
      com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
          "[CreateShop] housekeeping ran but moved=0 pendingUnreserved={} budget={} elapsed={}t",
          cachedHasIncomingRackWork,
          transferBudget,
          elapsed);
    }
  }

  private boolean shouldLogHousekeepingDebug(long now) {
    if (lastHousekeepingDebugTick >= 0L
        && now - lastHousekeepingDebugTick < HOUSEKEEPING_DEBUG_COOLDOWN) {
      return false;
    }
    lastHousekeepingDebugTick = now;
    return true;
  }

  private void ensureResolverRegistrationHealthy(IColony colony) {
    if (colony == null || colony.getWorld() == null || colony.getWorld().isClientSide) {
      return;
    }
    long now = colony.getWorld().getGameTime();
    if (lastResolverHealthcheckTick >= 0L && now - lastResolverHealthcheckTick < 100L) {
      return;
    }
    lastResolverHealthcheckTick = now;

    if (!(colony.getRequestManager() instanceof IStandardRequestManager manager)) {
      return;
    }
    CreateShopRequestResolver resolver = resolveLiveShopResolver(manager);
    if (resolver == null) {
      resolver = getOrCreateShopResolver();
      if (resolver == null) {
        return;
      }
    }
    if (shopResolver == null || !shopResolver.getId().equals(resolver.getId())) {
      setResolverState(resolver, deliveryResolverToken, pickupResolverToken);
    }
    IToken<?> resolverId = resolver.getId();
    boolean resolverKnown = false;
    try {
      manager.getResolverHandler().getResolver(resolverId);
      resolverKnown = true;
    } catch (IllegalArgumentException ignored) {
      // Health-check handles this.
    }

    boolean providerContains =
        manager.getProviderHandler().getRegisteredResolvers(this).contains(resolverId);
    var deliverableAssignments =
        manager
            .getRequestableTypeRequestResolverAssignmentDataStore()
            .getAssignments()
            .get(TypeConstants.DELIVERABLE);
    boolean typeContains =
        deliverableAssignments != null && deliverableAssignments.contains(resolverId);

    boolean hasAnyLocalProviderResolver = hasAnyLocalProviderResolver(manager);
    boolean hasAnyLocalDeliverableResolver =
        hasAnyLocalDeliverableResolver(manager, deliverableAssignments);

    if (resolverKnown
        && (providerContains || hasAnyLocalProviderResolver)
        && (typeContains || hasAnyLocalDeliverableResolver)) {
      return;
    }

    if (isDebugRequests()) {
      com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
          "[CreateShop] resolver health mismatch: resolverKnown={} providerContains={} typeContains={} resolver={}",
          resolverKnown,
          providerContains,
          typeContains,
          resolverId);
    }
    try {
      colony.getRequestManager().onProviderRemovedFromColony(this);
    } catch (Exception ignored) {
      // Best effort cleanup before re-register.
    }
    try {
      colony.getRequestManager().onProviderAddedToColony(this);
    } catch (Exception ex) {
      if (isDebugRequests()) {
        com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
            "[CreateShop] resolver provider repair failed: {}",
            ex.getMessage() == null ? "<null>" : ex.getMessage());
      }
      return;
    }
    if (isDebugRequests()) {
      com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
          "[CreateShop] resolver provider repair triggered for {}", resolverId);
    }
  }

  @Nullable
  private CreateShopRequestResolver resolveTickResolver(IColony colony) {
    if (colony == null || colony.getRequestManager() == null) {
      return getOrCreateShopResolver();
    }
    if (!(colony.getRequestManager() instanceof IStandardRequestManager manager)) {
      return getOrCreateShopResolver();
    }
    CreateShopRequestResolver current = getOrCreateShopResolver();
    var providerResolvers = manager.getProviderHandler().getRegisteredResolvers(this);
    if (providerResolvers == null || providerResolvers.isEmpty()) {
      CreateShopRequestResolver fallback = resolveLiveShopResolver(manager);
      if (fallback != null) {
        if (current == null || !current.getId().equals(fallback.getId())) {
          setResolverState(fallback, deliveryResolverToken, pickupResolverToken);
        }
        return fallback;
      }
      return current;
    }

    var deliverableAssignments =
        manager
            .getRequestableTypeRequestResolverAssignmentDataStore()
            .getAssignments()
            .get(TypeConstants.DELIVERABLE);
    Set<IToken<?>> prioritized = new LinkedHashSet<>();
    if (deliverableAssignments != null) {
      for (IToken<?> token : deliverableAssignments) {
        if (providerResolvers.contains(token)) {
          prioritized.add(token);
        }
      }
    }
    prioritized.addAll(providerResolvers);

    CreateShopRequestResolver selected = null;
    for (IToken<?> token : prioritized) {
      try {
        IRequestResolver<?> resolver = manager.getResolverHandler().getResolver(token);
        if (resolver instanceof CreateShopRequestResolver csr) {
          selected = csr;
          break;
        }
      } catch (IllegalArgumentException ignored) {
        // Ignore stale ids; health-check will repair registration.
      }
    }

    if (selected == null) {
      selected = resolveLiveShopResolver(manager);
    } else {
      // Prefer assignment-backed resolver when provider-prioritized resolver has no work.
      if (!hasAssignedRequestsForResolver(manager, selected.getId())) {
        CreateShopRequestResolver assignmentSelected = findResolverFromAssignments(manager);
        if (assignmentSelected != null
            && !assignmentSelected.getId().equals(selected.getId())
            && hasAssignedRequestsForResolver(manager, assignmentSelected.getId())) {
          if (isDebugRequests()) {
            com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
                "[CreateShop] resolver assignment drift detected: switching {} -> {}",
                selected.getId(),
                assignmentSelected.getId());
          }
          selected = assignmentSelected;
        } else {
          CreateShopRequestResolver ownershipSelected = findResolverFromRequestOwnership(manager);
          if (ownershipSelected != null && !ownershipSelected.getId().equals(selected.getId())) {
            if (isDebugRequests()) {
              com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
                  "[CreateShop] resolver ownership drift detected: switching {} -> {}",
                  selected.getId(),
                  ownershipSelected.getId());
            }
            selected = ownershipSelected;
          }
        }
      }
    }
    if (selected == null) {
      return current;
    }
    if (current == null || !current.getId().equals(selected.getId())) {
      setResolverState(selected, deliveryResolverToken, pickupResolverToken);
      if (isDebugRequests()) {
        com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
            "[CreateShop] resolver synced to registered token {} (previous={})",
            selected.getId(),
            current == null ? "<null>" : current.getId());
      }
    }
    return selected;
  }

  @Nullable
  private CreateShopRequestResolver findResolverFromAssignments(IStandardRequestManager manager) {
    var assignments = manager.getRequestResolverRequestAssignmentDataStore().getAssignments();
    if (assignments == null || assignments.isEmpty()) {
      return null;
    }
    for (IToken<?> resolverToken : assignments.keySet()) {
      try {
        IRequestResolver<?> resolver = manager.getResolverHandler().getResolver(resolverToken);
        if (resolver instanceof CreateShopRequestResolver shop && isLocalShopResolver(shop)) {
          return shop;
        }
      } catch (IllegalArgumentException ignored) {
        // Ignore stale tokens; health-check and reassignment paths handle cleanup.
      }
    }
    return null;
  }

  @Nullable
  private CreateShopRequestResolver findResolverFromRequestOwnership(
      IStandardRequestManager manager) {
    if (manager == null || manager.getRequestHandler() == null) {
      return null;
    }
    var assignments = manager.getRequestResolverRequestAssignmentDataStore().getAssignments();
    if (assignments == null || assignments.isEmpty()) {
      return null;
    }
    for (java.util.Collection<IToken<?>> requestTokens : assignments.values()) {
      if (requestTokens == null || requestTokens.isEmpty()) {
        continue;
      }
      for (IToken<?> requestToken : requestTokens) {
        try {
          var request = manager.getRequestHandler().getRequest(requestToken);
          if (request == null) {
            continue;
          }
          IRequestResolver<?> owner = manager.getResolverHandler().getResolverForRequest(request);
          if (owner instanceof CreateShopRequestResolver shop && isLocalShopResolver(shop)) {
            return shop;
          }
        } catch (Exception ignored) {
          // Ignore stale request/resolver links.
        }
      }
    }
    return null;
  }

  @Nullable
  private CreateShopRequestResolver resolveLiveShopResolver(IStandardRequestManager manager) {
    if (manager == null) {
      return null;
    }
    var providerResolvers = manager.getProviderHandler().getRegisteredResolvers(this);
    if (providerResolvers != null && !providerResolvers.isEmpty()) {
      for (IToken<?> token : providerResolvers) {
        CreateShopRequestResolver local = resolveLocalShopResolver(manager, token);
        if (local != null) {
          return local;
        }
      }
    }
    CreateShopRequestResolver byAssignments = findResolverFromAssignments(manager);
    if (byAssignments != null) {
      return byAssignments;
    }
    return findResolverFromRequestOwnership(manager);
  }

  private boolean hasAnyLocalProviderResolver(IStandardRequestManager manager) {
    if (manager == null) {
      return false;
    }
    var providerResolvers = manager.getProviderHandler().getRegisteredResolvers(this);
    if (providerResolvers == null || providerResolvers.isEmpty()) {
      return false;
    }
    for (IToken<?> token : providerResolvers) {
      if (resolveLocalShopResolver(manager, token) != null) {
        return true;
      }
    }
    return false;
  }

  private boolean hasAnyLocalDeliverableResolver(
      IStandardRequestManager manager, java.util.Collection<IToken<?>> deliverableAssignments) {
    if (manager == null || deliverableAssignments == null || deliverableAssignments.isEmpty()) {
      return false;
    }
    for (IToken<?> token : deliverableAssignments) {
      if (resolveLocalShopResolver(manager, token) != null) {
        return true;
      }
    }
    return false;
  }

  @Nullable
  private CreateShopRequestResolver resolveLocalShopResolver(
      IStandardRequestManager manager, IToken<?> token) {
    if (manager == null || token == null) {
      return null;
    }
    try {
      IRequestResolver<?> resolver = manager.getResolverHandler().getResolver(token);
      if (resolver instanceof CreateShopRequestResolver shop && isLocalShopResolver(shop)) {
        return shop;
      }
    } catch (Exception ignored) {
      // Ignore stale token links.
    }
    return null;
  }

  private boolean hasAssignedRequestsForResolver(
      IStandardRequestManager manager, IToken<?> resolverToken) {
    if (manager == null || resolverToken == null) {
      return false;
    }
    var assignments = manager.getRequestResolverRequestAssignmentDataStore().getAssignments();
    if (assignments == null || assignments.isEmpty()) {
      return false;
    }
    var resolverAssignments = assignments.get(resolverToken);
    return resolverAssignments != null && !resolverAssignments.isEmpty();
  }

  private boolean isLocalShopResolver(CreateShopRequestResolver resolver) {
    if (resolver == null || resolver.getLocation() == null || getLocation() == null) {
      return false;
    }
    return resolver.getLocation().getDimension().equals(getLocation().getDimension())
        && resolver
            .getLocation()
            .getInDimensionLocation()
            .equals(getLocation().getInDimensionLocation());
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
      if (ItemStack.isSameItemSameComponents(stack, key)) {
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
    return tag;
  }
}
