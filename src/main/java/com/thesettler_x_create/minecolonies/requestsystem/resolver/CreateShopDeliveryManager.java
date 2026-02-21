package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import com.google.common.collect.Lists;
import com.minecolonies.api.colony.buildings.workerbuildings.IWareHouse;
import com.minecolonies.api.colony.requestsystem.location.ILocation;
import com.minecolonies.api.colony.requestsystem.manager.IRequestManager;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.requestable.IDeliverable;
import com.minecolonies.api.colony.requestsystem.requestable.deliveryman.AbstractDeliverymanRequestable;
import com.minecolonies.api.colony.requestsystem.requestable.deliveryman.Delivery;
import com.minecolonies.api.colony.requestsystem.requester.IRequester;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.api.tileentities.AbstractTileEntityRack;
import com.minecolonies.api.util.WorldUtil;
import com.minecolonies.api.util.constant.TypeConstants;
import com.minecolonies.core.colony.buildings.modules.AbstractAssignedCitizenModule;
import com.minecolonies.core.colony.buildings.modules.BuildingModules;
import com.minecolonies.core.colony.buildings.modules.CourierAssignmentModule;
import com.minecolonies.core.colony.buildings.modules.DeliverymanAssignmentModule;
import com.minecolonies.core.colony.buildings.modules.WarehouseRequestQueueModule;
import com.minecolonies.core.colony.requestsystem.management.IStandardRequestManager;
import com.thesettler_x_create.Config;
import com.thesettler_x_create.TheSettlerXCreate;
import com.thesettler_x_create.blockentity.CreateShopBlockEntity;
import com.thesettler_x_create.minecolonies.building.BuildingCreateShop;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.IItemHandler;

/** Delivery creation and courier enqueue helpers for Create Shop resolver. */
final class CreateShopDeliveryManager {
  private final CreateShopRequestResolver resolver;

  CreateShopDeliveryManager(CreateShopRequestResolver resolver) {
    this.resolver = resolver;
  }

