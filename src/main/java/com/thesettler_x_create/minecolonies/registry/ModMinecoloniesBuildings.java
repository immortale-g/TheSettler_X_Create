package com.thesettler_x_create.minecolonies.registry;

import com.minecolonies.api.blocks.AbstractColonyBlock;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.registry.BuildingEntry;
import com.minecolonies.api.colony.buildings.registry.BuildingEntry.ModuleProducer;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.api.entity.citizen.Skill;
import com.minecolonies.apiimp.CommonMinecoloniesAPIImpl;
import com.minecolonies.core.colony.buildings.modules.ItemListModule;
import com.minecolonies.core.colony.buildings.modules.WorkerBuildingModule;
import com.minecolonies.core.colony.buildings.moduleviews.ItemListModuleView;
import com.minecolonies.core.colony.buildings.moduleviews.WorkerBuildingModuleView;
import com.minecolonies.core.colony.buildings.views.EmptyView;
import com.thesettler_x_create.TheSettlerXCreate;
import com.thesettler_x_create.init.ModBlocks;
import com.thesettler_x_create.minecolonies.building.BuildingCreateShop;
import com.thesettler_x_create.minecolonies.building.ShopColonySupplyPolicy;
import com.thesettler_x_create.minecolonies.module.CreateShopAddressModule;
import com.thesettler_x_create.minecolonies.module.CreateShopOutputModule;
import com.thesettler_x_create.minecolonies.module.CreateShopPermaModule;
import com.thesettler_x_create.minecolonies.module.CreateShopStockModule;
import com.thesettler_x_create.minecolonies.module.CreateShopTaskModule;
import com.thesettler_x_create.minecolonies.moduleview.CreateShopAddressModuleView;
import com.thesettler_x_create.minecolonies.moduleview.CreateShopOutputModuleView;
import com.thesettler_x_create.minecolonies.moduleview.CreateShopPermaModuleView;
import com.thesettler_x_create.minecolonies.moduleview.CreateShopStockModuleView;
import com.thesettler_x_create.minecolonies.moduleview.CreateShopTaskModuleView;
import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModMinecoloniesBuildings {
  private ModMinecoloniesBuildings() {}

  public static final DeferredRegister<BuildingEntry> BUILDINGS =
      DeferredRegister.create(CommonMinecoloniesAPIImpl.BUILDINGS, TheSettlerXCreate.MODID);

  public static final DeferredHolder<BuildingEntry, BuildingEntry> CREATE_SHOP =
      BUILDINGS.register(
          "createshop",
          () ->
              new BuildingEntry.Builder()
                  .setRegistryName(
                      ResourceLocation.fromNamespaceAndPath(TheSettlerXCreate.MODID, "createshop"))
                  .setBuildingBlock((AbstractColonyBlock<?>) ModBlocks.HUT_CREATE_SHOP.get())
                  .setBuildingProducer((colony, pos) -> new BuildingCreateShop(colony, pos))
                  .setBuildingViewProducer(
                      () -> (colonyView, pos) -> new EmptyView(colonyView, pos))
                  .addBuildingModuleProducer(
                      new ModuleProducer<>(
                          "createshop_worker",
                          () ->
                              new WorkerBuildingModule(
                                  ModMinecoloniesJobs.CREATE_SHOP.get(),
                                  Skill.Knowledge,
                                  Skill.Strength,
                                  true,
                                  building -> 1),
                          () -> WorkerBuildingModuleView::new))
                  .addBuildingModuleProducer(
                      new ModuleProducer<>(
                          "createshop_request_queue",
                          CreateShopTaskModule::new,
                          () -> CreateShopTaskModuleView::new))
                  .addBuildingModuleProducer(
                      new ModuleProducer<>(
                          "createshop_address",
                          CreateShopAddressModule::new,
                          () -> CreateShopAddressModuleView::new))
                  .addBuildingModuleProducer(
                      new ModuleProducer<>(
                          "createshop_stock",
                          CreateShopStockModule::new,
                          () -> CreateShopStockModuleView::new))
                  .addBuildingModuleProducer(
                      new ModuleProducer<>(
                          "createshop_perma",
                          CreateShopPermaModule::new,
                          () -> CreateShopPermaModuleView::new))
                  .addBuildingModuleProducer(
                      new ModuleProducer<>(
                          "createshop_output",
                          CreateShopOutputModule::new,
                          () -> CreateShopOutputModuleView::new))
                  // Which items the colony may draw from this shop's Create network. The list holds
                  // what it may not: empty means everything is allowed, and the player switches off
                  // what his own production needs. MineColonies' own module, window and message do
                  // the work; AssignFilterableItemMessage finds the module by its runtime id.
                  .addBuildingModuleProducer(
                      new ModuleProducer<>(
                          ShopColonySupplyPolicy.DENIED_LIST_ID,
                          () -> new ItemListModule(ShopColonySupplyPolicy.DENIED_LIST_ID),
                          () ->
                              () ->
                                  new ItemListModuleView(
                                      ShopColonySupplyPolicy.DENIED_LIST_ID,
                                      Component.translatable(
                                          "com.thesettler_x_create.gui.createshop.coloniesmaydraw"),
                                      true,
                                      buildingView -> everyItem())))
                  .createBuildingEntry());

  /** Every item the game knows, the same list the warehouse's minimum-stock picker offers. */
  private static Set<ItemStorage> everyItem() {
    Set<ItemStorage> items = new LinkedHashSet<>();
    for (var stack : IColonyManager.getInstance().getCompatibilityManager().getListOfAllItems()) {
      if (stack != null && !stack.isEmpty()) {
        items.add(new ItemStorage(stack));
      }
    }
    return items;
  }

  public static void register(IEventBus bus) {
    BUILDINGS.register(bus);
  }
}
