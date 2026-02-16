package com.thesettler_x_create.minecolonies.building;

import com.ldtteam.structurize.api.RotationMirror;
import com.ldtteam.structurize.blueprints.v1.Blueprint;
import com.ldtteam.structurize.blueprints.v1.BlueprintUtil;
import com.ldtteam.structurize.util.BlockInfo;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.core.colony.jobs.JobBuilder;
import com.thesettler_x_create.TheSettlerXCreate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/** Belt blueprint placement and belt item consumption for the Create Shop. */
final class ShopBeltBlueprints {
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

  private final BuildingCreateShop shop;

  ShopBeltBlueprints(BuildingCreateShop shop) {
    this.shop = shop;
  }

  static ResourceLocation beltItemId() {
    return BELT_ITEM_ID;
  }

  private void logBelt(String message, Object... args) {
    if (!BuildingCreateShop.isDebugRequests()) {
      return;
    }
    TheSettlerXCreate.LOGGER.info("[CreateShop] belt: " + message, args);
  }

  boolean trySpawnBeltBlueprint(IColony colony) {
    int targetLevel = Math.max(1, shop.getBuildingLevel());
    logBelt(
        "trySpawn start: targetLevel={} built={} level={} loc={}",
        targetLevel,
        shop.isBuilt(),
        shop.getBuildingLevel(),
        shop.getLocation().getInDimensionLocation());
    if (colony == null || !shop.isBuilt()) {
      logBelt(
          "trySpawn early-exit: targetLevel={} colonyNull={} built={}",
          targetLevel,
          colony == null,
          shop.isBuilt());
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
        findWorldAnchorShafts(serverLevel, shop.getLocation().getInDimensionLocation(), 10);
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
      if (BuildingCreateShop.isDebugRequests()) {
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
    int level = Math.max(1, shop.getBuildingLevel());
    if (level >= 2) {
      return "blueprints/"
          + TheSettlerXCreate.MODID
          + "/craftsmanship/storage/createshop2.blueprint";
    }
    return "blueprints/" + TheSettlerXCreate.MODID + "/craftsmanship/storage/createshop1.blueprint";
  }

  @Nullable
  private ResourceLocation getBeltBlueprintPath() {
    int level = Math.max(1, shop.getBuildingLevel());
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
    RotationMirror rotationMirror = shop.getRotationMirror();
    if (rotationMirror == null) {
      rotationMirror = RotationMirror.NONE;
    }
    BlockPos baseOffset = basePrimaryOffset == null ? BlockPos.ZERO : basePrimaryOffset;
    BlockPos beltOffset = beltPrimaryOffset == null ? BlockPos.ZERO : beltPrimaryOffset;
    BlockPos adjust = baseOffset.subtract(beltOffset);
    BlockPos buildingPos = shop.getLocation().getInDimensionLocation();
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
    RotationMirror rotationMirror = shop.getRotationMirror();
    if (rotationMirror == null) {
      rotationMirror = RotationMirror.NONE;
    }
    BlockPos beltOffset = beltPrimaryOffset == null ? BlockPos.ZERO : beltPrimaryOffset;
    BlockPos hutPos = shop.getPosition();
    if (hutPos == null) {
      hutPos = shop.getLocation().getInDimensionLocation();
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
    BlockPos builderHutPos = shop.getBuilderHutPos();
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
    BlockPos location = shop.getLocation().getInDimensionLocation();
    for (var order : workOrders) {
      if (order == null || order.getLocation() == null) {
        continue;
      }
      if (!order.getLocation().equals(location)) {
        continue;
      }
      if (order.isClaimed() && order.getClaimedBy() != null) {
        shop.setBuilderHutPos(order.getClaimedBy());
        logBelt("update builder hut: claimed by {}", shop.getBuilderHutPos());
        return;
      }
    }
    logBelt("update builder hut: no claimed work order for {}", location);
  }

  private boolean removeOneFromHandler(IItemHandler handler, Item item) {
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
}
