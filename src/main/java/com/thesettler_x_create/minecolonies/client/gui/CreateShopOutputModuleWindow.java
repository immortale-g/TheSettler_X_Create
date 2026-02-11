package com.thesettler_x_create.minecolonies.client.gui;

import com.ldtteam.blockui.controls.Text;
import com.minecolonies.core.client.gui.AbstractModuleWindow;
import com.thesettler_x_create.TheSettlerXCreate;
import com.thesettler_x_create.minecolonies.moduleview.CreateShopOutputModuleView;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.Locale;

public class CreateShopOutputModuleWindow extends AbstractModuleWindow<CreateShopOutputModuleView> {
    private final CreateShopOutputModuleView moduleView;

    public CreateShopOutputModuleWindow(CreateShopOutputModuleView moduleView) {
        super(moduleView, ResourceLocation.fromNamespaceAndPath(TheSettlerXCreate.MODID, "gui/layouthuts/layoutcreateshop_output.xml"));
        this.moduleView = moduleView;

        Text desc = findPaneOfTypeByID("desc", Text.class);
        if (desc != null) {
            desc.setText(moduleView.getDesc());
        }

        Text status = findPaneOfTypeByID("status", Text.class);
        if (status != null) {
            if (moduleView.isLinked()) {
                status.setText(Component.translatable(
                        "com.thesettler_x_create.gui.createshop.output.linked",
                        String.valueOf(moduleView.getOutputPos())));
            } else {
                status.setText(Component.translatable("com.thesettler_x_create.gui.createshop.output.missing"));
            }
        }
    }
}
