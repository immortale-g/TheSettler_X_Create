package com.thesettler_x_create.blockentity;

import com.minecolonies.api.tileentities.AbstractTileEntityRack;
import com.minecolonies.api.util.WorldUtil;
import com.thesettler_x_create.init.ModBlockEntities;
import com.thesettler_x_create.minecolonies.building.BuildingCreateShop;
import com.thesettler_x_create.minecolonies.tileentity.TileEntityCreateShop;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

public class CreateShopOutputBlockEntity extends BlockEntity {
  private static final String TAG_SHOP_POS = "ShopPos";
  private final IItemHandler itemHandler = new OutputItemHandler();
  private BlockPos shopPos;

  public CreateShopOutputBlockEntity(BlockPos pos, BlockState state) {
    super(ModBlockEntities.CREATE_SHOP_OUTPUT.get(), pos, state);
  }

  public void setShopPos(BlockPos pos) {
    shopPos = pos;
    setChanged();
  }

  @Nullable
  public BlockPos getShopPos() {
    return shopPos;
  }

  @Nullable
  public TileEntityCreateShop getShopTile() {
    if (level == null || shopPos == null) {
      return null;
    }
    BlockEntity be = level.getBlockEntity(shopPos);
    if (be instanceof TileEntityCreateShop shop) {
      return shop;
    }
    return null;
  }

  public IItemHandler getItemHandler(@Nullable Direction side) {
    return itemHandler;
  }

  @Override
  public void loadAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
    super.loadAdditional(tag, registries);
    if (tag.contains(TAG_SHOP_POS)) {
      shopPos = BlockPos.of(tag.getLong(TAG_SHOP_POS));
    }
  }

  @Override
  public void saveAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
    super.saveAdditional(tag, registries);
    if (shopPos != null) {
      tag.putLong(TAG_SHOP_POS, shopPos.asLong());
    }
  }

  private final class OutputItemHandler implements IItemHandler {
    @Override
    public int getSlots() {
      return getPermaItems().size();
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
      List<ItemStack> permaItems = getPermaItems();
      if (slot < 0 || slot >= permaItems.size()) {
        return ItemStack.EMPTY;
      }
      ItemStack key = permaItems.get(slot);
      int available = countAvailable(key);
      if (available <= 0) {
        return ItemStack.EMPTY;
      }
      ItemStack stack = key.copy();
      stack.setCount(Math.max(1, Math.min(stack.getMaxStackSize(), available)));
      return stack;
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
      return stack;
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
      if (amount <= 0) {
        return ItemStack.EMPTY;
      }
      List<ItemStack> permaItems = getPermaItems();
      if (slot < 0 || slot >= permaItems.size()) {
        return ItemStack.EMPTY;
      }
      ItemStack key = permaItems.get(slot);
      if (key.isEmpty()) {
        return ItemStack.EMPTY;
      }
      return extractFromRacks(key, amount, simulate);
    }

    @Override
    public int getSlotLimit(int slot) {
      return 64;
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
      return false;
    }

    private List<ItemStack> getPermaItems() {
      TileEntityCreateShop shop = getShopTile();
      if (shop == null || shop.getBuilding() == null) {
        return List.of();
      }
      if (!(shop.getBuilding() instanceof BuildingCreateShop building)
          || !building.canUsePermaRequests()) {
        return List.of();
      }
      List<ItemStack> stacks = new ArrayList<>();
      for (var id : building.getPermaOres()) {
        var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(id);
        if (item != null && item != net.minecraft.world.item.Items.AIR) {
          stacks.add(new ItemStack(item, 1));
        }
      }
      stacks.sort(
          Comparator.comparing(
              stack ->
                  net.minecraft.core.registries.BuiltInRegistries.ITEM
                      .getKey(stack.getItem())
                      .toString()));
      return stacks;
    }

    private int countAvailable(ItemStack key) {
      TileEntityCreateShop shop = getShopTile();
      if (shop == null || shop.getBuilding() == null || shop.getLevel() == null) {
        return 0;
      }
      int total = 0;
      for (BlockPos pos : shop.getBuilding().getContainers()) {
        if (!WorldUtil.isBlockLoaded(shop.getLevel(), pos)) {
          continue;
        }
        BlockEntity entity = shop.getLevel().getBlockEntity(pos);
        if (!(entity instanceof AbstractTileEntityRack rack)) {
          continue;
        }
        total += rack.getItemCount(match -> ItemStack.isSameItemSameComponents(match, key));
      }
      return Math.max(0, total);
    }

    private ItemStack extractFromRacks(ItemStack key, int amount, boolean simulate) {
      TileEntityCreateShop shop = getShopTile();
      if (shop == null || shop.getBuilding() == null || shop.getLevel() == null) {
        return ItemStack.EMPTY;
      }
      int remaining = amount;
      ItemStack extracted = key.copy();
      extracted.setCount(0);

      for (BlockPos pos : shop.getBuilding().getContainers()) {
        if (remaining <= 0) {
          break;
        }
        if (!WorldUtil.isBlockLoaded(shop.getLevel(), pos)) {
          continue;
        }
        BlockEntity entity = shop.getLevel().getBlockEntity(pos);
        if (!(entity instanceof AbstractTileEntityRack rack)) {
          continue;
        }
        IItemHandler handler = rack.getItemHandlerCap();
        if (handler == null) {
          continue;
        }
        for (int slot = 0; slot < handler.getSlots() && remaining > 0; slot++) {
          ItemStack slotStack = handler.getStackInSlot(slot);
          if (slotStack.isEmpty() || !ItemStack.isSameItemSameComponents(slotStack, key)) {
            continue;
          }
          ItemStack pulled = handler.extractItem(slot, remaining, simulate);
          if (!pulled.isEmpty()) {
            extracted.grow(pulled.getCount());
            remaining -= pulled.getCount();
          }
        }
      }

      if (extracted.isEmpty()) {
        return ItemStack.EMPTY;
      }
      return extracted;
    }
  }
}