  List<IToken<?>> createDeliveriesFromStacks(
      IRequestManager manager,
      IRequest<?> request,
      List<com.minecolonies.api.util.Tuple<ItemStack, BlockPos>> stacks,
      CreateShopBlockEntity pickup,
      BuildingCreateShop shop) {
    if (manager == null || pickup == null || stacks == null) {
      return Lists.newArrayList();
    }
    BlockPos startPos = pickup.getBlockPos();
    ItemStack selected = null;
    for (var entry : stacks) {
      if (entry == null) {
        continue;
      }
      ItemStack stack = entry.getA();
      if (stack.isEmpty()) {
        continue;
      }
      selected = stack.copy();
      if (entry.getB() != null) {
        startPos = entry.getB();
      }
      break;
    }
    if (selected == null) {
      return Lists.newArrayList();
    }
    var factory = manager.getFactoryController();
    Level pickupLevel = pickup.getLevel();
    if (pickupLevel == null) {
      return Lists.newArrayList();
    }
    var requester = request.getRequester();
    ILocation targetLocation = requester == null ? null : requester.getLocation();
    if (targetLocation == null) {
      return Lists.newArrayList();
    }
    if (isSelfLoopDeliveryTarget(pickupLevel, startPos, targetLocation)) {
      if (Config.DEBUG_LOGGING.getAsBoolean()) {
        TheSettlerXCreate.LOGGER.info(
            "[CreateShop] skip delivery create self-loop start={} target={} request={}",
            startPos,
            targetLocation.getInDimensionLocation(),
            request.getId());
      }
      return Lists.newArrayList();
    }
    ILocation pickupLocation =
        factory.getNewInstance(TypeConstants.ILOCATION, startPos, pickupLevel.dimension());
    if (Config.DEBUG_LOGGING.getAsBoolean()) {
      try {
        var targetPos = targetLocation.getInDimensionLocation();
        var pickupBlock = pickupLevel.getBlockState(startPos).getBlock();
        var targetBlock = pickupLevel.getBlockState(targetPos).getBlock();
        TheSettlerXCreate.LOGGER.info(
            "[CreateShop] delivery location pickupPosition={} pickupBlock={} targetPosition={} targetBlock={}",
            startPos,
            pickupBlock,
            targetPos,
            targetBlock);
      } catch (Exception ignored) {
        // Do not fail delivery creation for debug output.
      }
    }
    if (resolver.hasDeliveriesCreated(request.getId())) {
      return Lists.newArrayList();
    }
    Delivery delivery =
        new Delivery(
            pickupLocation,
            targetLocation,
            selected.copy(),
            AbstractDeliverymanRequestable.getDefaultDeliveryPriority(true));
    IRequester deliveryRequester = resolveDeliveryRequester(manager, request);
    IToken<?> token;
    try {
      token = manager.createRequest(deliveryRequester, delivery);
    } catch (Exception ex) {
      if (Config.DEBUG_LOGGING.getAsBoolean()) {
        TheSettlerXCreate.LOGGER.info(
            "[CreateShop] delivery create failed requester={} parentRequester={} error={}",
            deliveryRequester == null ? "<null>" : deliveryRequester.getClass().getName(),
            requester == null ? "<null>" : requester.getClass().getName(),
            ex.getMessage() == null ? "<null>" : ex.getMessage());
      }
      return Lists.newArrayList();
    }
    try {
      boolean alreadyLinked = request.getChildren().contains(token);
      if (!alreadyLinked) {
        request.addChild(token);
      }
      int duplicateLinksRemoved = 0;
      boolean seenNewChild = false;
      for (IToken<?> childToken : java.util.List.copyOf(request.getChildren())) {
        if (!token.equals(childToken)) {
          continue;
        }
        if (!seenNewChild) {
          seenNewChild = true;
          continue;
        }
        request.removeChild(childToken);
        duplicateLinksRemoved++;
      }
      IRequest<?> child = manager.getRequestForToken(token);
      if (child != null) {
        child.setParent(request.getId());
      }
      if (duplicateLinksRemoved > 0 && Config.DEBUG_LOGGING.getAsBoolean()) {
        TheSettlerXCreate.LOGGER.info(
            "[CreateShop] delivery link dedupe parent={} child={} removedDuplicates={}",
            request.getId(),
            token,
            duplicateLinksRemoved);
      }
      if (alreadyLinked && Config.DEBUG_LOGGING.getAsBoolean()) {
        TheSettlerXCreate.LOGGER.info(
            "[CreateShop] delivery link exists parent={} child={} skipAddChild=true",
            request.getId(),
            token);
      }
    } catch (Exception ex) {
      try {
        manager.updateRequestState(
            token, com.minecolonies.api.colony.requestsystem.request.RequestState.CANCELLED);
      } catch (Exception ignored) {
        // Best-effort rollback only.
      }
      if (Config.DEBUG_LOGGING.getAsBoolean()) {
        TheSettlerXCreate.LOGGER.info(
            "[CreateShop] delivery link failed parent={} child={} error={}",
            request.getId(),
            token,
            ex.getMessage() == null ? "<null>" : ex.getMessage());
      }
      return Lists.newArrayList();
    }
    request.addDelivery(selected.copy());
    resolver.markDeliveriesCreated(request.getId());
    if (Config.DEBUG_LOGGING.getAsBoolean()) {
      String key = token.toString();
      if (resolver.getDeliveryCreateLogged().add(key)) {
        TheSettlerXCreate.LOGGER.info(
            "[CreateShop] delivery create token={} requesterClass={} parentRequesterClass={} managerClass={}",
            token,
            deliveryRequester == null ? "<null>" : deliveryRequester.getClass().getName(),
            requester == null ? "<null>" : requester.getClass().getName(),
            manager.getClass().getName());
        IStandardRequestManager standard = CreateShopRequestResolver.unwrapStandardManager(manager);
        if (standard != null && standard.getRequestHandler() != null) {
          try {
            IRequest<?> created = manager.getRequestForToken(token);
            if (created != null) {
              String createdRequester = created.getRequester().getClass().getName();
              IToken<?> childParent = created.getParent();
              TheSettlerXCreate.LOGGER.info(
                  "[CreateShop] delivery create handler token={} createdRequester={} childParent={}",
                  token,
                  createdRequester,
                  childParent == null ? "<none>" : childParent);
            } else {
              TheSettlerXCreate.LOGGER.info(
                  "[CreateShop] delivery create handler token={} created=<null>", token);
            }
          } catch (Exception ex) {
            TheSettlerXCreate.LOGGER.info(
                "[CreateShop] delivery create handler token={} error={}",
                token,
                ex.getMessage() == null ? "<null>" : ex.getMessage());
          }
        } else {
          TheSettlerXCreate.LOGGER.info(
              "[CreateShop] delivery create handler unavailable token={} unwrappedManager={}",
              token,
              standard == null ? "<null>" : "ok");
        }
      }
    }
    boolean enqueued = tryEnqueueDelivery(manager, token);
    if (Config.DEBUG_LOGGING.getAsBoolean()) {
      TheSettlerXCreate.LOGGER.info(
          "[CreateShop] delivery native dispatch token={} viaWarehouseQueue={}",
          token,
          enqueued ? "ok" : "none");
    }
    if (Config.DEBUG_LOGGING.getAsBoolean()) {
      IDeliverable deliverable = request.getRequest() instanceof IDeliverable typed ? typed : null;
      int reservedForDeliverable =
          deliverable == null ? 0 : pickup.getReservedForDeliverable(deliverable);
      logDeliveryDiagnostics(
          "create",
          manager,
          token,
          resolver.toRequestId(request.getId()),
          startPos,
          selected.copy(),
          request.getRequester().getLocation(),
          pickup.getReservedForRequest(resolver.toRequestId(request.getId())),
          reservedForDeliverable,
          pickup.getReservedFor(selected));
      IStandardRequestManager standardManager =
          CreateShopRequestResolver.unwrapStandardManager(manager);
      if (standardManager != null) {
        resolver.logDeliveryLinkStateForOps("create", standardManager, request.getId(), token);
      }
    }
    notifyDeliverymen(manager, token, shop);
    return Lists.newArrayList(token);
  }

