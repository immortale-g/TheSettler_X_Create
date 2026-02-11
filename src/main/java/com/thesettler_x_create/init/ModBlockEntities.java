package com.thesettler_x_create.init;

import com.thesettler_x_create.TheSettlerXCreate;
import com.thesettler_x_create.blockentity.CreateShopBlockEntity;
import com.thesettler_x_create.blockentity.CreateShopOutputBlockEntity;
import com.thesettler_x_create.minecolonies.tileentity.TileEntityCreateShop;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {
    private ModBlockEntities() {
    }

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, TheSettlerXCreate.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TileEntityCreateShop>> CREATE_SHOP_BUILDING =
            BLOCK_ENTITIES.register("create_shop_colonybuilding",
                    () -> BlockEntityType.Builder.of(TileEntityCreateShop::new, ModBlocks.HUT_CREATE_SHOP.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CreateShopBlockEntity>> CREATE_SHOP_PICKUP =
            BLOCK_ENTITIES.register("create_shop_pickup",
                    () -> BlockEntityType.Builder.of(CreateShopBlockEntity::new, ModBlocks.CREATE_SHOP_PICKUP.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CreateShopOutputBlockEntity>> CREATE_SHOP_OUTPUT =
            BLOCK_ENTITIES.register("create_shop_output",
                    () -> BlockEntityType.Builder.of(CreateShopOutputBlockEntity::new, ModBlocks.CREATE_SHOP_OUTPUT.get()).build(null));

    public static void register(IEventBus bus) {
        BLOCK_ENTITIES.register(bus);
    }
}
