package com.thesettler_x_create.init;

import com.thesettler_x_create.TheSettlerXCreate;
import com.thesettler_x_create.item.StockLinkLinkerItem;
import com.thesettler_x_create.init.ModBlocks;
import com.minecolonies.api.items.ItemBlockHut;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredItem;

public final class ModItems {
    private ModItems() {
    }

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(TheSettlerXCreate.MODID);
    public static final DeferredItem<Item> NETWORK_LINK_TUNER =
            ITEMS.register("network_link_tuner", () -> new StockLinkLinkerItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> HUT_CREATE_SHOP =
            ITEMS.register("blockhutcreateshop", () -> new ItemBlockHut(ModBlocks.HUT_CREATE_SHOP.get(), new Item.Properties()));
    public static final DeferredItem<Item> CREATE_SHOP_PICKUP =
            ITEMS.register("create_shop_pickup", () -> new BlockItem(ModBlocks.CREATE_SHOP_PICKUP.get(), new Item.Properties()));
    public static final DeferredItem<Item> CREATE_SHOP_OUTPUT =
            ITEMS.register("create_shop_output", () -> new BlockItem(ModBlocks.CREATE_SHOP_OUTPUT.get(), new Item.Properties()));

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
    }
}
