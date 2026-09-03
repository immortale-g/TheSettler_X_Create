package com.thesettler_x_create.compat.jei;

import com.simibubi.create.compat.jei.GhostIngredientHandler;
import com.thesettler_x_create.TheSettlerXCreate;
import com.thesettler_x_create.client.gui.ColonyGaugeSetItemScreen;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import net.minecraft.resources.ResourceLocation;

@JeiPlugin
public class TheSettlerJEIPlugin implements IModPlugin {
  private static final ResourceLocation ID =
      ResourceLocation.fromNamespaceAndPath(TheSettlerXCreate.MODID, "jei_plugin");

  @Override
  public ResourceLocation getPluginUid() {
    return ID;
  }

  @Override
  @SuppressWarnings({"unchecked", "rawtypes"})
  public void registerGuiHandlers(IGuiHandlerRegistration registration) {
    registration.addGhostIngredientHandler(
        ColonyGaugeSetItemScreen.class, new GhostIngredientHandler());
  }
}
