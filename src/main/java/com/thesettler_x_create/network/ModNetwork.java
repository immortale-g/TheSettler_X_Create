package com.thesettler_x_create.network;

import com.thesettler_x_create.TheSettlerXCreate;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBehaviour;
import com.simibubi.create.content.logistics.packagerLink.LogisticsManager;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;
import com.minecolonies.core.network.messages.client.colony.ColonyViewBuildingViewMessage;
import com.thesettler_x_create.minecolonies.tileentity.TileEntityCreateShop;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.UUID;

public final class ModNetwork {
    private ModNetwork() {
    }

    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(TheSettlerXCreate.MODID).versioned("1");
        registrar.playToServer(SetCreateShopAddressPayload.TYPE, SetCreateShopAddressPayload.STREAM_CODEC, ModNetwork::handleSetAddress);
        registrar.playToServer(CreateShopTestRequestPayload.TYPE, CreateShopTestRequestPayload.STREAM_CODEC, ModNetwork::handleTestRequest);
        registrar.playToServer(CreateShopBatchRequestPayload.TYPE, CreateShopBatchRequestPayload.STREAM_CODEC, ModNetwork::handleBatchRequest);
        registrar.playToServer(CreateShopStockRefreshPayload.TYPE, CreateShopStockRefreshPayload.STREAM_CODEC, ModNetwork::handleStockRefresh);
    }

    private static void handleSetAddress(SetCreateShopAddressPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
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

    private static void handleTestRequest(CreateShopTestRequestPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
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
            PackageOrderWithCrafts order = PackageOrderWithCrafts.simple(List.of(request));
            LogisticsManager.broadcastPackageRequest(
                    networkId,
                    LogisticallyLinkedBehaviour.RequestType.PLAYER,
                    order,
                    null,
                    shop.getShopAddress()
            );
        });
    }

    private static void handleBatchRequest(CreateShopBatchRequestPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
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

            List<BigItemStack> orderStacks = payload.stacks().stream()
                    .filter(stack -> stack != null && stack.stack != null && !stack.stack.isEmpty() && stack.count > 0)
                    .toList();

            if (orderStacks.isEmpty()) {
                return;
            }

            PackageOrderWithCrafts order = PackageOrderWithCrafts.simple(orderStacks);
            LogisticsManager.broadcastPackageRequest(
                    networkId,
                    LogisticallyLinkedBehaviour.RequestType.PLAYER,
                    order,
                    null,
                    shop.getShopAddress()
            );
        });
    }

    private static void handleStockRefresh(CreateShopStockRefreshPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
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

    private static TileEntityCreateShop getShop(IPayloadContext context, BlockPos pos) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return null;
        }
        return getShop(player, pos);
    }

    private static TileEntityCreateShop getShop(ServerPlayer player, BlockPos pos) {
        BlockEntity be = player.level().getBlockEntity(pos);
        return be instanceof TileEntityCreateShop shop ? shop : null;
    }
}