  private IRequester resolveDeliveryRequester(IRequestManager manager, IRequest<?> parentRequest) {
    if (manager instanceof IStandardRequestManager standard) {
      try {
        var owner = standard.getResolverHandler().getResolverForRequest(parentRequest);
        if (owner instanceof IRequester requester
            && owner.getClass().getSimpleName().contains("WarehouseConcreteRequestResolver")) {
          return requester;
        }
      } catch (Exception ignored) {
        // Best effort.
      }
      try {
        var typeStore = standard.getRequestableTypeRequestResolverAssignmentDataStore();
        var assignments = typeStore == null ? null : typeStore.getAssignments();
        var requestableResolvers =
            assignments == null ? null : assignments.get(TypeConstants.REQUESTABLE);
        if (requestableResolvers != null) {
          for (IToken<?> resolverToken : requestableResolvers) {
            try {
              var candidate = standard.getResolverHandler().getResolver(resolverToken);
              if (candidate instanceof IRequester requester
                  && candidate
                      .getClass()
                      .getSimpleName()
                      .contains("WarehouseConcreteRequestResolver")) {
                return requester;
              }
            } catch (Exception ignored) {
              // Ignore stale resolver ids.
            }
          }
        }
      } catch (Exception ignored) {
        // Best effort.
      }
    }
    return resolver;
  }

