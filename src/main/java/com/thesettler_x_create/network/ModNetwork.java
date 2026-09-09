package com.thesettler_x_create.network;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.permissions.Action;
import com.minecolonies.core.network.messages.client.colony.ColonyViewBuildingViewMessage;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelPosition;
import com.thesettler_x_create.TheSettlerXCreate;
import com.thesettler_x_create.blockentity.ColonyGaugeBehaviour;
import com.thesettler_x_create.blockentity.ColonyGaugeBlockEntity;
import com.thesettler_x_create.create.CreateLogisticsBridge;
import com.thesettler_x_create.minecolonies.building.BuildingCreateShop;
import com.thesettler_x_create.minecolonies.tileentity.TileEntityCreateShop;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ModNetwork {
  private ModNetwork() {}

  /**
   * Shared max length for this mod's own address fields (Create Shop, Packager). Not used by the
   * Colony Gauge's address field - that one reuses Create's own {@code AddressEditBox} widget,
   * which hardcodes a 25-character max length matching Create's package-address convention, so
   * {@code ColonyGaugeConfigPacket} intentionally keeps its own, smaller constant instead of
   * sharing this one.
   */
  public static final int SHOP_ADDRESS_MAX_LENGTH = 64;

  public static void registerPayloads(RegisterPayloadHandlersEvent event) {
    PayloadRegistrar registrar = event.registrar(TheSettlerXCreate.MODID).versioned("1");
    registrar.playToServer(
        SetCreateShopAddressPayload.TYPE,
        SetCreateShopAddressPayload.STREAM_CODEC,
        ModNetwork::handleSetAddress);
    registrar.playToServer(
        SetCreateShopPermaOrePayload.TYPE,
        SetCreateShopPermaOrePayload.STREAM_CODEC,
        ModNetwork::handleSetPermaOre);
    registrar.playToServer(
        SetCreateShopPermaWaitPayload.TYPE,
        SetCreateShopPermaWaitPayload.STREAM_CODEC,
        ModNetwork::handleSetPermaWait);
    registrar.playToServer(
        CreateShopTestRequestPayload.TYPE,
        CreateShopTestRequestPayload.STREAM_CODEC,
        ModNetwork::handleTestRequest);
    registrar.playToServer(
        CreateShopBatchRequestPayload.TYPE,
        CreateShopBatchRequestPayload.STREAM_CODEC,
        ModNetwork::handleBatchRequest);
    registrar.playToServer(
        CreateShopStockRefreshPayload.TYPE,
        CreateShopStockRefreshPayload.STREAM_CODEC,
        ModNetwork::handleStockRefresh);
    registrar.playToServer(
        SetPackagerAddressPayload.TYPE,
        SetPackagerAddressPayload.STREAM_CODEC,
        ModNetwork::handleSetPackagerAddress);
    registrar.playToServer(
        ColonyGaugeConfigPacket.TYPE,
        ColonyGaugeConfigPacket.STREAM_CODEC,
        ModNetwork::handleColonyGaugeConfig);
  }

  private static void handleSetAddress(
      SetCreateShopAddressPayload payload, IPayloadContext context) {
    context.enqueueWork(
        () -> {
          TileEntityCreateShop shop = getShop(context, payload.pos());
          if (shop == null) {
            return;
          }

          String address = payload.address() == null ? "" : payload.address();
          shop.setShopAddress(address);
          BlockState state = shop.getBlockState();
          shop.getLevel().sendBlockUpdated(payload.pos(), state, state, 3);
        });
  }

  private static void handleSetPermaOre(
      SetCreateShopPermaOrePayload payload, IPayloadContext context) {
    context.enqueueWork(
        () -> {
          TileEntityCreateShop shop = getShop(context, payload.pos());
          if (shop == null || !(shop.getBuilding() instanceof BuildingCreateShop building)) {
            return;
          }
          building.setPermaOre(payload.oreId(), payload.enabled());
        });
  }

  private static void handleSetPermaWait(
      SetCreateShopPermaWaitPayload payload, IPayloadContext context) {
    context.enqueueWork(
        () -> {
          TileEntityCreateShop shop = getShop(context, payload.pos());
          if (shop == null || !(shop.getBuilding() instanceof BuildingCreateShop building)) {
            return;
          }
          building.setPermaWaitFullStack(payload.enabled());
        });
  }

  private static void handleTestRequest(
      CreateShopTestRequestPayload payload, IPayloadContext context) {
    context.enqueueWork(
        () -> {
          TileEntityCreateShop shop = getShop(context, payload.pos());
          if (shop == null) {
            return;
          }

          UUID networkId = shop.getStockNetworkId();
          if (networkId == null) {
            return;
          }

          if (payload.stack().isEmpty()) {
            return;
          }

          int amount = Math.max(1, payload.amount());
          BigItemStack request = new BigItemStack(payload.stack().copy(), amount);
          CreateLogisticsBridge.broadcastPackageRequest(
              networkId, List.of(request), shop.getShopAddress());
        });
  }

  private static void handleBatchRequest(
      CreateShopBatchRequestPayload payload, IPayloadContext context) {
    context.enqueueWork(
        () -> {
          TileEntityCreateShop shop = getShop(context, payload.pos());
          if (shop == null) {
            return;
          }

          UUID networkId = shop.getStockNetworkId();
          if (networkId == null) {
            return;
          }

          if (payload.stacks() == null || payload.stacks().isEmpty()) {
            return;
          }

          List<BigItemStack> orderStacks =
              payload.stacks().stream()
                  .filter(
                      stack ->
                          stack != null
                              && stack.stack != null
                              && !stack.stack.isEmpty()
                              && stack.count > 0)
                  .toList();

          if (orderStacks.isEmpty()) {
            return;
          }

          CreateLogisticsBridge.broadcastPackageRequest(
              networkId, orderStacks, shop.getShopAddress());
        });
  }

  private static void handleStockRefresh(
      CreateShopStockRefreshPayload payload, IPayloadContext context) {
    context.enqueueWork(
        () -> {
          if (!(context.player() instanceof ServerPlayer player)) {
            return;
          }
          TileEntityCreateShop shop = getShop(player, payload.pos());
          if (shop == null) {
            return;
          }
          if (shop.getBuilding() == null) {
            return;
          }
          new ColonyViewBuildingViewMessage(shop.getBuilding(), true).sendToPlayer(player);
        });
  }

  private static void handleSetPackagerAddress(
      SetPackagerAddressPayload payload, IPayloadContext context) {
    context.enqueueWork(
        () -> {
          TileEntityCreateShop shop = getShop(context, payload.hutPos());
          if (shop == null || !(shop.getBuilding() instanceof BuildingCreateShop building)) {
            return;
          }
          com.thesettler_x_create.blockentity.CreateShopOutputBlockEntity obe =
              building.getOutputBlockEntity();
          if (obe != null) {
            obe.setPackageAddress(payload.address());
          }
        });
  }

  private static void handleColonyGaugeConfig(
      ColonyGaugeConfigPacket payload, IPayloadContext context) {
    context.enqueueWork(
        () -> {
          boolean debug = com.thesettler_x_create.Config.DEBUG_LOGGING.getAsBoolean();
          if (!(context.player() instanceof ServerPlayer player)) {
            if (debug) {
              TheSettlerXCreate.LOGGER.info(
                  "[ColonyGauge] config packet skip reason=no-server-player");
            }
            return;
          }
          FactoryPanelPosition position = payload.position();
          if (!isAuthorized(player, position.pos())) {
            if (debug) {
              TheSettlerXCreate.LOGGER.info(
                  "[ColonyGauge] config packet skip reason=not-authorized pos={}", position.pos());
            }
            return;
          }
          if (!(player.level().getBlockEntity(position.pos())
              instanceof ColonyGaugeBlockEntity be)) {
            if (debug) {
              TheSettlerXCreate.LOGGER.info(
                  "[ColonyGauge] config packet skip reason=no-block-entity pos={}", position.pos());
            }
            return;
          }
          ColonyGaugeBehaviour behaviour = be.panels.get(position.slot());
          if (behaviour == null || !behaviour.isActive()) {
            if (debug) {
              TheSettlerXCreate.LOGGER.info(
                  "[ColonyGauge] config packet skip reason=behaviour-inactive-or-null pos={} slot={}",
                  position.pos(),
                  position.slot());
            }
            return;
          }
          if (payload.reset()) {
            behaviour.resetFilter();
            if (debug) {
              TheSettlerXCreate.LOGGER.info(
                  "[ColonyGauge] config packet applied reset pos={} slot={}",
                  position.pos(),
                  position.slot());
            }
            return;
          }
          behaviour.setManualAddress(payload.address());
          behaviour.setPromiseClearingInterval(payload.promiseClearingInterval());
          if (payload.clearPromises()) {
            behaviour.forceClearPromises();
          }
          if (debug) {
            TheSettlerXCreate.LOGGER.info(
                "[ColonyGauge] config packet applied address='{}' promiseClearingInterval={} clearPromises={} pos={} slot={} -> manualAddress={}",
                payload.address(),
                payload.promiseClearingInterval(),
                payload.clearPromises(),
                position.pos(),
                position.slot(),
                behaviour.manualAddress);
          }
        });
  }

  private static TileEntityCreateShop getShop(IPayloadContext context, BlockPos pos) {
    if (!(context.player() instanceof ServerPlayer player)) {
      return null;
    }
    return getShop(player, pos);
  }

  private static TileEntityCreateShop getShop(ServerPlayer player, BlockPos pos) {
    if (!isAuthorized(player, pos)) {
      return null;
    }
    BlockEntity be = player.level().getBlockEntity(pos);
    return be instanceof TileEntityCreateShop shop ? shop : null;
  }

  /**
   * Requires the sending player to be a member of the colony that owns the building at {@code pos}
   * with permission to manage huts, so a crafted packet naming an arbitrary {@link BlockPos} cannot
   * reconfigure or drain a building the player has no relation to.
   */
  private static boolean isAuthorized(ServerPlayer player, BlockPos pos) {
    IColony colony = IColonyManager.getInstance().getColonyByPosFromWorld(player.level(), pos);
    return colony != null && colony.getPermissions().hasPermission(player, Action.MANAGE_HUTS);
  }
}
