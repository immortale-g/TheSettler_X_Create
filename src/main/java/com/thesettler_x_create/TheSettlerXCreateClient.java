package com.thesettler_x_create;

import com.simibubi.create.content.logistics.packager.PackagerRenderer;
import com.simibubi.create.content.logistics.packager.PackagerVisual;
import com.thesettler_x_create.client.ColonyGaugeRenderer;
import com.thesettler_x_create.client.ModPartialModels;
import com.thesettler_x_create.client.gui.ColonyGaugeSetItemScreen;
import com.thesettler_x_create.client.gui.CreateShopScreen;
import com.thesettler_x_create.init.ModBlockEntities;
import com.thesettler_x_create.init.ModBlocks;
import com.thesettler_x_create.init.ModMenus;
import dev.engine_room.flywheel.lib.visualization.SimpleBlockEntityVisualizer;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(value = TheSettlerXCreate.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = TheSettlerXCreate.MODID, value = Dist.CLIENT)
public class TheSettlerXCreateClient {
  public TheSettlerXCreateClient(ModContainer container) {
    container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    ModPartialModels.init();
  }

  @SubscribeEvent
  static void onClientSetup(FMLClientSetupEvent event) {
    event.enqueueWork(() -> {
      ItemBlockRenderTypes.setRenderLayer(ModBlocks.COLONY_GAUGE.get(), RenderType.cutoutMipped());
      ItemBlockRenderTypes.setRenderLayer(ModBlocks.COLONY_PACKAGER.get(), RenderType.cutoutMipped());
      SimpleBlockEntityVisualizer.builder(ModBlockEntities.COLONY_PACKAGER.get())
          .factory(PackagerVisual::new)
          .skipVanillaRender(be -> false)
          .apply();
    });
    if (Config.DEBUG_LOGGING.getAsBoolean()) {
      TheSettlerXCreate.LOGGER.info("TheSettler_x_Create client setup complete");
    }
  }

  @SubscribeEvent
  static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
    event.registerBlockEntityRenderer(ModBlockEntities.COLONY_GAUGE.get(), ColonyGaugeRenderer::new);
    event.registerBlockEntityRenderer(ModBlockEntities.COLONY_PACKAGER.get(), PackagerRenderer::new);
  }

  @SubscribeEvent
  static void onRegisterScreens(RegisterMenuScreensEvent event) {
    event.register(ModMenus.CREATE_SHOP.get(), CreateShopScreen::new);
    event.register(ModMenus.COLONY_GAUGE_SET_ITEM.get(), ColonyGaugeSetItemScreen::new);
  }
}