  static boolean isSelfLoopDeliveryTarget(
      Level pickupLevel, BlockPos startPos, ILocation targetLocation) {
    if (pickupLevel == null || startPos == null || targetLocation == null) {
      return false;
    }
    return pickupLevel.dimension().equals(targetLocation.getDimension())
        && startPos.equals(targetLocation.getInDimensionLocation());
  }

  void logDeliveryDiagnostics(
      String stage,
      IRequestManager manager,
      IToken<?> deliveryToken,
      UUID parentRequestId,
      BlockPos pickupPosition,
      ItemStack stack,
      ILocation targetLocation,
      int reservedForRequest,
      int reservedForDeliverable,
      int reservedForStack) {
    Level level = manager.getColony().getWorld();
    String tokenInfo = deliveryToken == null ? "<null>" : deliveryToken.toString();
    String parentInfo = parentRequestId.toString();
    String itemInfo = stack.isEmpty() ? "<empty>" : stack.getItem().toString();
    int count = stack.getCount();

    boolean pickupLoaded = WorldUtil.isBlockLoaded(level, pickupPosition);
    String pickupBlock =
        pickupLoaded ? level.getBlockState(pickupPosition).getBlock().toString() : "<unloaded>";
    BlockEntity pickupEntity = pickupLoaded ? level.getBlockEntity(pickupPosition) : null;
    String pickupEntityName = pickupEntity == null ? "<none>" : pickupEntity.getClass().getName();
    IItemHandler handler = null;
    if (pickupEntity instanceof CreateShopBlockEntity shopPickup) {
      handler = shopPickup.getItemHandler(null);
    } else if (pickupEntity instanceof AbstractTileEntityRack rack) {
      handler = rack.getItemHandlerCap();
    }
    int slots = handler == null ? -1 : handler.getSlots();
    int matchSlot = -1;
    int simExtract = 0;
    if (handler != null && !stack.isEmpty()) {
      for (int i = 0; i < slots; i++) {
        ItemStack slotStack = handler.getStackInSlot(i);
        if (slotStack.isEmpty() || !ItemStack.isSameItemSameComponents(slotStack, stack)) {
          continue;
        }
        matchSlot = i;
        ItemStack extracted = handler.extractItem(i, stack.getCount(), true);
        simExtract = extracted.getCount();
        break;
      }
    }

    BlockPos targetPosition = targetLocation.getInDimensionLocation();
    boolean targetLoaded = WorldUtil.isBlockLoaded(level, targetPosition);
    String targetBlock =
        targetLoaded ? level.getBlockState(targetPosition).getBlock().toString() : "<unloaded>";
    String reservedDel =
        reservedForDeliverable < 0 ? "<n/a>" : String.valueOf(reservedForDeliverable);

    TheSettlerXCreate.LOGGER.info(
        "[CreateShop] delivery diag {} token={} parent={} item={} count={} pickupPosition={} pickupLoaded={} pickupBlock={} pickupEntity={} handlerSlots={} matchSlot={} simExtract={} reservedReq={} reservedDel={} reservedStack={} targetPosition={} targetLoaded={} targetBlock={}",
        stage,
        tokenInfo,
        parentInfo,
        itemInfo,
        count,
        pickupPosition,
        pickupLoaded,
        pickupBlock,
        pickupEntityName,
        slots,
        matchSlot,
        simExtract,
        reservedForRequest,
        reservedDel,
        reservedForStack,
        targetPosition,
        targetLoaded,
        targetBlock);
  }

