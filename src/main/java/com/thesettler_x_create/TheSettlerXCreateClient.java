package com.thesettler_x_create;

import com.thesettler_x_create.client.gui.CreateShopScreen;
import com.thesettler_x_create.init.ModMenus;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(value = TheSettlerXCreate.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = TheSettlerXCreate.MODID, value = Dist.CLIENT)
public class TheSettlerXCreateClient {
  public TheSettlerXCreateClient(ModContainer container) {
    container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
  }

  @SubscribeEvent
  static void onClientSetup(FMLClientSetupEvent event) {
    if (Config.DEBUG_LOGGING.getAsBoolean()) {
      TheSettlerXCreate.LOGGER.info("TheSettler_x_Create client setup complete");
    }
  }

  @SubscribeEvent
  static void onRegisterScreens(RegisterMenuScreensEvent event) {
    event.register(ModMenus.CREATE_SHOP.get(), CreateShopScreen::new);
  }
}
