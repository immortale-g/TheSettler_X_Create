package com.thesettler_x_create.init;

import com.thesettler_x_create.TheSettlerXCreate;
import com.thesettler_x_create.block.CreateShopBlock;
import com.thesettler_x_create.minecolonies.block.BlockHutCreateShop;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

public final class ModBlocks {
    private ModBlocks() {
    }

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(TheSettlerXCreate.MODID);
    public static final DeferredBlock<BlockHutCreateShop> HUT_CREATE_SHOP =
            BLOCKS.register("blockhutcreateshop", BlockHutCreateShop::new);
    public static final DeferredBlock<CreateShopBlock> CREATE_SHOP_PICKUP =
            BLOCKS.register("create_shop_pickup", () ->
                    new CreateShopBlock(BlockBehaviour.Properties.of()
                            .noCollission()
                            .noOcclusion()
                            .strength(-1.0f, 3600000.0f)));

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
    }
}