  boolean tryEnqueueDelivery(IRequestManager manager, IToken<?> token) {
    if (manager == null || token == null) {
      return false;
    }
    var buildingManager = manager.getColony().getServerBuildingManager();
    if (buildingManager == null) {
      return false;
    }
    int warehousesChecked = 0;
    int warehousesWithCouriers = 0;
    int warehousesWithQueue = 0;
    for (var entry : buildingManager.getBuildings().entrySet()) {
      if (!(entry.getValue() instanceof IWareHouse warehouse)) {
        continue;
      }
      warehousesChecked++;
      CourierAssignmentModule couriers = warehouse.getModule(BuildingModules.WAREHOUSE_COURIERS);
      int courierCount = couriers == null ? 0 : couriers.getAssignedCitizen().size();
      if (courierCount <= 0) {
        continue;
      }
      warehousesWithCouriers++;
      WarehouseRequestQueueModule queue =
          warehouse.getModule(BuildingModules.WAREHOUSE_REQUEST_QUEUE);
      if (queue == null) {
        continue;
      }
      warehousesWithQueue++;
      if (queue.getMutableRequestList().contains(token)) {
        if (Config.DEBUG_LOGGING.getAsBoolean()) {
          TheSettlerXCreate.LOGGER.info(
              "[CreateShop] delivery enqueue token={} warehouse=<known> couriers={} queued=already",
              token,
              courierCount);
        }
        return true;
      }
      queue.addRequest(token);
      if (Config.DEBUG_LOGGING.getAsBoolean()) {
        String warehouseInfo = "<unknown>";
        try {
          var getLocation = warehouse.getClass().getMethod("getLocation");
          Object location = getLocation.invoke(warehouse);
          if (location != null) {
            warehouseInfo = location.toString();
          }
        } catch (Exception ignored) {
          // Ignore reflection failures.
        }
        TheSettlerXCreate.LOGGER.info(
            "[CreateShop] delivery enqueue token={} warehouse={} couriers={} queued=true",
            token,
            warehouseInfo,
            courierCount);
      }
      return true;
    }
    if (Config.DEBUG_LOGGING.getAsBoolean()) {
      TheSettlerXCreate.LOGGER.info(
          "[CreateShop] delivery enqueue token={} queued=false warehousesChecked={} withCouriers={} withQueue={}",
          token,
          warehousesChecked,
          warehousesWithCouriers,
          warehousesWithQueue);
    }
    return false;
  }

  private int notifyDeliverymen(IRequestManager manager, IToken<?> token, BuildingCreateShop shop) {
    if (manager == null || token == null) {
      return 0;
    }
    int notified = 0;
    int checked = 0;
    if (shop != null) {
      CourierAssignmentModule module = shop.getModule(BuildingModules.WAREHOUSE_COURIERS);
      if (module != null) {
        checked += notifyCourierModule(module, token);
      }
      if (notified > 0) {
        if (Config.DEBUG_LOGGING.getAsBoolean()) {
          TheSettlerXCreate.LOGGER.info(
              "[CreateShop] delivery notify shop couriers token={} checked={} notified={}",
              token,
              checked,
              notified);
        }
        return notified;
      }
    }
    var colony = manager.getColony();
    if (colony == null) {
      return notified;
    }
    var buildingManager = colony.getServerBuildingManager();
    if (buildingManager == null) {
      return notified;
    }
    for (var entry : buildingManager.getBuildings().entrySet()) {
      var building = entry.getValue();
      if (building == null) {
        continue;
      }
      CourierAssignmentModule warehouseCouriers =
          building.getModule(BuildingModules.WAREHOUSE_COURIERS);
      if (warehouseCouriers != null) {
        checked += notifyCourierModule(warehouseCouriers, token);
      }
      DeliverymanAssignmentModule deliverymanModule =
          building.getModule(BuildingModules.COURIER_WORK);
      if (deliverymanModule != null) {
        checked += notifyCourierModule(deliverymanModule, token);
      }
    }
    if (Config.DEBUG_LOGGING.getAsBoolean()) {
      TheSettlerXCreate.LOGGER.info(
          "[CreateShop] delivery notify couriers token={} checked={} notified={}",
          token,
          checked,
          notified);
    }
    return notified;
  }

  private int notifyCourierModule(AbstractAssignedCitizenModule module, IToken<?> token) {
    if (module == null) {
      return 0;
    }
    var citizens = module.getAssignedCitizen();
    return citizens == null ? 0 : citizens.size();
  }
}
