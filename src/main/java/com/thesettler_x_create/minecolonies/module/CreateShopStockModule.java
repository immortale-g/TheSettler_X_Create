package com.thesettler_x_create.minecolonies.module;

import com.minecolonies.api.colony.buildings.modules.AbstractBuildingModule;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.packager.InventorySummary;
import com.simibubi.create.content.logistics.packagerLink.LogisticsManager;
import com.thesettler_x_create.minecolonies.tileentity.TileEntityCreateShop;
import net.minecraft.network.RegistryFriendlyByteBuf;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class CreateShopStockModule extends AbstractBuildingModule {
    @Override
    public void serializeToView(RegistryFriendlyByteBuf buf) {
        TileEntityCreateShop shop = getShopTile();
        UUID networkId = shop == null ? null : shop.getStockNetworkId();
        if (networkId == null) {
            buf.writeBoolean(false);
            buf.writeVarInt(0);
            return;
        }

        buf.writeBoolean(true);
        InventorySummary summary = LogisticsManager.getSummaryOfNetwork(networkId, true);
        List<BigItemStack> stacks = summary == null ? Collections.emptyList() : summary.getStacksByCount();
        buf.writeVarInt(stacks.size());
        for (BigItemStack stack : stacks) {
            BigItemStack.STREAM_CODEC.encode(buf, stack);
        }
    }

    private TileEntityCreateShop getShopTile() {
        if (building == null) {
            return null;
        }
        if (building.getTileEntity() instanceof TileEntityCreateShop shop) {
            return shop;
        }
        return null;
    }
}
