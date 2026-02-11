package com.thesettler_x_create.minecolonies.module;

import com.minecolonies.api.colony.buildings.modules.AbstractBuildingModule;
import com.thesettler_x_create.minecolonies.building.BuildingCreateShop;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public class CreateShopPermaModule extends AbstractBuildingModule {
    @Override
    public void serializeToView(RegistryFriendlyByteBuf buf) {
        BuildingCreateShop shop = getCreateShop();
        boolean enabled = shop != null && shop.canUsePermaRequests();
        boolean waitFull = shop != null && shop.isPermaWaitFullStack();
        buf.writeBoolean(enabled);
        buf.writeBoolean(waitFull);

        List<ItemStack> ores = BuildingCreateShop.getOreCandidates();
        buf.writeVarInt(ores.size());
        for (ItemStack stack : ores) {
            ItemStack.STREAM_CODEC.encode(buf, stack);
            boolean selected = shop != null && shop.getPermaOres().contains(
                    net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()));
            buf.writeBoolean(selected);
        }
    }

    private BuildingCreateShop getCreateShop() {
        return building instanceof BuildingCreateShop shop ? shop : null;
    }
}
