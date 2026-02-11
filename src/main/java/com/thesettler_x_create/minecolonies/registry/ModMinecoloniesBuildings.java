package com.thesettler_x_create.minecolonies.registry;

import com.minecolonies.api.colony.buildings.registry.BuildingEntry;
import com.minecolonies.apiimp.CommonMinecoloniesAPIImpl;
import com.minecolonies.core.colony.buildings.views.EmptyView;
import com.minecolonies.core.colony.buildings.modules.BuildingModules;
import com.minecolonies.core.colony.buildings.modules.WorkerBuildingModule;
import com.minecolonies.core.colony.buildings.moduleviews.WorkerBuildingModuleView;
import com.minecolonies.api.colony.buildings.registry.BuildingEntry.ModuleProducer;
import com.minecolonies.api.blocks.AbstractColonyBlock;
import com.minecolonies.api.entity.citizen.Skill;
import com.thesettler_x_create.TheSettlerXCreate;
import com.thesettler_x_create.init.ModBlocks;
import com.thesettler_x_create.minecolonies.building.BuildingCreateShop;
import com.thesettler_x_create.minecolonies.module.CreateShopAddressModule;
import com.thesettler_x_create.minecolonies.module.CreateShopOutputModule;
import com.thesettler_x_create.minecolonies.module.CreateShopPermaModule;
import com.thesettler_x_create.minecolonies.module.CreateShopStockModule;
import com.thesettler_x_create.minecolonies.moduleview.CreateShopAddressModuleView;
import com.thesettler_x_create.minecolonies.moduleview.CreateShopOutputModuleView;
import com.thesettler_x_create.minecolonies.moduleview.CreateShopPermaModuleView;
import com.thesettler_x_create.minecolonies.moduleview.CreateShopStockModuleView;
import com.thesettler_x_create.minecolonies.registry.ModMinecoloniesJobs;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

public final class ModMinecoloniesBuildings {
    private ModMinecoloniesBuildings() {
    }

    public static final DeferredRegister<BuildingEntry> BUILDINGS =
            DeferredRegister.create(CommonMinecoloniesAPIImpl.BUILDINGS, TheSettlerXCreate.MODID);

    public static final DeferredHolder<BuildingEntry, BuildingEntry> CREATE_SHOP =
            BUILDINGS.register("createshop", () ->
                    new BuildingEntry.Builder()
                            .setRegistryName(ResourceLocation.fromNamespaceAndPath(TheSettlerXCreate.MODID, "createshop"))
                            .setBuildingBlock((AbstractColonyBlock<?>) ModBlocks.HUT_CREATE_SHOP.get())
                            .setBuildingProducer((colony, pos) -> new BuildingCreateShop(colony, pos))
                            .setBuildingViewProducer(() -> (colonyView, pos) -> new EmptyView(colonyView, pos))
                            .addBuildingModuleProducer(new ModuleProducer<>(
                                    "createshop_worker",
                                    () -> new WorkerBuildingModule(
                                            ModMinecoloniesJobs.CREATE_SHOP.get(),
                                            Skill.Knowledge,
                                            Skill.Strength,
                                            true,
                                            building -> Math.max(1, building.getBuildingLevel())
                                    ),
                                    () -> WorkerBuildingModuleView::new
                            ))
                            .addBuildingModuleProducer(BuildingModules.WAREHOUSE_COURIERS)
                            .addBuildingModuleProducer(BuildingModules.WAREHOUSE_REQUEST_QUEUE)
                            .addBuildingModuleProducer(new ModuleProducer<>(
                                    "createshop_address",
                                    CreateShopAddressModule::new,
                                    () -> CreateShopAddressModuleView::new
                            ))
                            .addBuildingModuleProducer(new ModuleProducer<>(
                                    "createshop_stock",
                                    CreateShopStockModule::new,
                                    () -> CreateShopStockModuleView::new
                            ))
                            .addBuildingModuleProducer(new ModuleProducer<>(
                                    "createshop_perma",
                                    CreateShopPermaModule::new,
                                    () -> CreateShopPermaModuleView::new
                            ))
                            .addBuildingModuleProducer(new ModuleProducer<>(
                                    "createshop_output",
                                    CreateShopOutputModule::new,
                                    () -> CreateShopOutputModuleView::new
                            ))
                            .createBuildingEntry()
            );

    public static void register(IEventBus bus) {
        BUILDINGS.register(bus);
    }
}
