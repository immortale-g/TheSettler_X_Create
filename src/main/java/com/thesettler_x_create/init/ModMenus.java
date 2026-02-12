package com.thesettler_x_create.init;

import com.thesettler_x_create.TheSettlerXCreate;
import com.thesettler_x_create.menu.CreateShopMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.network.IContainerFactory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModMenus {
  private ModMenus() {}

  public static final DeferredRegister<MenuType<?>> MENUS =
      DeferredRegister.create(Registries.MENU, TheSettlerXCreate.MODID);

  public static final DeferredHolder<MenuType<?>, MenuType<CreateShopMenu>> CREATE_SHOP =
      MENUS.register(
          "create_shop",
          () -> IMenuTypeExtension.create((IContainerFactory<CreateShopMenu>) CreateShopMenu::new));

  public static void register(IEventBus bus) {
    MENUS.register(bus);
  }
}
