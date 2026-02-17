package com.thesettler_x_create.minecolonies.building;

import com.google.common.collect.ImmutableCollection;
import com.minecolonies.api.blocks.AbstractBlockMinecoloniesRack;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.modules.IBuildingModule;
import com.minecolonies.api.colony.buildings.modules.IBuildingModuleView;
import com.minecolonies.api.colony.buildings.workerbuildings.IWareHouse;
import com.minecolonies.api.colony.requestsystem.resolver.IRequestResolver;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.api.tileentities.AbstractTileEntityWareHouse;
import com.minecolonies.core.colony.buildings.AbstractBuilding;
import com.minecolonies.core.colony.buildings.modules.BuildingModules;
import com.minecolonies.core.colony.buildings.modules.CourierAssignmentModule;
import com.minecolonies.core.tileentities.TileEntityRack;
import com.thesettler_x_create.Config;
import com.thesettler_x_create.block.CreateShopBlock;
import com.thesettler_x_create.block.CreateShopOutputBlock;
import com.thesettler_x_create.blockentity.CreateShopBlockEntity;
import com.thesettler_x_create.blockentity.CreateShopOutputBlockEntity;
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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Tuple;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

/** Create Shop building integration with MineColonies request system and Create network. */
public class BuildingCreateShop extends AbstractBuilding implements IWareHouse {
  public static final String SCHEMATIC_NAME = "createshop";

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
    return workerStatus.hasActiveWorker();
  }

  public boolean isWorkerWorking() {
    return workerStatus.isWorkerWorking();
  }

  public void notifyMissingNetwork() {
    networkNotifier.notifyMissingNetwork();
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
    resolverAssignments.ensureDeliverableAssignment();
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
