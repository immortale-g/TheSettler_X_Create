package com.thesettler_x_create.minecolonies.building;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.ldtteam.structurize.api.RotationMirror;
import com.ldtteam.structurize.blueprints.v1.Blueprint;
import com.ldtteam.structurize.blueprints.v1.BlueprintUtil;
import com.ldtteam.structurize.util.BlockInfo;
import com.minecolonies.api.blocks.AbstractBlockMinecoloniesRack;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.modules.IBuildingModule;
import com.minecolonies.api.colony.buildings.modules.IBuildingModuleView;
import com.minecolonies.api.colony.buildings.workerbuildings.IWareHouse;
import com.minecolonies.api.colony.interactionhandling.ChatPriority;
import com.minecolonies.api.colony.requestsystem.factory.IFactoryController;
import com.minecolonies.api.colony.requestsystem.location.ILocation;
import com.minecolonies.api.colony.requestsystem.manager.IRequestManager;
import com.minecolonies.api.colony.requestsystem.requestable.Stack;
import com.minecolonies.api.colony.requestsystem.requester.IRequester;
import com.minecolonies.api.colony.requestsystem.resolver.IRequestResolver;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.api.tileentities.AbstractTileEntityWareHouse;
import com.minecolonies.api.util.MessageUtils;
import com.minecolonies.api.util.WorldUtil;
import com.minecolonies.api.util.constant.TypeConstants;
import com.minecolonies.core.colony.buildings.AbstractBuilding;
import com.minecolonies.core.colony.buildings.modules.BuildingModules;
import com.minecolonies.core.colony.buildings.modules.CourierAssignmentModule;
import com.minecolonies.core.colony.buildings.modules.WarehouseModule;
import com.minecolonies.core.colony.interactionhandling.SimpleNotificationInteraction;
import com.minecolonies.core.colony.jobs.JobBuilder;
import com.minecolonies.core.colony.requestsystem.resolvers.DeliveryRequestResolver;
import com.minecolonies.core.colony.requestsystem.resolvers.PickupRequestResolver;
import com.minecolonies.core.tileentities.TileEntityRack;
import com.thesettler_x_create.Config;
import com.thesettler_x_create.TheSettlerXCreate;
import com.thesettler_x_create.block.CreateShopBlock;
import com.thesettler_x_create.block.CreateShopOutputBlock;
import com.thesettler_x_create.blockentity.CreateShopBlockEntity;
import com.thesettler_x_create.blockentity.CreateShopOutputBlockEntity;
import com.thesettler_x_create.init.ModBlocks;
import com.thesettler_x_create.minecolonies.job.JobCreateShop;
import com.thesettler_x_create.minecolonies.module.CreateShopCourierModule;
import com.thesettler_x_create.minecolonies.requestsystem.resolver.CreateShopRequestResolver;
import com.thesettler_x_create.minecolonies.tileentity.TileEntityCreateShop;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
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
import net.minecraft.network.chat.Component;
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
  private static final ResourceLocation BELT_BLUEPRINT_L1 =
      ResourceLocation.fromNamespaceAndPath(
          TheSettlerXCreate.MODID, "blueprints_internal/createshop1_belt.blueprint");
  private static final ResourceLocation BELT_BLUEPRINT_L2 =
      ResourceLocation.fromNamespaceAndPath(
          TheSettlerXCreate.MODID, "blueprints_internal/createshop2_belt.blueprint");
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

  private static boolean isDebugRequests() {
    return Config.DEBUG_LOGGING.getAsBoolean();
  }

  private static final java.util.Set<String> REFLECTION_WARNED =
      java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());
  private static final String TAG_PICKUP_POS = "PickupPos";
  private static final String TAG_OUTPUT_POS = "OutputPos";
  private static final String TAG_PERMA_ORES = "PermaOres";
  private static final String TAG_PERMA_WAIT_FULL = "PermaWaitFullStack";
  private static final String TAG_BUILDER_HUT_POS = "BuilderHutPos";

  private long lastMissingNetworkWarning;
  private long lastCourierDebugTime;
  private long lastCourierEntityDebugTime;
  private String lastCourierDebugDump;
  private String lastCourierEntityDump;
  private String lastAssignedCitizensDump;
  private final java.util.Map<String, String> lastAssignedCitizenInfo = new java.util.HashMap<>();
  private final java.util.Map<String, String> lastModuleCitizenInfo = new java.util.HashMap<>();
  private final java.util.Map<Integer, Boolean> lastAccessResult = new java.util.HashMap<>();
  private final java.util.Map<String, Long> lastEntityRepairAttemptTime = new java.util.HashMap<>();
  private String lastModuleAssignDump;
  private String lastEntityRepairDump;
  private String lastWarehouseCompareDump;
  private final java.util.Map<String, String> lastRequesterError = new java.util.HashMap<>();
  private boolean warehouseRegistered;
  private long lastRackScanTick;
  private long lastInflightTick;
  private CreateShopRequestResolver shopResolver;
  private IToken<?> deliveryResolverToken;
  private IToken<?> pickupResolverToken;
  private BlockPos pickupPos;
  private BlockPos outputPos;
  private final Set<ResourceLocation> permaOres = new HashSet<>();
  private boolean permaWaitFullStack;
  private long lastPermaRequestTick;
  private final Map<IToken<?>, PendingPermaRequest> permaPendingRequests =
      new java.util.HashMap<>();
  private final Map<ResourceLocation, Integer> permaPendingCounts = new java.util.HashMap<>();
  private BlockPos builderHutPos;
  private boolean beltRebuildPending;

  public BuildingCreateShop(IColony colony, BlockPos location) {
    super(colony, location);
    this.lastMissingNetworkWarning = 0L;
    this.lastCourierDebugTime = 0L;
    this.lastCourierEntityDebugTime = 0L;
    this.lastCourierDebugDump = "";
    this.lastCourierEntityDump = "";
    this.lastAssignedCitizensDump = "";
    this.lastModuleAssignDump = "";
    this.lastEntityRepairDump = "";
    this.lastWarehouseCompareDump = "";
    this.warehouseRegistered = false;
    this.lastRackScanTick = -1L;
    this.lastInflightTick = -1L;
    this.shopResolver = null;
    this.permaWaitFullStack = false;
    this.lastPermaRequestTick = 0L;
    this.builderHutPos = null;
    this.beltRebuildPending = false;
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
      logAccessCheck(citizen, result);
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
    return permaWaitFullStack;
  }

  public Set<ResourceLocation> getPermaOres() {
    return java.util.Collections.unmodifiableSet(permaOres);
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
    beltRebuildPending = true;
  }

  @Override
  public void onPlacement() {
    super.onPlacement();
    ensureWarehouseRegistration();
    ensureDeliverableAssignment();
    com.thesettler_x_create.minecolonies.requestsystem.CreateShopResolverInjector
        .ensureGlobalResolver(getColony());
    ensurePickupLink();
    beltRebuildPending = true;
    trySpawnBeltBlueprint(getColony());
  }

  @Override
  public void onUpgradeComplete(int newLevel) {
    super.onUpgradeComplete(newLevel);
    ensureWarehouseRegistration();
    ensureDeliverableAssignment();
    com.thesettler_x_create.minecolonies.requestsystem.CreateShopResolverInjector
        .ensureGlobalResolver(getColony());
    ensurePickupLink();
    beltRebuildPending = true;
    trySpawnBeltBlueprint(getColony());
  }

  @Override
  public void onColonyTick(IColony colony) {
    super.onColonyTick(colony);
    ensureWarehouseRegistration();
    ensureDeliverableAssignment();
    com.thesettler_x_create.minecolonies.requestsystem.CreateShopResolverInjector
        .ensureGlobalResolver(colony);
    ensurePickupLink();
    if (beltRebuildPending && isBuilt() && !hasActiveWorkOrder(colony)) {
      if (trySpawnBeltBlueprint(colony)) {
        beltRebuildPending = false;
      }
    }
    tickPermaRequests(colony);
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
      tickInflightTracking(colony);
      debugCourierAssignments(colony);
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
      if (entity instanceof TileEntityRack rack) {
        rack.setInWarehouse(Boolean.TRUE);
        var warehouseModules = getModulesByType(WarehouseModule.class);
        if (!warehouseModules.isEmpty()) {
          WarehouseModule warehouseModule = warehouseModules.get(0);
          while (rack.getUpgradeSize() < warehouseModule.getStorageUpgrade()) {
            rack.upgradeRackSize();
          }
        }
      }
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
    if (warehouseRegistered || getColony() == null) {
      return;
    }
    var manager = getColony().getServerBuildingManager();
    if (manager == null) {
      return;
    }
    var warehouses = manager.getWareHouses();
    if (warehouses == null) {
      return;
    }
    if (!hasWarehouseModules()) {
      warehouses.remove(this);
      warehouseRegistered = false;
      return;
    }
    if (!warehouses.contains(this)) {
      warehouses.add(this);
    }
    warehouseRegistered = true;
  }

  private boolean hasWarehouseModules() {
    return getModule(BuildingModules.WAREHOUSE_COURIERS) != null
        && getModule(BuildingModules.WAREHOUSE_REQUEST_QUEUE) != null;
  }

  public void ensureRackContainers() {
    if (getColony() == null) {
      return;
    }
    Level level = getColony().getWorld();
    if (level == null) {
      return;
    }
    long now = level.getGameTime();
    if (lastRackScanTick >= 0 && now - lastRackScanTick < 40L) {
      return;
    }
    lastRackScanTick = now;
    int added = 0;
    Tuple<BlockPos, BlockPos> corners = getCorners();
    boolean usedFallback = false;
    if (corners != null && corners.getA() != null && corners.getB() != null) {
      BlockPos a = corners.getA();
      BlockPos b = corners.getB();
      int minX = Math.min(a.getX(), b.getX());
      int maxX = Math.max(a.getX(), b.getX());
      int minY = Math.min(a.getY(), b.getY());
      int maxY = Math.max(a.getY(), b.getY());
      int minZ = Math.min(a.getZ(), b.getZ());
      int maxZ = Math.max(a.getZ(), b.getZ());
      long volume = (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
      if (volume <= 20000L) {
        added += scanRackBox(level, minX, maxX, minY, maxY, minZ, maxZ);
      } else {
        usedFallback = true;
      }
    } else {
      usedFallback = true;
    }
    if (usedFallback) {
      BlockPos origin = getLocation().getInDimensionLocation();
      int radius = 12;
      int minX = origin.getX() - radius;
      int maxX = origin.getX() + radius;
      int minY = origin.getY() - 4;
      int maxY = origin.getY() + 4;
      int minZ = origin.getZ() - radius;
      int maxZ = origin.getZ() + radius;
      added += scanRackBox(level, minX, maxX, minY, maxY, minZ, maxZ);
    }
    if (isDebugRequests() && added > 0) {
      com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
          "[CreateShop] ensureRackContainers added={} total={}", added, containerList.size());
    }
  }

  /** Returns rack inventory counts for the given stack keys. */
  public java.util.Map<ItemStack, Integer> getStockCountsForKeys(List<ItemStack> keys) {
    java.util.Map<ItemStack, Integer> counts = new java.util.HashMap<>();
    if (keys == null || keys.isEmpty()) {
      return counts;
    }
    for (ItemStack key : keys) {
      if (key == null || key.isEmpty()) {
        continue;
      }
      ItemStack normalized = normalizeKey(key);
      if (!containsKey(counts, normalized)) {
        counts.put(normalized, 0);
      }
    }
    if (counts.isEmpty()) {
      return counts;
    }
    Level level = getColony() == null ? null : getColony().getWorld();
    if (level == null) {
      return counts;
    }
    ensureRackContainers();
    for (BlockPos pos : containerList) {
      if (!WorldUtil.isBlockLoaded(level, pos)) {
        continue;
      }
      BlockEntity entity = level.getBlockEntity(pos);
      if (!(entity instanceof TileEntityRack rack)) {
        continue;
      }
      addCountsFromHandler(rack.getInventory(), counts);
    }
    return counts;
  }

  private void addCountsFromHandler(
      net.neoforged.neoforge.items.IItemHandler handler, java.util.Map<ItemStack, Integer> counts) {
    if (handler == null || counts == null || counts.isEmpty()) {
      return;
    }
    for (int slot = 0; slot < handler.getSlots(); slot++) {
      ItemStack stack = handler.getStackInSlot(slot);
      if (stack == null || stack.isEmpty()) {
        continue;
      }
      ItemStack key = findMatchingKey(counts, stack);
      if (key != null) {
        counts.put(key, counts.get(key) + stack.getCount());
      }
    }
  }

  private ItemStack findMatchingKey(java.util.Map<ItemStack, Integer> counts, ItemStack stack) {
    if (counts == null || counts.isEmpty() || stack == null || stack.isEmpty()) {
      return null;
    }
    for (ItemStack key : counts.keySet()) {
      if (ItemStack.isSameItemSameComponents(key, stack)) {
        return key;
      }
    }
    return null;
  }

  private boolean containsKey(java.util.Map<ItemStack, Integer> counts, ItemStack key) {
    if (counts == null || counts.isEmpty() || key == null || key.isEmpty()) {
      return false;
    }
    for (ItemStack existing : counts.keySet()) {
      if (ItemStack.isSameItemSameComponents(existing, key)) {
        return true;
      }
    }
    return false;
  }

  private ItemStack normalizeKey(ItemStack stack) {
    ItemStack copy = stack.copy();
    copy.setCount(1);
    return copy;
  }

  /** Periodically reconciles inflight stock orders and notifies on overdue entries. */
  private void tickInflightTracking(IColony colony) {
    if (colony == null) {
      return;
    }
    Level level = colony.getWorld();
    if (level == null) {
      return;
    }
    long now = level.getGameTime();
    if (now != 0L
        && lastInflightTick >= 0
        && now - lastInflightTick < Config.INFLIGHT_CHECK_INTERVAL_TICKS.getAsLong()) {
      return;
    }
    lastInflightTick = now;
    CreateShopBlockEntity pickup = getPickupBlockEntity();
    if (pickup == null) {
      return;
    }
    List<ItemStack> inflightKeys = pickup.getInflightKeys();
    if (inflightKeys.isEmpty()) {
      return;
    }
    java.util.Map<ItemStack, Integer> currentCounts = getStockCountsForKeys(inflightKeys);
    pickup.reconcileInflight(currentCounts);
    List<CreateShopBlockEntity.InflightNotice> notices =
        pickup.consumeOverdueNotices(now, Config.INFLIGHT_TIMEOUT_TICKS.getAsLong());
    if (!notices.isEmpty()) {
      notifyShopkeeperOverdue(notices);
    }
  }

  /** Emits a shopkeeper interaction for each overdue inflight notice. */
  private void notifyShopkeeperOverdue(List<CreateShopBlockEntity.InflightNotice> notices) {
    if (notices == null || notices.isEmpty()) {
      return;
    }
    ICitizenData citizen = getShopkeeperCitizen();
    if (citizen == null) {
      return;
    }
    for (CreateShopBlockEntity.InflightNotice notice : notices) {
      if (notice == null || notice.stackKey == null || notice.stackKey.isEmpty()) {
        continue;
      }
      String requester =
          notice.requesterName == null || notice.requesterName.isBlank()
              ? "unknown requester"
              : notice.requesterName;
      String address =
          notice.address == null || notice.address.isBlank() ? "unknown address" : notice.address;
      String itemName = notice.stackKey.getHoverName().getString();
      String text =
          "Delivery seems lost for "
              + requester
              + ". Item: "
              + itemName
              + " x"
              + notice.remaining
              + " (address: "
              + address
              + ").";
      citizen.triggerInteraction(
          new SimpleNotificationInteraction(Component.literal(text), ChatPriority.IMPORTANT));
    }
  }

  /** Returns the assigned Create Shop worker, if any. */
  private ICitizenData getShopkeeperCitizen() {
    for (ICitizenData citizen : getAllAssignedCitizen()) {
      if (citizen == null) {
        continue;
      }
      if (citizen.getJob() instanceof JobCreateShop) {
        return citizen;
      }
    }
    return null;
  }

  private int scanRackBox(Level level, int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
    int added = 0;
    for (int x = minX; x <= maxX; x++) {
      for (int y = minY; y <= maxY; y++) {
        for (int z = minZ; z <= maxZ; z++) {
          BlockPos pos = new BlockPos(x, y, z);
          if (!WorldUtil.isBlockLoaded(level, pos)) {
            continue;
          }
          BlockState state = level.getBlockState(pos);
          if (!(state.getBlock() instanceof AbstractBlockMinecoloniesRack)) {
            continue;
          }
          if (containerList.contains(pos)) {
            continue;
          }
          addContainerPosition(pos);
          BlockEntity entity = level.getBlockEntity(pos);
          if (entity instanceof TileEntityRack rack) {
            rack.setInWarehouse(Boolean.TRUE);
            var warehouseModules = getModulesByType(WarehouseModule.class);
            if (!warehouseModules.isEmpty()) {
              WarehouseModule warehouseModule = warehouseModules.get(0);
              while (rack.getUpgradeSize() < warehouseModule.getStorageUpgrade()) {
                rack.upgradeRackSize();
              }
            }
          }
          added++;
        }
      }
    }
    return added;
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
    permaWaitFullStack = enabled;
    setDirty();
  }

  public void setPermaOre(ResourceLocation itemId, boolean enabled) {
    if (itemId == null) {
      return;
    }
    if (enabled) {
      permaOres.add(itemId);
    } else {
      permaOres.remove(itemId);
    }
    setDirty();
  }

  private void setDirty() {
    if (getColony() != null) {
      getColony().markDirty();
    }
  }

  private void tickPermaRequests(IColony colony) {
    if (colony == null || permaOres.isEmpty() || !canUsePermaRequests() || !isWorkerWorking()) {
      if (isDebugRequests()) {
        TheSettlerXCreate.LOGGER.info(
            "[CreateShop] perma tick skipped: colony={} permaOres={} canUse={}",
            colony == null ? "<null>" : colony.getID(),
            permaOres.size(),
            canUsePermaRequests());
      }
      return;
    }
    Level level = colony.getWorld();
    if (level == null || level.isClientSide) {
      if (isDebugRequests()) {
        TheSettlerXCreate.LOGGER.info("[CreateShop] perma tick skipped: level={}", level);
      }
      return;
    }
    long now = level.getGameTime();
    if (now - lastPermaRequestTick < Config.PERMA_REQUEST_INTERVAL_TICKS.getAsLong()) {
      if (isDebugRequests()) {
        TheSettlerXCreate.LOGGER.info(
            "[CreateShop] perma tick throttled: now={} last={} diff={}",
            now,
            lastPermaRequestTick,
            now - lastPermaRequestTick);
      }
      return;
    }
    lastPermaRequestTick = now;

    IRequestManager manager = colony.getRequestManager();
    if (manager == null) {
      if (isDebugRequests()) {
        TheSettlerXCreate.LOGGER.info("[CreateShop] perma tick skipped: request manager null");
      }
      return;
    }
    IRequester requester = getRequester();
    if (requester == null) {
      if (isDebugRequests()) {
        TheSettlerXCreate.LOGGER.info("[CreateShop] perma tick skipped: requester null");
      }
      return;
    }

    List<ResourceLocation> ordered = new ArrayList<>(permaOres);
    ordered.sort(Comparator.comparing(ResourceLocation::toString));

    for (ResourceLocation itemId : ordered) {
      Item item = BuiltInRegistries.ITEM.get(itemId);
      if (item == null || item == net.minecraft.world.item.Items.AIR) {
        if (isDebugRequests()) {
          TheSettlerXCreate.LOGGER.info("[CreateShop] perma skip: missing item {}", itemId);
        }
        continue;
      }
      ItemStack stack = new ItemStack(item, 1);
      int available = countInWarehouses(stack);
      int pending = permaPendingCounts.getOrDefault(itemId, 0);
      int requestable = Math.max(0, available - pending);
      if (isDebugRequests()) {
        TheSettlerXCreate.LOGGER.info(
            "[CreateShop] perma eval item={} available={} pending={} requestable={} waitFull={}",
            itemId,
            available,
            pending,
            requestable,
            permaWaitFullStack);
      }
      if (requestable <= 0) {
        continue;
      }
      int maxStack = Math.max(1, stack.getMaxStackSize());
      int amount = permaWaitFullStack ? (requestable / maxStack) * maxStack : requestable;
      if (amount <= 0) {
        continue;
      }
      Stack deliverable = new Stack(stack, amount, 1);
      IToken<?> token = manager.createRequest(requester, deliverable);
      if (token != null) {
        permaPendingRequests.put(token, new PendingPermaRequest(itemId, amount));
        permaPendingCounts.merge(itemId, amount, Integer::sum);
        if (isDebugRequests()) {
          TheSettlerXCreate.LOGGER.info(
              "[CreateShop] perma request created token={} item={} amount={}",
              token,
              itemId,
              amount);
        }
      }
    }
  }

  private int countInWarehouses(ItemStack stack) {
    if (stack == null || stack.isEmpty() || getColony() == null) {
      return 0;
    }
    var manager = getColony().getServerBuildingManager();
    if (manager == null) {
      return 0;
    }
    List<IWareHouse> warehouses = manager.getWareHouses();
    if (warehouses == null || warehouses.isEmpty()) {
      return 0;
    }
    int total = 0;
    for (IWareHouse warehouse : warehouses) {
      if (warehouse == null || warehouse == this) {
        continue;
      }
      if (!(warehouse.getTileEntity() instanceof AbstractTileEntityWareHouse wareHouse)) {
        continue;
      }
      for (var entry :
          wareHouse.getMatchingItemStacksInWarehouse(match -> matchesStack(match, stack))) {
        ItemStack found = entry.getA();
        if (found == null || found.isEmpty()) {
          continue;
        }
        total += found.getCount();
      }
    }
    return Math.max(0, total);
  }

  private boolean matchesStack(ItemStack candidate, ItemStack target) {
    if (candidate == null || target == null) {
      return false;
    }
    return ItemStack.isSameItemSameComponents(candidate, target);
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
    ResourceLocation fallback = BELT_BLUEPRINT_L1;
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
      return BELT_BLUEPRINT_L2;
    }
    return BELT_BLUEPRINT_L1;
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

  private void debugCourierAssignments(IColony colony) {
    if (!isDebugRequests() || colony == null) {
      return;
    }
    Level level = colony.getWorld();
    long now = level == null ? 0L : level.getGameTime();
    if (now != 0L && now - lastCourierDebugTime < Config.COURIER_DEBUG_COOLDOWN.getAsLong()) {
      return;
    }
    lastCourierDebugTime = now;
    var manager = colony.getRequestManager();
    if (!(manager
        instanceof
        com.minecolonies.core.colony.requestsystem.management.IStandardRequestManager
        standardManager)) {
      return;
    }
    java.util.List<String> debugLines = new java.util.ArrayList<>();
    var assignmentStore = standardManager.getRequestResolverRequestAssignmentDataStore();
    var assignments = assignmentStore == null ? null : assignmentStore.getAssignments();
    var typeStore = standardManager.getRequestableTypeRequestResolverAssignmentDataStore();
    var typeAssignments = typeStore == null ? null : typeStore.getAssignments();
    var requestableResolvers =
        typeAssignments == null ? null : typeAssignments.get(TypeConstants.REQUESTABLE);
    if (requestableResolvers != null) {
      int loggedResolvers = 0;
      for (var resolverToken : requestableResolvers) {
        if (loggedResolvers >= 5) {
          break;
        }
        try {
          var resolver = standardManager.getResolverHandler().getResolver(resolverToken);
          if (!(resolver
              instanceof
              com.minecolonies.core.colony.requestsystem.resolvers.DeliveryRequestResolver)) {
            continue;
          }
          String info = "<unknown>";
          try {
            var getLocation = resolver.getClass().getMethod("getLocation");
            Object location = getLocation.invoke(resolver);
            if (location != null) {
              info = location.toString();
            }
          } catch (Exception ignored) {
            // Ignore.
          }
          var assignedRequests = assignments == null ? null : assignments.get(resolverToken);
          int assignedCount = assignedRequests == null ? 0 : assignedRequests.size();
          debugLines.add(
              "deliveryResolver token="
                  + resolverToken
                  + " assignedCount="
                  + assignedCount
                  + " location="
                  + info);
          if (assignedRequests != null) {
            int logged = 0;
            for (var token : assignedRequests) {
              if (logged >= 5) {
                break;
              }
              try {
                var request = standardManager.getRequestHandler().getRequest(token);
                String type =
                    request == null || request.getRequest() == null
                        ? "<null>"
                        : request.getRequest().getClass().getName();
                String state = request == null ? "<null>" : String.valueOf(request.getState());
                debugLines.add("deliveryRequest " + token + " type=" + type + " state=" + state);
                logged++;
              } catch (IllegalArgumentException ignored) {
                // Missing request.
              }
            }
          }
          loggedResolvers++;
        } catch (IllegalArgumentException ignored) {
          // Missing resolver.
        }
      }
    }

    var assignedCitizens = getAllAssignedCitizen();
    debugLines.add("assignedCitizens=" + assignedCitizens.size());
    int loggedCitizens = 0;
    for (var citizen : assignedCitizens) {
      if (loggedCitizens >= 3) {
        break;
      }
      String name = citizen.getName() == null ? "<unknown>" : citizen.getName();
      var job = citizen.getJob();
      String jobName = job == null ? "<none>" : job.getClass().getName();
      String jobState = "<unknown>";
      String citizenPos = describeCitizenPosition(citizen);
      if (job != null) {
        try {
          var method = job.getClass().getMethod("getState");
          Object state = method.invoke(job);
          jobState = state == null ? "<null>" : state.toString();
        } catch (Exception ignored) {
          // Fallback below.
        }
        if ("<unknown>".equals(jobState)) {
          try {
            var method = job.getClass().getMethod("isWorking");
            Object state = method.invoke(job);
            jobState = state == null ? "<null>" : "isWorking=" + state;
          } catch (Exception ignored) {
            // Ignore.
          }
        }
        jobState = appendJobDetail(job, jobState, "getCurrentRequest");
        jobState = appendJobDetail(job, jobState, "getCurrentRequestToken");
        jobState = appendJobDetail(job, jobState, "getRequestToken");
        jobState = appendJobDetail(job, jobState, "getCurrentTask");
      }
      debugLines.add(
          "citizen=" + name + " job=" + jobName + " state=" + jobState + " pos=" + citizenPos);
      if (job != null) {
        Object currentTask = tryInvoke(job, "getCurrentTask");
        if (currentTask != null) {
          debugLines.add(
              "task class="
                  + currentTask.getClass().getName()
                  + " detail="
                  + describeTask(currentTask));
        }
      }
      if ("<entity-null>".equals(citizenPos)) {
        if (shouldLogCourierEntity(level)) {
          logCitizenEntityDiagnostics(citizen, level);
        }
        String key = describeCitizenKey(citizen);
        if (shouldAttemptEntityRepair(key, level)) {
          attemptCitizenEntityRepair(citizen, level);
        }
      }
      loggedCitizens++;
    }

    String dump = String.join(" | ", debugLines);
    if (!dump.equals(lastCourierDebugDump)) {
      lastCourierDebugDump = dump;
      com.thesettler_x_create.TheSettlerXCreate.LOGGER.info("[CreateShop] courier debug: {}", dump);
    }

    logAssignedCitizensChanges();
    logModuleAssignments();
    logWarehouseComparison(colony);
    logCourierWorkBuildings(colony);
  }

  private void logAssignedCitizensChanges() {
    var citizens = getAllAssignedCitizen();
    java.util.List<String> entries = new java.util.ArrayList<>();
    java.util.Map<String, String> currentInfo = new java.util.HashMap<>();
    for (var citizen : citizens) {
      entries.add(describeCitizenAssignmentDetail(citizen));
      String key = describeCitizenKey(citizen);
      currentInfo.put(key, describeCitizenAssignmentDetail(citizen));
      if (isDebugRequests()) {
        logCitizenUuidLookup(citizen);
      }
    }
    String dump = String.join(" | ", entries);
    if (!dump.equals(lastAssignedCitizensDump)) {
      lastAssignedCitizensDump = dump;
      com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
          "[CreateShop] courier assign change: {}", dump.isEmpty() ? "<none>" : dump);
      logAssignmentDelta("courier hire", lastAssignedCitizenInfo, currentInfo);
      lastAssignedCitizenInfo.clear();
      lastAssignedCitizenInfo.putAll(currentInfo);
      Level level = getColony() == null ? null : getColony().getWorld();
      for (var citizen : citizens) {
        if (citizen == null || safeCitizenEntityId(citizen) >= 0) {
          continue;
        }
        if (getCitizenEntity(citizen, level) != null) {
          attemptCitizenEntityRepair(citizen, level);
        }
      }
    }
  }

  private void logModuleAssignments() {
    CourierAssignmentModule module = getFirstModuleOccurance(CourierAssignmentModule.class);
    if (module == null) {
      return;
    }
    java.util.List<String> entries = new java.util.ArrayList<>();
    java.util.Map<String, String> currentInfo = new java.util.HashMap<>();
    java.util.List<ICitizenData> assignedCitizens = java.util.Collections.emptyList();
    java.util.List<? extends java.util.Optional<?>> assignedEntities =
        java.util.Collections.emptyList();
    try {
      assignedCitizens = module.getAssignedCitizen();
      assignedEntities = module.getAssignedEntities();
      int count = Math.max(assignedCitizens.size(), assignedEntities.size());
      for (int i = 0; i < count; i++) {
        String citizenInfo =
            i < assignedCitizens.size()
                ? describeCitizenAssignmentDetail(assignedCitizens.get(i))
                : "<no-citizen>";
        if (i < assignedCitizens.size()) {
          ICitizenData citizen = assignedCitizens.get(i);
          currentInfo.put(
              describeCitizenKey(citizen),
              "slot=" + i + " " + describeCitizenAssignmentDetail(citizen));
        }
        String entityInfo = "<no-entity>";
        if (i < assignedEntities.size()) {
          var optionalEntity = assignedEntities.get(i);
          if (optionalEntity != null && optionalEntity.isPresent()) {
            Object entity = optionalEntity.get();
            if (entity instanceof net.minecraft.world.entity.Entity mcEntity) {
              entityInfo =
                  mcEntity.getClass().getName()
                      + " pos="
                      + mcEntity.blockPosition()
                      + " dim="
                      + mcEntity.level().dimension().location();
            } else {
              entityInfo = entity.getClass().getName();
            }
          } else {
            entityInfo = "<empty>";
          }
        }
        entries.add("slot=" + i + " citizen=" + citizenInfo + " entity=" + entityInfo);
      }
    } catch (Exception ignored) {
      // If module API changes, skip.
    }
    String dump = String.join(" | ", entries);
    if (!dump.equals(lastModuleAssignDump)) {
      lastModuleAssignDump = dump;
      com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
          "[CreateShop] courier module assign: {}", dump.isEmpty() ? "<none>" : dump);
      logAssignmentDelta("courier module assign", lastModuleCitizenInfo, currentInfo);
      lastModuleCitizenInfo.clear();
      lastModuleCitizenInfo.putAll(currentInfo);
      Level level = getColony() == null ? null : getColony().getWorld();
      int count = Math.min(assignedCitizens.size(), assignedEntities.size());
      for (int i = 0; i < count; i++) {
        ICitizenData citizen = assignedCitizens.get(i);
        if (citizen == null || safeCitizenEntityId(citizen) >= 0) {
          continue;
        }
        java.util.Optional<?> opt = assignedEntities.get(i);
        if (opt == null || opt.isEmpty()) {
          continue;
        }
        attemptForceEntityId(citizen, level);
      }
    }
  }

  private void logWarehouseComparison(IColony colony) {
    if (colony == null) {
      return;
    }
    var manager = colony.getServerBuildingManager();
    if (manager == null) {
      return;
    }
    var warehouses = manager.getWareHouses();
    if (warehouses == null || warehouses.isEmpty()) {
      return;
    }
    java.util.List<String> dumps = new java.util.ArrayList<>();
    for (var warehouse : warehouses) {
      if (warehouse == this) {
        continue;
      }
      if (!(warehouse instanceof AbstractBuilding building)) {
        continue;
      }
      CourierAssignmentModule module =
          building.getFirstModuleOccurance(CourierAssignmentModule.class);
      if (module == null) {
        continue;
      }
      String label =
          building.getSchematicName() + "@" + building.getLocation().getInDimensionLocation();
      java.util.List<String> entries = new java.util.ArrayList<>();
      try {
        var assignedCitizens = module.getAssignedCitizen();
        var assignedEntities = module.getAssignedEntities();
        int count = Math.max(assignedCitizens.size(), assignedEntities.size());
        for (int i = 0; i < count; i++) {
          String citizenInfo =
              i < assignedCitizens.size()
                  ? describeCitizenAssignmentDetail(assignedCitizens.get(i))
                  : "<no-citizen>";
          String entityInfo = "<no-entity>";
          if (i < assignedEntities.size()) {
            var optionalEntity = assignedEntities.get(i);
            if (optionalEntity != null && optionalEntity.isPresent()) {
              Object entity = optionalEntity.get();
              if (entity instanceof net.minecraft.world.entity.Entity mcEntity) {
                entityInfo =
                    mcEntity.getClass().getName()
                        + " pos="
                        + mcEntity.blockPosition()
                        + " dim="
                        + mcEntity.level().dimension().location();
              } else {
                entityInfo = entity.getClass().getName();
              }
            } else {
              entityInfo = "<empty>";
            }
          }
          entries.add("slot=" + i + " citizen=" + citizenInfo + " entity=" + entityInfo);
        }
      } catch (Exception ignored) {
        // Ignore module API changes.
      }
      dumps.add(label + " => " + String.join(" | ", entries));
    }
    String dump = String.join(" || ", dumps);
    if (!dump.equals(lastWarehouseCompareDump)) {
      lastWarehouseCompareDump = dump;
      com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
          "[CreateShop] courier warehouse compare: {}", dump.isEmpty() ? "<none>" : dump);
    }
  }

  private void logCourierWorkBuildings(IColony colony) {
    if (colony == null) {
      return;
    }
    var manager = colony.getServerBuildingManager();
    if (manager == null) {
      return;
    }
    var warehouses = manager.getWareHouses();
    if (warehouses == null || warehouses.isEmpty()) {
      return;
    }
    java.util.Set<String> warehousePos = new java.util.HashSet<>();
    for (var wh : warehouses) {
      if (wh instanceof AbstractBuilding building) {
        warehousePos.add(String.valueOf(building.getLocation().getInDimensionLocation()));
      }
    }
    java.util.List<String> entries = new java.util.ArrayList<>();
    for (var citizen : colony.getCitizenManager().getCitizens()) {
      String job = citizen.getJob() == null ? "<none>" : citizen.getJob().getClass().getName();
      if (!job.contains("Deliveryman")) {
        continue;
      }
      Object workBuilding = tryInvoke(citizen, "getWorkBuilding");
      String workPos = workBuilding == null ? "<null>" : String.valueOf(workBuilding);
      boolean isWarehouse = warehousePos.contains(workPos);
      entries.add(
          describeCitizen(citizen) + " workBuilding=" + workPos + " isWarehouse=" + isWarehouse);
    }
    String dump = String.join(" | ", entries);
    if (!dump.equals(lastWarehouseCompareDump)) {
      lastWarehouseCompareDump = dump;
      com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
          "[CreateShop] courier workbuilding compare: {}", dump.isEmpty() ? "<none>" : dump);
    }
  }

  private void logCitizenUuidLookup(ICitizenData citizen) {
    if (citizen == null || getColony() == null) {
      return;
    }
    Level level = getColony().getWorld();
    if (!(level instanceof net.minecraft.server.level.ServerLevel serverLevel)) {
      return;
    }
    Object uuidValue = tryInvoke(citizen, "getUUID");
    if (!(uuidValue instanceof java.util.UUID uuid)) {
      return;
    }
    var entity = serverLevel.getEntity(uuid);
    String lookup =
        entity == null
            ? "<missing>"
            : entity.getClass().getName()
                + " pos="
                + entity.blockPosition()
                + " dim="
                + serverLevel.dimension().location();
    String dump = "uuid=" + uuid + " lookup=" + lookup;
    if (!dump.equals(lastCourierEntityDump)) {
      lastCourierEntityDump = dump;
      com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
          "[CreateShop] courier assign uuid lookup: {}", dump);
    }
  }

  private void logAccessCheck(ICitizenData citizen, boolean result) {
    int key = citizen == null ? -1 : safeCitizenId(citizen);
    Boolean last = lastAccessResult.get(key);
    if (last != null && last == result) {
      return;
    }
    lastAccessResult.put(key, result);
    com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
        "[CreateShop] courier access check: {} -> {}", describeCitizen(citizen), result);
  }

  private String describeCitizen(ICitizenData citizen) {
    if (citizen == null) {
      return "<null-citizen>";
    }
    String name = citizen.getName() == null ? "<unknown>" : citizen.getName();
    Object idValue = tryInvoke(citizen, "getId");
    Object entityIdValue = tryInvoke(citizen, "getEntityId");
    Object uuidValue = tryInvoke(citizen, "getUUID");
    String jobName = citizen.getJob() == null ? "<none>" : citizen.getJob().getClass().getName();
    return "name="
        + name
        + " id="
        + (idValue == null ? "<null>" : idValue)
        + " entityId="
        + (entityIdValue == null ? "<null>" : entityIdValue)
        + " uuid="
        + (uuidValue == null ? "<null>" : uuidValue)
        + " job="
        + jobName;
  }

  private String describeCitizenKey(ICitizenData citizen) {
    if (citizen == null) {
      return "<null-citizen>";
    }
    Object idValue = tryInvoke(citizen, "getId");
    Object uuidValue = tryInvoke(citizen, "getUUID");
    String id = idValue == null ? "<null>" : String.valueOf(idValue);
    String uuid = uuidValue == null ? "<null>" : String.valueOf(uuidValue);
    return id + ":" + uuid;
  }

  private String describeCitizenAssignmentDetail(ICitizenData citizen) {
    if (citizen == null) {
      return "<null-citizen>";
    }
    String base = describeCitizen(citizen);
    String workBuilding = describeCitizenWorkBuilding(citizen);
    return base + " workBuilding=" + workBuilding;
  }

  private String describeCitizenWorkBuilding(ICitizenData citizen) {
    Object workBuilding = tryInvoke(citizen, "getWorkBuilding");
    if (workBuilding == null) {
      return "<none>";
    }
    Object location = tryInvoke(workBuilding, "getLocation");
    if (location != null) {
      return workBuilding.getClass().getName() + "@" + location;
    }
    return workBuilding.getClass().getName();
  }

  private void logAssignmentDelta(
      String label,
      java.util.Map<String, String> previousInfo,
      java.util.Map<String, String> currentInfo) {
    if (previousInfo == null) {
      return;
    }
    java.util.List<String> added = new java.util.ArrayList<>();
    java.util.List<String> removed = new java.util.ArrayList<>();
    java.util.List<String> changed = new java.util.ArrayList<>();
    for (var entry : currentInfo.entrySet()) {
      if (!previousInfo.containsKey(entry.getKey())) {
        added.add(entry.getValue());
      } else {
        String prev = previousInfo.get(entry.getKey());
        if (prev != null && !prev.equals(entry.getValue())) {
          changed.add(prev + " -> " + entry.getValue());
        }
      }
    }
    for (var entry : previousInfo.entrySet()) {
      if (!currentInfo.containsKey(entry.getKey())) {
        removed.add(entry.getValue());
      }
    }
    if (added.isEmpty() && removed.isEmpty() && changed.isEmpty()) {
      return;
    }
    String addedDump = added.isEmpty() ? "<none>" : String.join(" | ", added);
    String removedDump = removed.isEmpty() ? "<none>" : String.join(" | ", removed);
    String changedDump = changed.isEmpty() ? "<none>" : String.join(" | ", changed);
    com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
        "[CreateShop] {} delta: added={} removed={} changed={}",
        label,
        addedDump,
        removedDump,
        changedDump);
  }

  private int safeCitizenId(ICitizenData citizen) {
    Object idValue = tryInvoke(citizen, "getId");
    if (idValue instanceof Number number) {
      return number.intValue();
    }
    return -1;
  }

  private boolean shouldLogCourierEntity(Level level) {
    long now = level == null ? 0L : level.getGameTime();
    if (now == 0L
        || now - lastCourierEntityDebugTime >= Config.COURIER_ENTITY_DEBUG_COOLDOWN.getAsLong()) {
      lastCourierEntityDebugTime = now;
      return true;
    }
    return false;
  }

  private boolean shouldAttemptEntityRepair(String key, Level level) {
    if (key == null || level == null) {
      return false;
    }
    long now = level.getGameTime();
    long last = lastEntityRepairAttemptTime.getOrDefault(key, 0L);
    if (now == 0L || now - last >= Config.COURIER_ENTITY_DEBUG_COOLDOWN.getAsLong()) {
      lastEntityRepairAttemptTime.put(key, now);
      return true;
    }
    return false;
  }

  private void logCitizenEntityDiagnostics(ICitizenData citizen, Level level) {
    if (citizen == null || level == null) {
      return;
    }
    Object idValue = tryInvoke(citizen, "getId");
    Object entityIdValue = tryInvoke(citizen, "getEntityId");
    Object uuidValue = tryInvoke(citizen, "getUUID");
    int entityId = -1;
    if (entityIdValue instanceof Number number) {
      entityId = number.intValue();
    }
    String entityLookup = "<unknown>";
    if (entityId >= 0 && level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
      var entity = serverLevel.getEntity(entityId);
      entityLookup = entity == null ? "<missing>" : entity.getClass().getName();
    }
    String uuidInfo = uuidValue == null ? "<null>" : uuidValue.toString();
    String uuidLookup = "<n/a>";
    boolean hasUuidEntity = false;
    if (uuidValue instanceof java.util.UUID uuid
        && level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
      var entity = serverLevel.getEntity(uuid);
      if (entity != null) {
        uuidLookup =
            entity.getClass().getName()
                + " pos="
                + entity.blockPosition()
                + " dim="
                + serverLevel.dimension().location();
        hasUuidEntity = true;
      } else {
        uuidLookup = "<missing>";
      }
    }
    String dump =
        "id="
            + (idValue == null ? "<null>" : idValue)
            + " entityId="
            + (entityId >= 0 ? entityId : "<null>")
            + " uuid="
            + uuidInfo
            + " idLookup="
            + entityLookup
            + " uuidLookup="
            + uuidLookup;
    if (!dump.equals(lastCourierEntityDump)) {
      lastCourierEntityDump = dump;
      com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
          "[CreateShop] courier debug: citizen entity missing {}", dump);
    }
    if (hasUuidEntity && entityId < 0) {
      attemptCitizenEntityRepair(citizen, level);
    }
  }

  private void attemptCitizenEntityRepair(ICitizenData citizen, Level level) {
    if (citizen == null || level == null) {
      return;
    }
    boolean invoked = false;
    String result = "<unknown>";
    try {
      var method = citizen.getClass().getMethod("updateEntityIfNecessary", Level.class);
      method.invoke(citizen, level);
      invoked = true;
      result = "ok";
    } catch (NoSuchMethodException ignored) {
      try {
        var method = citizen.getClass().getMethod("updateEntityIfNecessary");
        method.invoke(citizen);
        invoked = true;
        result = "ok";
      } catch (Exception ex) {
        result = ex.getMessage() == null ? "<error>" : ex.getMessage();
      }
    } catch (Exception ex) {
      result = ex.getMessage() == null ? "<error>" : ex.getMessage();
    }
    boolean forceInvoked = false;
    String forceResult = "<skipped>";
    boolean spawnInvoked = false;
    String spawnResult = "<skipped>";
    boolean registerInvoked = false;
    String registerResult = "<skipped>";
    boolean hasEntity = getCitizenEntity(citizen, level) != null;
    if (safeCitizenEntityId(citizen) < 0 && hasEntity) {
      forceResult = attemptForceEntityId(citizen, level);
      forceInvoked = !"<skipped>".equals(forceResult);
    }
    if (invoked && safeCitizenEntityId(citizen) < 0 && !hasEntity) {
      spawnResult = attemptSpawnOrCreateCitizen(citizen, level);
      spawnInvoked = !"<skipped>".equals(spawnResult);
    }
    if (safeCitizenEntityId(citizen) < 0 && !hasEntity) {
      registerResult = attemptRegisterCivilian(citizen, level);
      registerInvoked = !"<skipped>".equals(registerResult);
    }
    if (safeCitizenEntityId(citizen) < 0 && !hasEntity) {
      forceResult = attemptForceEntityId(citizen, level);
      forceInvoked = !"<skipped>".equals(forceResult);
    }
    String dump =
        "id="
            + safeCitizenId(citizen)
            + " updateInvoked="
            + invoked
            + " updateResult="
            + result
            + " spawnInvoked="
            + spawnInvoked
            + " spawnResult="
            + spawnResult
            + " registerInvoked="
            + registerInvoked
            + " registerResult="
            + registerResult
            + " forceInvoked="
            + forceInvoked
            + " forceResult="
            + forceResult;
    if (!dump.equals(lastEntityRepairDump)) {
      lastEntityRepairDump = dump;
      com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
          "[CreateShop] courier entity repair: {}", dump);
    }
  }

  private String attemptSpawnOrCreateCitizen(ICitizenData citizen, Level level) {
    if (citizen == null || level == null || getColony() == null) {
      return "<skipped>";
    }
    try {
      var manager = getColony().getCitizenManager();
      if (manager == null) {
        return "<no-manager>";
      }
      var method =
          manager
              .getClass()
              .getMethod(
                  "spawnOrCreateCitizen",
                  com.minecolonies.api.colony.ICitizenData.class,
                  Level.class);
      method.invoke(manager, citizen, level);
      return "ok";
    } catch (NoSuchMethodException ex) {
      return "<no-method>";
    } catch (Exception ex) {
      return ex.getMessage() == null ? "<error>" : ex.getMessage();
    }
  }

  private String attemptRegisterCivilian(ICitizenData citizen, Level level) {
    if (citizen == null || level == null || getColony() == null) {
      return "<skipped>";
    }
    Object entity = getCitizenEntity(citizen, level);
    if (entity == null) {
      return "<no-entity>";
    }
    try {
      var manager = getColony().getCitizenManager();
      if (manager == null) {
        return "<no-manager>";
      }
      java.lang.reflect.Method target = null;
      for (var method : manager.getClass().getMethods()) {
        if (!"registerCivilian".equals(method.getName()) || method.getParameterCount() != 1) {
          continue;
        }
        target = method;
        break;
      }
      if (target == null) {
        return "<no-method>";
      }
      target.invoke(manager, entity);
      return "ok";
    } catch (Exception ex) {
      return ex.getMessage() == null ? "<error>" : ex.getMessage();
    }
  }

  private Object getCitizenEntity(ICitizenData citizen, Level level) {
    java.util.Optional<com.minecolonies.api.entity.citizen.AbstractEntityCitizen> opt =
        citizen.getEntity();
    if (opt.isPresent()) {
      return opt.get();
    }
    Object uuidValue = tryInvoke(citizen, "getUUID");
    if (uuidValue instanceof java.util.UUID uuid
        && level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
      return serverLevel.getEntity(uuid);
    }
    return null;
  }

  private String attemptForceEntityId(ICitizenData citizen, Level level) {
    if (citizen == null || level == null) {
      return "<skipped>";
    }
    Object entityOpt = tryInvoke(citizen, "getEntity");
    boolean hasEntityOpt = entityOpt instanceof java.util.Optional<?>;
    boolean entityOptPresent = hasEntityOpt && ((java.util.Optional<?>) entityOpt).isPresent();
    Object entity = getCitizenEntity(citizen, level);
    if (!(entity instanceof net.minecraft.world.entity.Entity mcEntity)) {
      Object uuidValue = tryInvoke(citizen, "getUUID");
      String uuidInfo = uuidValue instanceof java.util.UUID uuid ? uuid.toString() : "<null>";
      String lookup = "<n/a>";
      if (uuidValue instanceof java.util.UUID uuid
          && level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
        var found = serverLevel.getEntity(uuid);
        lookup =
            found == null
                ? "<missing>"
                : found.getClass().getName()
                    + " pos="
                    + found.blockPosition()
                    + " dim="
                    + serverLevel.dimension().location();
      }
      String optInfo = hasEntityOpt ? (entityOptPresent ? "present" : "empty") : "n/a";
      return "<no-entity opt=" + optInfo + " uuid=" + uuidInfo + " uuidLookup=" + lookup + ">";
    }
    Object citizenUuidValue = tryInvoke(citizen, "getUUID");
    if (citizenUuidValue instanceof java.util.UUID citizenUuid) {
      java.util.UUID entityUuid = mcEntity.getUUID();
      if (!citizenUuid.equals(entityUuid)) {
        return "<uuid-mismatch citizen=" + citizenUuid + " entity=" + entityUuid + ">";
      }
    }
    int id = mcEntity.getId();
    try {
      java.lang.reflect.Method target = null;
      for (var method : citizen.getClass().getMethods()) {
        if (!"setEntity".equals(method.getName()) || method.getParameterCount() != 1) {
          continue;
        }
        Class<?> param = method.getParameterTypes()[0];
        if (param.isAssignableFrom(entity.getClass())) {
          target = method;
          break;
        }
      }
      if (target != null) {
        target.invoke(citizen, entity);
        return "setEntity ok id=" + id;
      }
    } catch (Exception ex) {
      return ex.getMessage() == null ? "<error>" : ex.getMessage();
    }
    try {
      var method = citizen.getClass().getMethod("setEntityId", int.class);
      method.invoke(citizen, id);
      return "setEntityId(int) ok id=" + id;
    } catch (NoSuchMethodException ignored) {
      // Fallthrough to other options.
    } catch (Exception ex) {
      return ex.getMessage() == null ? "<error>" : ex.getMessage();
    }
    try {
      var method = citizen.getClass().getMethod("setEntityId", Integer.class);
      method.invoke(citizen, Integer.valueOf(id));
      return "setEntityId(Integer) ok id=" + id;
    } catch (NoSuchMethodException ignored) {
      // Fallthrough to field.
    } catch (Exception ex) {
      return ex.getMessage() == null ? "<error>" : ex.getMessage();
    }
    try {
      java.lang.reflect.Field field = null;
      for (var f : citizen.getClass().getDeclaredFields()) {
        String name = f.getName();
        if ("entityId".equals(name) || "entityID".equals(name)) {
          field = f;
          break;
        }
      }
      if (field == null) {
        return "<no-field>";
      }
      field.setAccessible(true);
      field.setInt(citizen, id);
      return "field entityId ok id=" + id;
    } catch (Exception ex) {
      return ex.getMessage() == null ? "<error>" : ex.getMessage();
    }
  }

  private int safeCitizenEntityId(ICitizenData citizen) {
    Object entityIdValue = tryInvoke(citizen, "getEntityId");
    if (entityIdValue instanceof Number number) {
      return number.intValue();
    }
    return -1;
  }

  private String describeCitizenPosition(ICitizenData citizen) {
    if (citizen == null) {
      return "<unknown>";
    }
    java.util.Optional<com.minecolonies.api.entity.citizen.AbstractEntityCitizen> entityOpt =
        citizen.getEntity();
    if (entityOpt.isPresent()) {
      var mcEntity = entityOpt.get();
      var pos = mcEntity.blockPosition();
      var dim = mcEntity.level().dimension();
      return "pos=" + pos + " dim=" + dim.location();
    }
    try {
      var method = citizen.getClass().getMethod("getPosition");
      Object pos = method.invoke(citizen);
      if (pos != null) {
        return pos.toString();
      }
    } catch (Exception ignored) {
      // Ignore.
    }
    return "<unknown>";
  }

  private Object tryInvoke(Object target, String methodName) {
    if (target == null) {
      return null;
    }
    try {
      var method = target.getClass().getMethod(methodName);
      return method.invoke(target);
    } catch (Exception ex) {
      logReflectionFailure(target, methodName, ex);
      return null;
    }
  }

  private void logReflectionFailure(Object target, String methodName, Exception ex) {
    if (!isDebugRequests() || target == null) {
      return;
    }
    String key =
        target.getClass().getName() + "#" + methodName + ":" + ex.getClass().getSimpleName();
    if (!REFLECTION_WARNED.add(key)) {
      return;
    }
    com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
        "[CreateShop] reflection call failed {} err={}",
        key,
        ex.getMessage() == null ? "<null>" : ex.getMessage());
  }

  private String describeTask(Object task) {
    if (task == null) {
      return "<none>";
    }
    StringBuilder detail = new StringBuilder();
    Object token = tryInvoke(task, "getRequestToken");
    if (token != null) {
      detail.append("token=").append(token).append(" ");
    }
    Object request = tryInvoke(task, "getRequest");
    if (request != null) {
      detail.append("request=").append(request).append(" ");
    }
    Object requester = tryInvoke(task, "getRequester");
    if (requester != null) {
      detail.append("requester=").append(requester).append(" ");
    }
    Object from = tryInvoke(task, "getFrom");
    if (from != null) {
      detail.append("from=").append(from).append(" ");
    }
    Object to = tryInvoke(task, "getTo");
    if (to != null) {
      detail.append("to=").append(to).append(" ");
    }
    Object location = tryInvoke(task, "getLocation");
    if (location != null) {
      detail.append("location=").append(location).append(" ");
    }
    Object pickup = tryInvoke(task, "getPickupLocation");
    if (pickup != null) {
      detail.append("pickup=").append(pickup).append(" ");
    }
    Object delivery = tryInvoke(task, "getDeliveryLocation");
    if (delivery != null) {
      detail.append("delivery=").append(delivery).append(" ");
    }
    Object start = tryInvoke(task, "getStart");
    if (start != null) {
      detail.append("start=").append(start).append(" ");
    }
    Object target = tryInvoke(task, "getTarget");
    if (target != null) {
      detail.append("target=").append(target).append(" ");
    }
    String result = detail.toString().trim();
    return result.isEmpty() ? task.toString() : result;
  }

  private String appendJobDetail(Object job, String base, String methodName) {
    if (job == null) {
      return base;
    }
    try {
      var method = job.getClass().getMethod(methodName);
      Object value = method.invoke(job);
      if (value != null) {
        return base + " " + methodName + "=" + value;
      }
    } catch (Exception ignored) {
      // Ignore.
    }
    return base;
  }

  private void clearPermaPending(
      com.minecolonies.api.colony.requestsystem.request.IRequest<?> request) {
    if (request == null || request.getId() == null) {
      return;
    }
    PendingPermaRequest pending = permaPendingRequests.remove(request.getId());
    if (pending == null) {
      return;
    }
    permaPendingCounts.merge(pending.itemId, -pending.count, Integer::sum);
    if (permaPendingCounts.getOrDefault(pending.itemId, 0) <= 0) {
      permaPendingCounts.remove(pending.itemId);
    }
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
    permaOres.clear();
    if (compound.contains(TAG_PERMA_ORES)) {
      var list = compound.getList(TAG_PERMA_ORES, net.minecraft.nbt.Tag.TAG_STRING);
      for (int i = 0; i < list.size(); i++) {
        String key = list.getString(i);
        if (key == null || key.isBlank()) {
          continue;
        }
        ResourceLocation id = ResourceLocation.tryParse(key);
        if (id != null) {
          permaOres.add(id);
        }
      }
    }
    permaWaitFullStack = compound.getBoolean(TAG_PERMA_WAIT_FULL);
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
    if (!permaOres.isEmpty()) {
      net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
      for (ResourceLocation id : permaOres) {
        list.add(net.minecraft.nbt.StringTag.valueOf(id.toString()));
      }
      tag.put(TAG_PERMA_ORES, list);
    }
    if (permaWaitFullStack) {
      tag.putBoolean(TAG_PERMA_WAIT_FULL, true);
    }
    if (builderHutPos != null) {
      tag.putLong(TAG_BUILDER_HUT_POS, builderHutPos.asLong());
    }
    return tag;
  }

  private static final class PendingPermaRequest {
    private final ResourceLocation itemId;
    private final int count;

    private PendingPermaRequest(ResourceLocation itemId, int count) {
      this.itemId = itemId;
      this.count = count;
    }
  }
}
