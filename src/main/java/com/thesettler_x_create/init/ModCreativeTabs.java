package com.thesettler_x_create.init;

import com.thesettler_x_create.TheSettlerXCreate;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModCreativeTabs {
  private ModCreativeTabs() {}

  public static final DeferredRegister<CreativeModeTab> TABS =
      DeferredRegister.create(Registries.CREATIVE_MODE_TAB, TheSettlerXCreate.MODID);

  public static void register(IEventBus bus) {
    TABS.register(bus);
  }
}
