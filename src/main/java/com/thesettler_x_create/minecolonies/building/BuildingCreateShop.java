package com.thesettler_x_create.minecolonies.building;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.ldtteam.structurize.api.RotationMirror;
import com.ldtteam.structurize.blueprints.v1.Blueprint;
import com.ldtteam.structurize.blueprints.v1.BlueprintUtil;
import com.ldtteam.structurize.util.BlockInfo;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.modules.IBuildingModule;
import com.minecolonies.api.colony.buildings.modules.IBuildingModuleView;
import com.minecolonies.api.colony.buildings.workerbuildings.IWareHouse;
import com.minecolonies.api.colony.requestsystem.factory.IFactoryController;
import com.minecolonies.api.colony.requestsystem.location.ILocation;
import com.minecolonies.api.colony.requestsystem.requester.IRequester;
import com.minecolonies.api.colony.requestsystem.resolver.IRequestResolver;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.api.tileentities.AbstractTileEntityWareHouse;
import com.minecolonies.api.util.MessageUtils;
import com.minecolonies.api.util.constant.TypeConstants;
import com.minecolonies.core.colony.buildings.AbstractBuilding;
import com.minecolonies.core.colony.buildings.modules.BuildingModules;
import com.minecolonies.core.colony.buildings.modules.CourierAssignmentModule;
import com.minecolonies.core.colony.jobs.JobBuilder;
import com.minecolonies.core.colony.requestsystem.resolvers.DeliveryRequestResolver;
import com.minecolonies.core.colony.requestsystem.resolvers.PickupRequestResolver;
import com.thesettler_x_create.Config;
import com.thesettler_x_create.TheSettlerXCreate;
import com.thesettler_x_create.block.CreateShopBlock;
import com.thesettler_x_create.block.CreateShopOutputBlock;
import com.thesettler_x_create.blockentity.CreateShopBlockEntity;
import com.thesettler_x_create.blockentity.CreateShopOutputBlockEntity;
import com.thesettler_x_create.init.ModBlocks;
import com.thesettler_x_create.minecolonies.module.CreateShopCourierModule;
import com.thesettler_x_create.minecolonies.requestsystem.resolver.CreateShopRequestResolver;
import com.thesettler_x_create.minecolonies.tileentity.TileEntityCreateShop;
import java.util.ArrayList;
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
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Tuple;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** Create Shop building integration with MineColonies request system and Create network. */
public class BuildingCreateShop extends AbstractBuilding implements IWareHouse {
  public static final String SCHEMATIC_NAME = "createshop";
  private static final ResourceLocation BELT_ITEM_ID =
      ResourceLocation.fromNamespaceAndPath("create", "belt_connector");
  private static final Set<ResourceLocation> BELT_ANCHOR_BLOCK_IDS =
      Set.of(
          ResourceLocation.fromNamespaceAndPath("structurize", "blocksubstitution"),
          ResourceLocation.fromNamespaceAndPath("structurize", "blocksolidsubstitution"),
          ResourceLocation.fromNamespaceAndPath("structurize", "blocktagsubstitution"),
          ResourceLocation.fromNamespaceAndPath("structurize", "blockfluidsubstitution"));
  private static final ResourceLocation SHAFT_BLOCK_ID =
      ResourceLocation.fromNamespaceAndPath("create", "shaft");
  private static final ResourceLocation ENCASED_SHAFT_BLOCK_ID =
      ResourceLocation.fromNamespaceAndPath("create", "andesite_encased_shaft");

  static boolean isDebugRequests() {
    return Config.DEBUG_LOGGING.getAsBoolean();
  }

  private static final String TAG_PICKUP_POS = "PickupPos";
  private static final String TAG_OUTPUT_POS = "OutputPos";
  static final String TAG_PERMA_ORES = "PermaOres";
  static final String TAG_PERMA_WAIT_FULL = "PermaWaitFullStack";
  private static final String TAG_BUILDER_HUT_POS = "BuilderHutPos";

  private long lastMissingNetworkWarning;
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
  private final ShopWarehouseRegistrar warehouseRegistrar;
  private final ShopCourierDiagnostics courierDiagnostics;
  private final ShopPermaRequestManager permaManager;

  public BuildingCreateShop(IColony colony, BlockPos location) {
    super(colony, location);
    this.lastMissingNetworkWarning = 0L;
    this.warehouseRegistered = false;
    this.shopResolver = null;
    this.builderHutPos = null;
    this.inflightTracker = new ShopInflightTracker(this);
    this.rackIndex = new ShopRackIndex(this);
    this.beltManager = new ShopBeltManager(this);
    this.warehouseRegistrar = new ShopWarehouseRegistrar(this);
    this.courierDiagnostics = new ShopCourierDiagnostics(this);
    this.permaManager = new ShopPermaRequestManager(this);
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
    CourierAssignmentModule module = getFirstModuleOccurance(CourierAssignmentModule.class);
    boolean result = module != null && module.hasAssignedCitizen(citizen);
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
    Item beltItem = BuiltInRegistries.ITEM.get(BELT_ITEM_ID);
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
    ensureDeliverableAssignment();
    com.thesettler_x_create.minecolonies.requestsystem.CreateShopResolverInjector
        .ensureGlobalResolver(getColony());
    ensurePickupLink();
    beltManager.onPlacement();
  }

