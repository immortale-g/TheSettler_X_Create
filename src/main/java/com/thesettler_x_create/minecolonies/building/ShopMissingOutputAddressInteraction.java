package com.thesettler_x_create.minecolonies.building;

import com.minecolonies.api.colony.ICitizen;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.interactionhandling.ChatPriority;
import com.minecolonies.core.colony.interactionhandling.ServerCitizenInteraction;
import com.thesettler_x_create.blockentity.CreateShopOutputBlockEntity;
import java.util.Collections;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

public class ShopMissingOutputAddressInteraction extends ServerCitizenInteraction {
  public ShopMissingOutputAddressInteraction(ICitizen citizen) {
    super(citizen);
  }

  public ShopMissingOutputAddressInteraction() {
    super(
        Component.translatable(
            "com.thesettler_x_create.interaction.createshop.missing_output_address"),
        true,
        ChatPriority.IMPORTANT,
        data -> true,
        Component.translatable(
            "com.thesettler_x_create.interaction.createshop.missing_output_address.id"));
  }

  @Override
  public void onServerResponseTriggered(int response, Player player, ICitizenData citizen) {}

  @Override
  public boolean isValid(ICitizenData citizen) {
    if (citizen == null || !(citizen.getWorkBuilding() instanceof BuildingCreateShop shop)) {
      return false;
    }
    CreateShopOutputBlockEntity obe = shop.getOutputBlockEntity();
    return obe != null && obe.getPackageAddress().isEmpty();
  }

  @Override
  public String getType() {
    return com.minecolonies.api.colony.interactionhandling.ModInteractionResponseHandlers.STANDARD
        .getPath();
  }

  @Override
  public List<com.minecolonies.api.colony.interactionhandling.IInteractionResponseHandler>
      genChildInteractions() {
    return Collections.emptyList();
  }
}
