package com.thesettler_x_create.minecolonies.module;

import com.minecolonies.api.colony.buildings.modules.AbstractBuildingModule;
import com.simibubi.create.content.logistics.BigItemStack;
import com.thesettler_x_create.minecolonies.building.BuildingCreateShop;
import com.thesettler_x_create.minecolonies.tileentity.TileEntityCreateShop;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

public class CreateShopStockModule extends AbstractBuildingModule {
  @Override
  public void serializeToView(RegistryFriendlyByteBuf buf) {
    TileEntityCreateShop shop = getShopTile();
    UUID networkId = shop == null ? null : shop.getStockNetworkId();
    buf.writeBoolean(networkId != null);

    writeStacks(buf, getHutInventoryStock());
    writeStacks(buf, getRegisteredStorageStock());
  }

  private TileEntityCreateShop getShopTile() {
    if (building == null) {
      return null;
    }
    if (building.getTileEntity() instanceof TileEntityCreateShop shop) {
      return shop;
    }
    return null;
  }

  private List<BigItemStack> getRegisteredStorageStock() {
    if (building instanceof BuildingCreateShop shopBuilding) {
      return shopBuilding.getRegisteredStorageStock();
    }
    return Collections.emptyList();
  }

  private List<BigItemStack> getHutInventoryStock() {
    if (!(building instanceof BuildingCreateShop shopBuilding)) {
      return Collections.emptyList();
    }
    IItemHandler handler = shopBuilding.getItemHandlerCap((Direction) null);
    if (handler == null) {
      return Collections.emptyList();
    }
    Map<ItemStack, Integer> merged = new HashMap<>();
    for (int slot = 0; slot < handler.getSlots(); slot++) {
      ItemStack stack = handler.getStackInSlot(slot);
      if (stack == null || stack.isEmpty()) {
        continue;
      }
      ItemStack key = stack.copy();
      key.setCount(1);
      ItemStack existingKey = findMatchingKey(merged, key);
      if (existingKey == null) {
        merged.put(key, stack.getCount());
      } else {
        merged.put(existingKey, merged.get(existingKey) + stack.getCount());
      }
    }
    if (merged.isEmpty()) {
      return Collections.emptyList();
    }
    List<BigItemStack> result = new ArrayList<>(merged.size());
    for (Map.Entry<ItemStack, Integer> entry : merged.entrySet()) {
      ItemStack stack = entry.getKey().copy();
      int count = Math.max(1, entry.getValue());
      result.add(new BigItemStack(stack, count));
    }
    result.sort(Comparator.comparingInt((BigItemStack entry) -> entry.count).reversed());
    return result;
  }

  private static ItemStack findMatchingKey(Map<ItemStack, Integer> merged, ItemStack key) {
    if (merged == null || merged.isEmpty() || key == null || key.isEmpty()) {
      return null;
    }
    for (ItemStack existing : merged.keySet()) {
      if (ItemStack.isSameItemSameComponents(existing, key)) {
        return existing;
      }
    }
    return null;
  }

  private static void writeStacks(RegistryFriendlyByteBuf buf, List<BigItemStack> stacks) {
    if (stacks == null || stacks.isEmpty()) {
      buf.writeVarInt(0);
      return;
    }
    buf.writeVarInt(stacks.size());
    for (BigItemStack stack : stacks) {
      BigItemStack.STREAM_CODEC.encode(buf, stack);
    }
  }
}
