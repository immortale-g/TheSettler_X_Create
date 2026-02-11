package com.thesettler_x_create.create;

import com.minecolonies.api.colony.requestsystem.requestable.IDeliverable;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public interface ICreateNetworkFacade {
    int getAvailable(IDeliverable deliverable);

    int getAvailable(ItemStack stack);

    List<ItemStack> getAvailableStacks();

    ItemStack extract(ItemStack stack, int amount, boolean simulate);

    List<ItemStack> planItems(IDeliverable deliverable, int amount);

    List<ItemStack> requestItems(IDeliverable deliverable, int amount);
}
