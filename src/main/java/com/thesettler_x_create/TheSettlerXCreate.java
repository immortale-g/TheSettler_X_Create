package com.thesettler_x_create;

import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.requestsystem.StandardFactoryController;
import com.minecolonies.api.sounds.EventType;
import com.minecolonies.api.sounds.ModSoundEvents;
import com.minecolonies.api.util.Tuple;
import com.mojang.logging.LogUtils;
import com.thesettler_x_create.create.CreateNetworkFacade;
import com.thesettler_x_create.event.StockLinkLinkerEvents;
import com.thesettler_x_create.init.ModBlockEntities;
import com.thesettler_x_create.init.ModBlocks;
import com.thesettler_x_create.init.ModCreativeTabs;
import com.thesettler_x_create.init.ModItems;
import com.thesettler_x_create.init.ModMenus;
import com.thesettler_x_create.minecolonies.registry.ModMinecoloniesBuildings;
import com.thesettler_x_create.minecolonies.registry.ModMinecoloniesJobs;
import com.thesettler_x_create.minecolonies.requestsystem.resolver.CreateShopRequestResolverFactory;
import com.thesettler_x_create.network.ModNetwork;
import java.util.List;
import java.util.Map;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

@Mod(TheSettlerXCreate.MODID)
public class TheSettlerXCreate {
  public static final String MODID = "thesettler_x_create";
  public static final Logger LOGGER = LogUtils.getLogger();
  private long lastGlobalTickLog = -1L;

  public TheSettlerXCreate(IEventBus modEventBus, ModContainer modContainer) {
    modEventBus.addListener(this::commonSetup);
    modEventBus.addListener(this::registerCapabilities);

    ModBlocks.register(modEventBus);
    ModBlockEntities.register(modEventBus);
    ModItems.register(modEventBus);
    ModCreativeTabs.register(modEventBus);
    ModMenus.register(modEventBus);
    ModMinecoloniesBuildings.register(modEventBus);
    ModMinecoloniesJobs.register(modEventBus);
    modEventBus.addListener(ModNetwork::registerPayloads);

    NeoForge.EVENT_BUS.register(this);
    NeoForge.EVENT_BUS.addListener(StockLinkLinkerEvents::onRightClickBlock);
    NeoForge.EVENT_BUS.addListener(this::onServerTick);

    modEventBus.addListener(this::addCreative);

    modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
  }

  private void commonSetup(FMLCommonSetupEvent event) {
    event.enqueueWork(
        () -> {
          LOGGER.info("[CreateShop] debugLogging = {}", Config.DEBUG_LOGGING.getAsBoolean());
          try {
            StandardFactoryController.getInstance()
                .registerNewFactory(new CreateShopRequestResolverFactory());
          } catch (IllegalArgumentException ignored) {
            // Ignore duplicate factory registration across reloads.
          }
          // Ensure Create Shop has citizen sound mappings to avoid NPEs in SoundUtils.
          Map<String, Map<EventType, List<Tuple<SoundEvent, SoundEvent>>>> sounds =
              ModSoundEvents.CITIZEN_SOUND_EVENTS;
          if (sounds == null || sounds.containsKey("createshop")) {
            return;
          }
          Map<EventType, List<Tuple<SoundEvent, SoundEvent>>> base = sounds.get("deliveryman");
          if (base == null) {
            base = sounds.get("builder");
          }
          if (base == null && !sounds.isEmpty()) {
            base = sounds.values().iterator().next();
          }
          if (base != null) {
            sounds.put("createshop", base);
          }
        });
    if (Config.DEBUG_LOGGING.getAsBoolean()) {
      LOGGER.info("TheSettler_x_Create common setup complete");
    }
  }

  private void registerCapabilities(RegisterCapabilitiesEvent event) {
    event.registerBlockEntity(
        Capabilities.ItemHandler.BLOCK,
        ModBlockEntities.CREATE_SHOP_PICKUP.get(),
        (be, side) -> be.getItemHandler(side));
    event.registerBlockEntity(
        Capabilities.ItemHandler.BLOCK,
        ModBlockEntities.CREATE_SHOP_OUTPUT.get(),
        (be, side) -> be.getItemHandler(side));
  }

  @SubscribeEvent
  public void onServerStarting(ServerStartingEvent event) {
    if (Config.DEBUG_LOGGING.getAsBoolean()) {
      LOGGER.info("TheSettler_x_Create server starting");
    }
  }

  private void onServerTick(ServerTickEvent.Post event) {
    CreateNetworkFacade.flushQueuedRequests();
    IColonyManager manager = IColonyManager.getInstance();
    var colonies = manager.getAllColonies();
    if (Config.DEBUG_LOGGING.getAsBoolean()
        && event.getServer() != null
        && (lastGlobalTickLog < 0L
            || event.getServer().getTickCount() - lastGlobalTickLog >= 200L)) {
      lastGlobalTickLog = event.getServer().getTickCount();
      LOGGER.info("[CreateShop] Global shop tick, colonies={}", colonies.size());
    }
  }

  private void addCreative(BuildCreativeModeTabContentsEvent event) {
    if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
      event.accept(ModItems.NETWORK_LINK_TUNER);
      event.accept(ModItems.CREATE_SHOP_PICKUP);
      event.accept(ModItems.CREATE_SHOP_OUTPUT);
    }
    if (event.getTabKey() == com.minecolonies.api.creativetab.ModCreativeTabs.HUTS.getKey()) {
      event.accept(ModItems.HUT_CREATE_SHOP);
    }
  }
}
