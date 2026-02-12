package com.thesettler_x_create;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import com.thesettler_x_create.init.ModBlocks;
import com.thesettler_x_create.init.ModBlockEntities;
import com.thesettler_x_create.init.ModCreativeTabs;
import com.thesettler_x_create.init.ModItems;
import com.thesettler_x_create.init.ModMenus;
import com.thesettler_x_create.event.StockLinkLinkerEvents;
import com.thesettler_x_create.minecolonies.registry.ModMinecoloniesBuildings;
import com.thesettler_x_create.minecolonies.registry.ModMinecoloniesJobs;
import com.thesettler_x_create.minecolonies.requestsystem.resolver.CreateShopRequestResolverFactory;
import com.thesettler_x_create.minecolonies.requestsystem.CreateShopResolverInjector;
import com.thesettler_x_create.minecolonies.requestsystem.requesters.SafeRequesterFactory;
import com.thesettler_x_create.network.ModNetwork;
import com.minecolonies.api.colony.requestsystem.StandardFactoryController;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.sounds.ModSoundEvents;
import com.minecolonies.api.sounds.EventType;
import com.minecolonies.api.util.Tuple;
import com.minecolonies.api.util.constant.TypeConstants;
import net.minecraft.sounds.SoundEvent;
import java.util.List;
import java.util.Map;

@Mod(TheSettlerXCreate.MODID)
public class TheSettlerXCreate {
    public static final String MODID = "thesettler_x_create";
    public static final Logger LOGGER = LogUtils.getLogger();
    private static long lastGlobalInjectorLogTime = 0L;
    private static int lastGlobalInjectorColonyCount = -1;
    private static long lastGlobalRequestLogTime = 0L;
    private static int lastGlobalRequestCount = -1;
    private static String lastGlobalRequestDump = "";

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
        event.enqueueWork(() -> {
            LOGGER.info("[CreateShop] debugLogging = {}", Config.DEBUG_LOGGING.getAsBoolean());
            try {
                StandardFactoryController.getInstance()
                        .registerNewFactory(new CreateShopRequestResolverFactory());
            } catch (IllegalArgumentException ignored) {
                // Ignore duplicate factory registration across reloads.
            }
            try {
                StandardFactoryController.getInstance()
                        .registerNewFactory(new SafeRequesterFactory());
            } catch (IllegalArgumentException ignored) {
                // Ignore duplicate factory registration across reloads.
            }
            // Ensure Create Shop has citizen sound mappings to avoid NPEs in SoundUtils.
            Map<String, Map<EventType, List<Tuple<SoundEvent, SoundEvent>>>> sounds = ModSoundEvents.CITIZEN_SOUND_EVENTS;
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
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ModBlockEntities.CREATE_SHOP_PICKUP.get(),
                (be, side) -> be.getItemHandler(side));
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ModBlockEntities.CREATE_SHOP_OUTPUT.get(),
                (be, side) -> be.getItemHandler(side));
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        if (Config.DEBUG_LOGGING.getAsBoolean()) {
            LOGGER.info("TheSettler_x_Create server starting");
        }
    }

    private void onServerTick(ServerTickEvent.Post event) {
        IColonyManager manager = IColonyManager.getInstance();
        var colonies = manager.getAllColonies();
        if (Config.DEBUG_LOGGING.getAsBoolean()) {
            long now = event.getServer() == null ? 0L : event.getServer().getTickCount();
            boolean logTick = now == 0L
                    || now - lastGlobalInjectorLogTime >= Config.GLOBAL_INJECTOR_LOG_COOLDOWN.getAsLong()
                    || colonies.size() != lastGlobalInjectorColonyCount;
            if (logTick) {
                lastGlobalInjectorLogTime = now;
                lastGlobalInjectorColonyCount = colonies.size();
                LOGGER.info("[CreateShop] Global injector tick, colonies={}", colonies.size());
            }
        }
        for (IColony colony : colonies) {
            CreateShopResolverInjector.ensureGlobalResolver(colony);
            if (!Config.DEBUG_LOGGING.getAsBoolean()) {
                continue;
            }
            if (!(colony.getRequestManager() instanceof com.minecolonies.core.colony.requestsystem.management.IStandardRequestManager requestManager)) {
                continue;
            }
            var identities = requestManager.getRequestIdentitiesDataStore().getIdentities();
            long now = event.getServer() == null ? 0L : event.getServer().getTickCount();
            boolean logRequests = identities.size() != lastGlobalRequestCount
                    || now == 0L
                    || now - lastGlobalRequestLogTime >= Config.GLOBAL_REQUEST_LOG_COOLDOWN.getAsLong();
            if (logRequests) {
                int logged = 0;
                java.util.List<String> entries = new java.util.ArrayList<>();
                for (var entry : identities.entrySet()) {
                    var request = entry.getValue();
                    Object payload = request.getRequest();
                    boolean isDeliverable = request.getSuperClasses().contains(TypeConstants.DELIVERABLE);
                    boolean isRequestable = request.getSuperClasses().contains(TypeConstants.REQUESTABLE);
                    boolean isTool = request.getSuperClasses().contains(TypeConstants.TOOL);
                    entries.add("token=" + entry.getKey()
                            + " type=" + (payload == null ? "<null>" : payload.getClass().getName())
                            + " state=" + request.getState()
                            + " flags[d=" + isDeliverable + ",r=" + isRequestable + ",t=" + isTool + "]");
                    if (++logged >= 3) {
                        break;
                    }
                }
                String dump = String.join(" | ", entries);
                if (!dump.equals(lastGlobalRequestDump)) {
                    lastGlobalRequestDump = dump;
                    lastGlobalRequestLogTime = now;
                    lastGlobalRequestCount = identities.size();
                    LOGGER.info("[CreateShop] Active requests ({}): {}",
                            identities.size(), dump.isEmpty() ? "<none>" : dump);
                }
            }
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
