package com.thesettler_x_create.minecolonies.client.gui;

import com.ldtteam.blockui.controls.Button;
import com.ldtteam.blockui.controls.Text;
import com.ldtteam.blockui.controls.TextField;
import com.minecolonies.core.client.gui.AbstractModuleWindow;
import com.thesettler_x_create.TheSettlerXCreate;
import com.thesettler_x_create.minecolonies.moduleview.CreateShopAddressModuleView;
import com.thesettler_x_create.network.SetCreateShopAddressPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Locale;

public class CreateShopAddressModuleWindow extends AbstractModuleWindow<CreateShopAddressModuleView> {
    private final com.minecolonies.api.colony.buildings.views.IBuildingView building;
    private final CreateShopAddressModuleView moduleView;
    private final TextField addressInput;

    public CreateShopAddressModuleWindow(CreateShopAddressModuleView moduleView) {
        super(moduleView, ResourceLocation.fromNamespaceAndPath(TheSettlerXCreate.MODID, "gui/layouthuts/layoutcreateshop_address.xml"));
        this.building = moduleView.getBuildingView();
        this.moduleView = moduleView;

        Text desc = findPaneOfTypeByID("desc", Text.class);
        if (desc != null) {
            desc.setText(moduleView.getDesc());
        }

        addressInput = findPaneOfTypeByID("addressInput", TextField.class);
        if (addressInput != null) {
            addressInput.setText(moduleView.getAddress());
        }

        registerButton("save", this::onSave);
    }

    private void onSave(Button button) {
        if (addressInput == null) {
            return;
        }
        String address = addressInput.getText();
        PacketDistributor.sendToServer(new SetCreateShopAddressPayload(building.getPosition(), address));
    }
}