  @Override
  public void onUpgradeComplete(int newLevel) {
    super.onUpgradeComplete(newLevel);
    ensureWarehouseRegistration();
    ensureDeliverableAssignment();
    com.thesettler_x_create.minecolonies.requestsystem.CreateShopResolverInjector
        .ensureGlobalResolver(getColony());
    ensurePickupLink();
    beltManager.onUpgrade();
  }

  @Override
  public void onColonyTick(IColony colony) {
    super.onColonyTick(colony);
    ensureWarehouseRegistration();
    ensureDeliverableAssignment();
    com.thesettler_x_create.minecolonies.requestsystem.CreateShopResolverInjector
        .ensureGlobalResolver(colony);
    ensurePickupLink();
    beltManager.tick();
    permaManager.tickPermaRequests(colony);
    if (colony != null) {
      CreateShopRequestResolver resolver = getOrCreateShopResolver();
      if (isDebugRequests() && resolver == null) {
        com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
            "[CreateShop] tick: resolver missing for shop {}",
            getLocation().getInDimensionLocation());
      }
      if (resolver != null) {
        resolver.tickPendingDeliveries(colony.getRequestManager());
      }
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
    ImmutableList.Builder<IRequestResolver<?>> builder = ImmutableList.builder();
    for (IRequestResolver<?> resolver : super.createResolvers()) {
      if (resolver
          instanceof
          com.minecolonies.core.colony.requestsystem.resolvers.core
              .AbstractWarehouseRequestResolver) {
        // CreateShop is not a BuildingWareHouse; avoid MineColonies' warehouse resolver cast crash.
        continue;
      }
      builder.add(resolver);
    }

    ILocation location = getRequester().getLocation();
    IFactoryController factory = getColony().getRequestManager().getFactoryController();
    IToken<?> token = factory.getNewInstance(TypeConstants.ITOKEN);

    shopResolver = new CreateShopRequestResolver(location, token);
    builder.add(shopResolver);
    deliveryResolverToken = factory.getNewInstance(TypeConstants.ITOKEN);
    pickupResolverToken = factory.getNewInstance(TypeConstants.ITOKEN);
    builder.add(new DeliveryRequestResolver(location, deliveryResolverToken));
    builder.add(new PickupRequestResolver(location, pickupResolverToken));

    if (isDebugRequests()) {
      com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
          "[CreateShop] createResolvers at {} -> {}",
          getLocation().getInDimensionLocation(),
          builder.build().size());
      com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
          "[CreateShop] delivery resolver token={} pickup resolver token={}",
          deliveryResolverToken,
          pickupResolverToken);
    }

    return builder.build();
  }

  public CreateShopRequestResolver getShopResolver() {
    return shopResolver;
  }

  @Override
  @SuppressWarnings("unchecked")
  public <M extends IBuildingModule, V extends IBuildingModuleView> M getModule(
      com.minecolonies.api.colony.buildings.registry.BuildingEntry.ModuleProducer<M, V> producer) {
    M module = super.getModule(producer);
    if (module != null) {
      return module;
    }
    if (producer == BuildingModules.WAREHOUSE_COURIERS) {
      var modules = getModulesByType(CreateShopCourierModule.class);
      if (!modules.isEmpty()) {
        return (M) modules.get(0);
      }
    }
    return null;
  }

  @Nullable
  public CreateShopRequestResolver getOrCreateShopResolver() {
    if (shopResolver == null) {
      getResolvers();
    }
    return shopResolver;
  }

  public boolean hasActiveWorker() {
    return getAllAssignedCitizen().stream()
        .anyMatch(citizen -> citizen.getJob() instanceof JobCreateShop);
  }

  public boolean isWorkerWorking() {
    for (var citizen : getAllAssignedCitizen()) {
      if (citizen == null || !(citizen.getJob() instanceof JobCreateShop)) {
        continue;
      }
      if (citizen.getJobStatus() == com.minecolonies.api.entity.ai.JobStatus.WORKING) {
        return true;
      }
      try {
        var entity = citizen.getEntity();
        if (entity != null && entity.isPresent()) {
          String meta = entity.get().getRenderMetadata();
          if ("working".equals(meta)) {
            return true;
          }
        }
      } catch (Exception ignored) {
        // Ignore entity lookup issues.
      }
    }
    return false;
  }

  public void notifyMissingNetwork() {
    if (!Config.CHAT_MESSAGES_ENABLED.getAsBoolean()) {
      return;
    }
    TileEntityCreateShop shop = getCreateShopTileEntity();
    if (shop == null || shop.getLevel() == null) {
      return;
    }
    long gameTime = shop.getLevel().getGameTime();
    if (gameTime - lastMissingNetworkWarning
        <= Config.MISSING_NETWORK_WARNING_COOLDOWN.getAsLong()) {
      return;
    }
    lastMissingNetworkWarning = gameTime;
    MessageUtils.format("com.thesettler_x_create.message.createshop.no_network")
        .sendTo(getColony())
        .forAllPlayers();
  }

  private void ensureWarehouseRegistration() {
    warehouseRegistrar.ensureWarehouseRegistration();
  }

  public void ensureRackContainers() {
    rackIndex.ensureRackContainers();
  }

  /** Returns rack inventory counts for the given stack keys. */
  public java.util.Map<ItemStack, Integer> getStockCountsForKeys(List<ItemStack> keys) {
    return rackIndex.getStockCountsForKeys(keys);
  }

  private void ensureDeliverableAssignment() {
    if (getColony() == null) {
      return;
    }
    if (shopResolver == null) {
      getResolvers();
    }
    if (shopResolver == null) {
      return;
    }
    if (!(getColony().getRequestManager()
        instanceof
        com.minecolonies.core.colony.requestsystem.management.IStandardRequestManager
        manager)) {
      return;
    }
    var resolverHandler = manager.getResolverHandler();
    boolean registered = false;
    try {
      resolverHandler.getResolver(shopResolver.getId());
      registered = true;
    } catch (IllegalArgumentException ignored) {
      // Not registered yet.
    }
    if (!registered) {
      try {
        resolverHandler.registerResolver(shopResolver);
        registered = true;
        if (isDebugRequests()) {
          com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
              "[CreateShop] registered resolver {}", shopResolver.getId());
        }
      } catch (Exception ex) {
        if (isDebugRequests()) {
          com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
              "[CreateShop] resolver registration failed: {}", ex.getMessage());
        }
      }
    }
    if (registered) {
      var store = manager.getRequestableTypeRequestResolverAssignmentDataStore();
      var assignments = store.getAssignments();
      var deliverableList =
          assignments.computeIfAbsent(
              TypeConstants.DELIVERABLE, key -> new java.util.ArrayList<>());
      var requestableList =
          assignments.computeIfAbsent(
              TypeConstants.REQUESTABLE, key -> new java.util.ArrayList<>());
      var toolList =
          assignments.computeIfAbsent(TypeConstants.TOOL, key -> new java.util.ArrayList<>());
      if (!deliverableList.contains(shopResolver.getId())) {
        deliverableList.add(shopResolver.getId());
        if (isDebugRequests()) {
          com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
              "[CreateShop] added resolver {} to DELIVERABLE assignment list",
              shopResolver.getId());
        }
      }
      if (!requestableList.contains(shopResolver.getId())) {
        requestableList.add(shopResolver.getId());
        if (isDebugRequests()) {
          com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
              "[CreateShop] added resolver {} to REQUESTABLE assignment list",
              shopResolver.getId());
        }
      }
      if (!toolList.contains(shopResolver.getId())) {
        toolList.add(shopResolver.getId());
        if (isDebugRequests()) {
          com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
              "[CreateShop] added resolver {} to TOOL assignment list", shopResolver.getId());
        }
      }
    }
  }

  public void ensurePickupLink() {
    Level level = getColony() == null ? null : getColony().getWorld();
    if (level == null) {
      return;
    }
    if (pickupPos != null) {
      BlockEntity existing = level.getBlockEntity(pickupPos);
      if (existing instanceof CreateShopBlockEntity shopBlock) {
        if (shopBlock.getShopPos() == null) {
          shopBlock.setShopPos(getLocation().getInDimensionLocation());
        }
        return;
      }
      pickupPos = null;
    }
    BlockPos hutPos = getLocation().getInDimensionLocation();
    // First, try to discover an existing pickup block nearby.
    int radius = 2;
    for (int dx = -radius; dx <= radius; dx++) {
      for (int dy = 0; dy <= 2; dy++) {
        for (int dz = -radius; dz <= radius; dz++) {
          BlockPos candidate = hutPos.offset(dx, dy, dz);
          BlockEntity entity = level.getBlockEntity(candidate);
          if (entity instanceof CreateShopBlockEntity shopBlock) {
            pickupPos = candidate;
            if (shopBlock.getShopPos() == null) {
              shopBlock.setShopPos(hutPos);
            }
            return;
          }
        }
      }
    }
    BlockPos above = hutPos.above();
    if (level.isEmptyBlock(above)) {
      level.setBlockAndUpdate(above, ModBlocks.CREATE_SHOP_PICKUP.get().defaultBlockState());
    }
    if (level.getBlockState(above).is(ModBlocks.CREATE_SHOP_PICKUP.get())) {
      pickupPos = above;
    }
    if (pickupPos == null) {
      return;
    }
    BlockEntity entity = level.getBlockEntity(pickupPos);
    if (entity instanceof CreateShopBlockEntity shopBlock) {
      if (shopBlock.getShopPos() == null) {
        shopBlock.setShopPos(getLocation().getInDimensionLocation());
      }
    }
  }

  public void setPermaWaitFullStack(boolean enabled) {
    permaManager.setPermaWaitFullStack(enabled);
  }

  public void setPermaOre(ResourceLocation itemId, boolean enabled) {
    permaManager.setPermaOre(itemId, enabled);
  }

  private boolean trySpawnBeltBlueprint(IColony colony) {
    int targetLevel = Math.max(1, getBuildingLevel());
    logBelt(
        "trySpawn start: targetLevel={} built={} level={} loc={}",
        targetLevel,
        isBuilt(),
        getBuildingLevel(),
        getLocation().getInDimensionLocation());
    if (colony == null || !isBuilt()) {
      logBelt(
          "trySpawn early-exit: targetLevel={} colonyNull={} built={}",
          targetLevel,
          colony == null,
          isBuilt());
      return false;
    }
    Level level = colony.getWorld();
    if (!(level instanceof ServerLevel serverLevel)) {
      logBelt(
          "trySpawn exit: not server level: {}",
          level == null ? "<null>" : level.getClass().getName());
      return false;
    }
    for (int beltLevel = 1; beltLevel <= targetLevel; beltLevel++) {
      if (!trySpawnBeltBlueprintForLevel(colony, serverLevel, beltLevel)) {
        return false;
      }
      logBelt("trySpawn completed: beltPlacedLevel={}", beltLevel);
    }
    return true;
  }

  private boolean hasActiveWorkOrder(IColony colony) {
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

  private boolean trySpawnBeltBlueprintForLevel(
      IColony colony, ServerLevel serverLevel, int beltLevel) {
    Blueprint blueprint = loadBeltBlueprint(serverLevel, beltLevel);
    if (blueprint == null) {
      logBelt("trySpawn exit: belt blueprint missing level={}", beltLevel);
      return false;
    }
    Blueprint baseBlueprint = loadBaseShopBlueprint(serverLevel);
    BlockPos basePrimaryOffset =
        baseBlueprint == null ? null : baseBlueprint.getPrimaryBlockOffset();
    BlockPos beltPrimaryOffset = blueprint.getPrimaryBlockOffset();
    BlockPos beltAnchor = findBeltAnchor(blueprint);
    if (beltAnchor != null) {
      logBelt("belt anchor found at {} (level={})", beltAnchor, beltLevel);
      if (placeBeltBlueprintAtAnchor(serverLevel, blueprint, beltAnchor, beltPrimaryOffset)) {
        consumeBeltItem(colony);
        return true;
      }
    } else {
      logBelt(
          "belt anchor missing (substitution blocks) level={} knownBlocks={}",
          beltLevel,
          dumpBlueprintBlocks(blueprint, 12));
    }
    if (placeBeltBlueprintAtBuilding(
        serverLevel, blueprint, basePrimaryOffset, beltPrimaryOffset)) {
      consumeBeltItem(colony);
      return true;
    }
    List<BlockPos> blueprintAnchors = findAnchorShafts(blueprint);
    if (blueprintAnchors.size() < 2 && baseBlueprint != null) {
      List<BlockPos> baseAnchors = findAnchorShafts(baseBlueprint);
      if (baseAnchors.size() >= 2) {
        logBelt("using anchors from base blueprint");
        blueprintAnchors = baseAnchors;
      }
    }
    if (blueprintAnchors.size() < 2) {
      logBelt(
          "trySpawn exit: blueprint anchors <2 count={} level={}",
          blueprintAnchors.size(),
          beltLevel);
      return false;
    }
    List<BlockPos> worldAnchors =
        findWorldAnchorShafts(serverLevel, getLocation().getInDimensionLocation(), 10);
    if (worldAnchors.size() < 2) {
      logBelt("trySpawn exit: world anchors <2 count={} level={}", worldAnchors.size(), beltLevel);
      return false;
    }
    BeltTransform transform = findTransform(blueprintAnchors, worldAnchors, BlockPos.ZERO);
    if (transform == null) {
      logBelt("trySpawn exit: transform not found level={}", beltLevel);
      return false;
    }
    placeBeltBlueprint(serverLevel, blueprint, transform);
    boolean consumed = consumeBeltItem(colony);
    logBelt("trySpawn placed belt blocks level={} consumedItem={}", beltLevel, consumed);
    return true;
  }

  @Nullable
  private Blueprint loadBeltBlueprint(ServerLevel level, int beltLevel) {
    ResourceLocation primary = getBeltBlueprintPath(beltLevel);
    ResourceLocation fallback = ShopBeltManager.beltBlueprintL1();
    logBelt("load blueprint: primary={} fallback={}", primary, fallback);
    Blueprint blueprint = loadBeltBlueprintFromPath(level, primary);
    if (blueprint != null) {
      return blueprint;
    }
    if (!primary.equals(fallback)) {
      return loadBeltBlueprintFromPath(level, fallback);
    }
    return null;
  }

  @Nullable
  private Blueprint loadBeltBlueprintFromPath(ServerLevel level, ResourceLocation path) {
    if (path == null) {
      return null;
    }
    try {
      var resourceManager = level.getServer().getResourceManager();
      var resourceOpt = resourceManager.getResource(path);
      if (resourceOpt.isEmpty()) {
        logBelt("load blueprint missing resource: {}", path);
        return null;
      }
      try (var input = resourceOpt.get().open()) {
        CompoundTag tag = NbtIo.readCompressed(input, NbtAccounter.unlimitedHeap());
        Blueprint blueprint = BlueprintUtil.readBlueprintFromNBT(tag, level.registryAccess());
        logBelt(
            "load blueprint ok: {} blocks={}",
            path,
            blueprint == null ? 0 : blueprint.getBlockInfoAsList().size());
        return blueprint;
      }
    } catch (Exception ex) {
      if (isDebugRequests()) {
        TheSettlerXCreate.LOGGER.info(
            "[CreateShop] belt blueprint load failed: {}", ex.getMessage());
      }
      return null;
    }
  }

  @Nullable
  private Blueprint loadBaseShopBlueprint(ServerLevel level) {
    String path = getBaseShopBlueprintPath();
    if (path == null) {
      return null;
    }
    try (var input = TheSettlerXCreate.class.getClassLoader().getResourceAsStream(path)) {
      if (input == null) {
        logBelt("base blueprint missing resource: {}", path);
        return null;
      }
      CompoundTag tag = NbtIo.readCompressed(input, NbtAccounter.unlimitedHeap());
      Blueprint blueprint = BlueprintUtil.readBlueprintFromNBT(tag, level.registryAccess());
      logBelt(
          "base blueprint ok: {} blocks={}",
          path,
          blueprint == null ? 0 : blueprint.getBlockInfoAsList().size());
      return blueprint;
    } catch (Exception ex) {
      logBelt("base blueprint load failed: {}", ex.getMessage());
      return null;
    }
  }

  @Nullable
  private String getBaseShopBlueprintPath() {
    int level = Math.max(1, getBuildingLevel());
    if (level >= 2) {
      return "blueprints/"
          + TheSettlerXCreate.MODID
          + "/craftsmanship/storage/createshop2.blueprint";
    }
    return "blueprints/" + TheSettlerXCreate.MODID + "/craftsmanship/storage/createshop1.blueprint";
  }

  @Nullable
  private ResourceLocation getBeltBlueprintPath() {
    int level = Math.max(1, getBuildingLevel());
    return getBeltBlueprintPath(level);
  }

  @Nullable
  private ResourceLocation getBeltBlueprintPath(int beltLevel) {
    int level = Math.max(1, beltLevel);
    if (level >= 2) {
      return ShopBeltManager.beltBlueprintL2();
    }
    return ShopBeltManager.beltBlueprintL1();
  }

  private List<BlockPos> findAnchorShafts(Blueprint blueprint) {
    List<BlockPos> anchors = new ArrayList<>();
    for (BlockInfo info : blueprint.getBlockInfoAsList()) {
      if (info == null) {
        continue;
      }
      if (isAnchorShaft(info.getState())) {
        anchors.add(info.getPos());
        if (anchors.size() >= 2) {
          break;
        }
      }
    }
    logBelt("blueprint anchors found count={} anchors={}", anchors.size(), anchors);
    return anchors;
  }

  private List<BlockPos> findWorldAnchorShafts(ServerLevel level, BlockPos center, int radius) {
    List<BlockPos> anchors = new ArrayList<>();
    for (int dx = -radius; dx <= radius; dx++) {
      for (int dy = -3; dy <= 5; dy++) {
        for (int dz = -radius; dz <= radius; dz++) {
          BlockPos pos = center.offset(dx, dy, dz);
          BlockState state = level.getBlockState(pos);
          if (isAnchorShaft(state)) {
            anchors.add(pos);
          }
        }
      }
    }
    logBelt("world anchors found count={} center={} radius={}", anchors.size(), center, radius);
    return anchors;
  }

  private boolean isAnchorShaft(BlockState state) {
    if (state == null) {
      return false;
    }
    ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
    if (id == null) {
      return false;
    }
    return SHAFT_BLOCK_ID.equals(id) || ENCASED_SHAFT_BLOCK_ID.equals(id);
  }

  @Nullable
  private BeltTransform findTransform(
      List<BlockPos> blueprintAnchors, List<BlockPos> worldAnchors, BlockPos blueprintOffset) {
    if (blueprintAnchors.size() < 2 || worldAnchors.size() < 2) {
      return null;
    }
    BlockPos a = blueprintAnchors.get(0);
    BlockPos b = blueprintAnchors.get(1);
    List<Rotation> rotations =
        List.of(
            Rotation.NONE,
            Rotation.CLOCKWISE_90,
            Rotation.CLOCKWISE_180,
            Rotation.COUNTERCLOCKWISE_90);

    for (Rotation rotation : rotations) {
      BlockPos ra = rotatePos(a, rotation);
      BlockPos rb = rotatePos(b, rotation);
      for (int i = 0; i < worldAnchors.size(); i++) {
        for (int j = i + 1; j < worldAnchors.size(); j++) {
          BlockPos wa = worldAnchors.get(i);
          BlockPos wb = worldAnchors.get(j);
          if (vectorEquals(ra, rb, wa, wb)) {
            logBelt(
                "transform match rotation={} anchorA={} anchorB={} worldA={} worldB={}",
                rotation,
                ra,
                rb,
                wa,
                wb);
            return new BeltTransform(rotation, wa.subtract(ra), blueprintOffset);
          }
          if (vectorEquals(ra, rb, wb, wa)) {
            logBelt(
                "transform match rotation={} anchorA={} anchorB={} worldA={} worldB={}",
                rotation,
                ra,
                rb,
                wb,
                wa);
            return new BeltTransform(rotation, wb.subtract(ra), blueprintOffset);
          }
        }
      }
    }
    return null;
  }

  private boolean vectorEquals(BlockPos a, BlockPos b, BlockPos c, BlockPos d) {
    return (b.getX() - a.getX()) == (d.getX() - c.getX())
        && (b.getY() - a.getY()) == (d.getY() - c.getY())
        && (b.getZ() - a.getZ()) == (d.getZ() - c.getZ());
  }

  private BlockPos rotatePos(BlockPos pos, Rotation rotation) {
    int x = pos.getX();
    int y = pos.getY();
    int z = pos.getZ();
    return switch (rotation) {
      case CLOCKWISE_90 -> new BlockPos(-z, y, x);
      case CLOCKWISE_180 -> new BlockPos(-x, y, -z);
      case COUNTERCLOCKWISE_90 -> new BlockPos(z, y, -x);
      default -> pos;
    };
  }

  private boolean placeBeltBlueprintAtBuilding(
      ServerLevel level,
      Blueprint blueprint,
      BlockPos basePrimaryOffset,
      BlockPos beltPrimaryOffset) {
    if (level == null || blueprint == null) {
      return false;
    }
    RotationMirror rotationMirror = getRotationMirror();
    if (rotationMirror == null) {
      rotationMirror = RotationMirror.NONE;
    }
    BlockPos baseOffset = basePrimaryOffset == null ? BlockPos.ZERO : basePrimaryOffset;
    BlockPos beltOffset = beltPrimaryOffset == null ? BlockPos.ZERO : beltPrimaryOffset;
    BlockPos adjust = baseOffset.subtract(beltOffset);
    BlockPos buildingPos = getLocation().getInDimensionLocation();
    BlockPos translation = buildingPos.subtract(baseOffset);
    logBelt(
        "place by rotationMirror={} basePrimary={} beltPrimary={} adjust={} translation={}",
        rotationMirror,
        baseOffset,
        beltOffset,
        adjust,
        translation);
    int placedCount = 0;
    int logged = 0;
    BlockPos min = null;
    BlockPos max = null;
    for (BlockInfo info : blueprint.getBlockInfoAsList()) {
      if (info == null) {
        continue;
      }
      BlockState state = info.getState();
      if (state == null || state.isAir() || isBeltAnchor(state)) {
        continue;
      }
      BlockPos local = info.getPos().offset(adjust);
      BlockPos rotated = rotationMirror.applyToPos(local, baseOffset);
      BlockPos worldPos = rotated.offset(translation);
      BlockState placed = rotationMirror.applyToBlockState(state, level, worldPos);
      level.setBlockAndUpdate(worldPos, placed);
      applyTileEntityData(level, worldPos, info);
      if (min == null) {
        min = worldPos;
        max = worldPos;
      } else {
        min =
            new BlockPos(
                Math.min(min.getX(), worldPos.getX()),
                Math.min(min.getY(), worldPos.getY()),
                Math.min(min.getZ(), worldPos.getZ()));
        max =
            new BlockPos(
                Math.max(max.getX(), worldPos.getX()),
                Math.max(max.getY(), worldPos.getY()),
                Math.max(max.getZ(), worldPos.getZ()));
      }
      if (logged < 10) {
        BlockState actual = level.getBlockState(worldPos);
        logBelt(
            "place block pos={} expected={} actual={}",
            worldPos,
            getBlockId(placed),
            getBlockId(actual));
        logged++;
      }
      placedCount++;
    }
    logBelt(
        "placed belt blocks (rotationMirror) count={} boundsMin={} boundsMax={}",
        placedCount,
        min,
        max);
    return placedCount > 0;
  }

  private boolean placeBeltBlueprintAtAnchor(
      ServerLevel level, Blueprint blueprint, BlockPos beltAnchor, BlockPos beltPrimaryOffset) {
    if (level == null || blueprint == null || beltAnchor == null) {
      return false;
    }
    RotationMirror rotationMirror = getRotationMirror();
    if (rotationMirror == null) {
      rotationMirror = RotationMirror.NONE;
    }
    BlockPos beltOffset = beltPrimaryOffset == null ? BlockPos.ZERO : beltPrimaryOffset;
    BlockPos hutPos = getPosition();
    if (hutPos == null) {
      hutPos = getLocation().getInDimensionLocation();
    }
    BlockPos targetAnchor = hutPos.below();
    BlockPos rotatedAnchor = rotationMirror.applyToPos(beltAnchor, beltOffset);
    BlockPos translation = targetAnchor.subtract(rotatedAnchor);
    logBelt(
        "place by belt anchor rotationMirror={} beltPrimary={} beltAnchor={} targetAnchor={} translation={}",
        rotationMirror,
        beltOffset,
        beltAnchor,
        targetAnchor,
        translation);
    int placedCount = 0;
    int logged = 0;
    BlockPos min = null;
    BlockPos max = null;
    for (BlockInfo info : blueprint.getBlockInfoAsList()) {
      if (info == null) {
        continue;
      }
      BlockState state = info.getState();
      if (state == null || state.isAir() || isBeltAnchor(state)) {
        continue;
      }
      BlockPos local = info.getPos();
      BlockPos rotated = rotationMirror.applyToPos(local, beltOffset);
      BlockPos worldPos = rotated.offset(translation);
      BlockState placed = rotationMirror.applyToBlockState(state, level, worldPos);
      level.setBlockAndUpdate(worldPos, placed);
      applyTileEntityData(level, worldPos, info);
      if (min == null) {
        min = worldPos;
        max = worldPos;
      } else {
        min =
            new BlockPos(
                Math.min(min.getX(), worldPos.getX()),
                Math.min(min.getY(), worldPos.getY()),
                Math.min(min.getZ(), worldPos.getZ()));
        max =
            new BlockPos(
                Math.max(max.getX(), worldPos.getX()),
                Math.max(max.getY(), worldPos.getY()),
                Math.max(max.getZ(), worldPos.getZ()));
      }
      if (logged < 10) {
        BlockState actual = level.getBlockState(worldPos);
        logBelt(
            "place block pos={} expected={} actual={}",
            worldPos,
            getBlockId(placed),
            getBlockId(actual));
        logged++;
      }
      placedCount++;
    }
    logBelt(
        "placed belt blocks (anchor) count={} boundsMin={} boundsMax={}", placedCount, min, max);
    return placedCount > 0;
  }

  @Nullable
  private BlockPos findBeltAnchor(Blueprint blueprint) {
    if (blueprint == null) {
      return null;
    }
    for (BlockInfo info : blueprint.getBlockInfoAsList()) {
      if (info == null) {
        continue;
      }
      if (isBeltAnchor(info.getState())) {
        return info.getPos();
      }
    }
    return null;
  }

  private boolean isBeltAnchor(BlockState state) {
    if (state == null) {
      return false;
    }
    ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
    if (id == null) {
      return false;
    }
    return BELT_ANCHOR_BLOCK_IDS.contains(id);
  }

  private List<String> dumpBlueprintBlocks(Blueprint blueprint, int limit) {
    List<String> ids = new ArrayList<>();
    if (blueprint == null || limit <= 0) {
      return ids;
    }
    for (BlockInfo info : blueprint.getBlockInfoAsList()) {
      if (info == null || info.getState() == null) {
        continue;
      }
      ResourceLocation id = BuiltInRegistries.BLOCK.getKey(info.getState().getBlock());
      if (id == null) {
        continue;
      }
      String value = id.toString();
      if (!ids.contains(value)) {
        ids.add(value);
        if (ids.size() >= limit) {
          break;
        }
      }
    }
    return ids;
  }

  private void placeBeltBlueprint(ServerLevel level, Blueprint blueprint, BeltTransform transform) {
    int placedCount = 0;
    int logged = 0;
    BlockPos min = null;
    BlockPos max = null;
    for (BlockInfo info : blueprint.getBlockInfoAsList()) {
      if (info == null) {
        continue;
      }
      BlockState state = info.getState();
      if (state == null || state.isAir() || isBeltAnchor(state)) {
        continue;
      }
      BlockPos local = info.getPos();
      if (transform.blueprintOffset != null && !transform.blueprintOffset.equals(BlockPos.ZERO)) {
        local = local.offset(transform.blueprintOffset);
      }
      BlockPos rotated = rotatePos(local, transform.rotation);
      BlockPos worldPos = rotated.offset(transform.translation);
      BlockState placed = state.rotate(transform.rotation);
      level.setBlockAndUpdate(worldPos, placed);
      applyTileEntityData(level, worldPos, info);
      if (min == null) {
        min = worldPos;
        max = worldPos;
      } else {
        min =
            new BlockPos(
                Math.min(min.getX(), worldPos.getX()),
                Math.min(min.getY(), worldPos.getY()),
                Math.min(min.getZ(), worldPos.getZ()));
        max =
            new BlockPos(
                Math.max(max.getX(), worldPos.getX()),
                Math.max(max.getY(), worldPos.getY()),
                Math.max(max.getZ(), worldPos.getZ()));
      }
      if (logged < 10) {
        BlockState actual = level.getBlockState(worldPos);
        logBelt(
            "place block pos={} expected={} actual={}",
            worldPos,
            getBlockId(placed),
            getBlockId(actual));
        logged++;
      }
      placedCount++;
    }
    logBelt(
        "placed belt blocks count={} rotation={} translation={} offset={} boundsMin={} boundsMax={}",
        placedCount,
        transform.rotation,
        transform.translation,
        transform.blueprintOffset,
        min,
        max);
  }

  private void applyTileEntityData(ServerLevel level, BlockPos worldPos, BlockInfo info) {
    if (info == null || !info.hasTileEntityData()) {
      return;
    }
    BlockEntity entity = level.getBlockEntity(worldPos);
    if (entity == null) {
      logBelt("tile entity missing at {}", worldPos);
      return;
    }
    CompoundTag tag = info.getTileEntityData();
    if (tag == null) {
      return;
    }
    CompoundTag copy = tag.copy();
    copy.putInt("x", worldPos.getX());
    copy.putInt("y", worldPos.getY());
    copy.putInt("z", worldPos.getZ());
    if (tryLoadWithComponents(entity, copy, level, worldPos)) {
      return;
    }
    try {
      var method = entity.getClass().getMethod("load", CompoundTag.class);
      method.invoke(entity, copy);
      entity.setChanged();
    } catch (Exception ex) {
      logBelt(
          "tile entity load failed at {} type={} err={}",
          worldPos,
          entity.getClass().getName(),
          ex.getMessage());
    }
  }

  private boolean tryLoadWithComponents(
      BlockEntity entity, CompoundTag copy, ServerLevel level, BlockPos worldPos) {
    try {
      var method =
          entity
              .getClass()
              .getMethod(
                  "loadWithComponents",
                  CompoundTag.class,
                  net.minecraft.core.HolderLookup.Provider.class);
      method.invoke(entity, copy, level.registryAccess());
      entity.setChanged();
      return true;
    } catch (NoSuchMethodException ignored) {
      return false;
    } catch (Exception ex) {
      logBelt(
          "tile entity loadWithComponents failed at {} type={} err={}",
          worldPos,
          entity.getClass().getName(),
          ex.getMessage());
      return false;
    }
  }

  private String getBlockId(BlockState state) {
    if (state == null) {
      return "<null>";
    }
    ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
    return id == null ? "<unknown>" : id.toString();
  }

  private boolean consumeBeltItem(IColony colony) {
    if (colony == null) {
      logBelt("consume belt item skipped: colony null");
      return false;
    }
    Item beltItem = BuiltInRegistries.ITEM.get(BELT_ITEM_ID);
    if (beltItem == null || beltItem == net.minecraft.world.item.Items.AIR) {
      logBelt("consume belt item skipped: belt item missing id={}", BELT_ITEM_ID);
      return false;
    }
    return consumeFromBuilderHut(colony, beltItem);
  }

  private boolean consumeFromBuilderHut(IColony colony, Item beltItem) {
    updateBuilderHutPos(colony);
    var manager = colony.getCitizenManager();
    if (manager == null || builderHutPos == null) {
      logBelt(
          "consume belt item skipped: builder hut missing managerNull={} hutPos={}",
          manager == null,
          builderHutPos);
      return false;
    }
    var buildingManager = colony.getServerBuildingManager();
    if (buildingManager == null) {
      logBelt("consume belt item skipped: building manager missing");
      return false;
    }
    var builderBuilding = buildingManager.getBuilding(builderHutPos);
    if (builderBuilding == null) {
      logBelt("consume belt item skipped: builder building missing at {}", builderHutPos);
      return false;
    }
    for (var citizen : builderBuilding.getAllAssignedCitizen()) {
      if (citizen == null || !(citizen.getJob() instanceof JobBuilder)) {
        continue;
      }
      var inventory = citizen.getInventory();
      if (inventory == null) {
        continue;
      }
      if (removeOneFromHandler(inventory, beltItem)) {
        logBelt("consume belt item ok: citizen={}", citizen.getName());
        return true;
      }
    }
    logBelt(
        "consume belt item failed: no belt item found in builder hut inventory {}", builderHutPos);
    return false;
  }

  private void updateBuilderHutPos(IColony colony) {
    if (colony == null) {
      return;
    }
    var workManager = colony.getWorkManager();
    if (workManager == null) {
      return;
    }
    var workOrders =
        workManager.getWorkOrdersOfType(
            com.minecolonies.core.colony.workorders.WorkOrderBuilding.class);
    if (workOrders == null || workOrders.isEmpty()) {
      logBelt("update builder hut: no work orders");
      return;
    }
    BlockPos location = getLocation().getInDimensionLocation();
    for (var order : workOrders) {
      if (order == null || order.getLocation() == null) {
        continue;
      }
      if (!order.getLocation().equals(location)) {
        continue;
      }
      if (order.isClaimed() && order.getClaimedBy() != null) {
        builderHutPos = order.getClaimedBy();
        logBelt("update builder hut: claimed by {}", builderHutPos);
        return;
      }
    }
    logBelt("update builder hut: no claimed work order for {}", location);
  }

  private boolean removeOneFromHandler(
      net.neoforged.neoforge.items.IItemHandler handler, Item item) {
    for (int slot = 0; slot < handler.getSlots(); slot++) {
      ItemStack stack = handler.getStackInSlot(slot);
      if (stack.isEmpty() || stack.getItem() != item) {
        continue;
      }
      ItemStack extracted = handler.extractItem(slot, 1, false);
      if (!extracted.isEmpty()) {
        return true;
      }
    }
    return false;
  }

  private static final class BeltTransform {
    private final Rotation rotation;
    private final BlockPos translation;
    private final BlockPos blueprintOffset;

    private BeltTransform(Rotation rotation, BlockPos translation, BlockPos blueprintOffset) {
      this.rotation = rotation;
      this.translation = translation;
      this.blueprintOffset = blueprintOffset;
    }
  }

  private void logBelt(String message, Object... args) {
    if (!isDebugRequests()) {
      return;
    }
    TheSettlerXCreate.LOGGER.info("[CreateShop] belt: " + message, args);
  }

  private void clearPermaPending(
      com.minecolonies.api.colony.requestsystem.request.IRequest<?> request) {
    permaManager.clearPermaPending(request);
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
