package com.thesettler_x_create.minecolonies.client.gui;

import com.ldtteam.blockui.controls.Button;
import com.ldtteam.blockui.controls.Text;
import com.ldtteam.blockui.controls.TextField;
import com.minecolonies.core.client.gui.AbstractModuleWindow;
import com.thesettler_x_create.TheSettlerXCreate;
import com.thesettler_x_create.minecolonies.moduleview.CreateShopAddressModuleView;
import com.thesettler_x_create.network.SetCreateShopAddressPayload;
import com.thesettler_x_create.network.SetPackagerAddressPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

public class CreateShopAddressModuleWindow
    extends AbstractModuleWindow<CreateShopAddressModuleView> {
  private final com.minecolonies.api.colony.buildings.views.IBuildingView building;
  private final TextField addressInput;
  private final TextField packageAddressInput;

  public CreateShopAddressModuleWindow(CreateShopAddressModuleView moduleView) {
    super(
        moduleView,
        ResourceLocation.fromNamespaceAndPath(
            TheSettlerXCreate.MODID, "gui/layouthuts/layoutcreateshop_address.xml"));
    this.building = moduleView.getBuildingView();

    Text desc = findPaneOfTypeByID("desc", Text.class);
    if (desc != null) {
      desc.setText(moduleView.getDesc());
    }

    addressInput = findPaneOfTypeByID("addressInput", TextField.class);
    if (addressInput != null) {
      addressInput.setText(moduleView.getAddress());
    }

    packageAddressInput = findPaneOfTypeByID("packageAddressInput", TextField.class);
    if (packageAddressInput != null) {
      packageAddressInput.setText(moduleView.getPackageAddress());
    }

    registerButton("save", this::onSave);
  }

  private void onSave(Button button) {
    if (addressInput == null) {
      return;
    }
    PacketDistributor.sendToServer(
        new SetCreateShopAddressPayload(building.getPosition(), addressInput.getText()));
    if (packageAddressInput != null) {
      PacketDistributor.sendToServer(
          new SetPackagerAddressPayload(building.getPosition(), packageAddressInput.getText()));
    }
  }
}
